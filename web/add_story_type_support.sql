-- =============================================
-- THÊM HỖ TRỢ TRUYỆN CHỮ VÀ TRUYỆN TRANH
-- Script này an toàn để chạy nhiều lần
-- Tự động kiểm tra và chỉ tạo phần chưa có
-- =============================================

USE Fimory;
GO

PRINT N'========================================';
PRINT N'BẮT ĐẦU CẬP NHẬT DATABASE';
PRINT N'========================================';
PRINT N'';

-- =============================================
-- 1. THÊM CỘT StoryType VÀO BẢNG Series
-- =============================================
PRINT N'[1/3] Kiểm tra cột StoryType...';

IF NOT EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'Series' AND COLUMN_NAME = 'StoryType'
)
BEGIN
    PRINT N'   → Đang thêm cột StoryType...';
    
    -- Thêm cột cho phép NULL trước
    ALTER TABLE Series
    ADD StoryType NVARCHAR(20) NULL;
    
    PRINT N'   ✅ Đã thêm cột StoryType (NULL)';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Cột StoryType đã tồn tại';
END
GO

-- Cập nhật và set NOT NULL cho StoryType (batch riêng)
IF EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'Series' AND COLUMN_NAME = 'StoryType'
)
BEGIN
    -- Cập nhật các giá trị NULL thành 'Text'
    UPDATE Series SET StoryType = 'Text' WHERE StoryType IS NULL;
    
    -- Set NOT NULL nếu chưa phải NOT NULL
    IF EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_NAME = 'Series' 
        AND COLUMN_NAME = 'StoryType' 
        AND IS_NULLABLE = 'YES'
    )
    BEGIN
        ALTER TABLE Series
        ALTER COLUMN StoryType NVARCHAR(20) NOT NULL;
    END
    
    -- Thêm DEFAULT constraint nếu chưa có
    IF NOT EXISTS (
        SELECT * FROM sys.default_constraints 
        WHERE name = 'DF_Series_StoryType'
    )
    BEGIN
        ALTER TABLE Series
        ADD CONSTRAINT DF_Series_StoryType DEFAULT 'Text' FOR StoryType;
    END
    
    PRINT N'   ✅ Đã cập nhật StoryType (NOT NULL, DEFAULT ''Text'')';
END
GO

-- =============================================
-- 2. TẠO BẢNG ChapterImages
-- =============================================
PRINT N'';
PRINT N'[2/3] Kiểm tra bảng ChapterImages...';

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'ChapterImages')
BEGIN
    PRINT N'   → Đang tạo bảng ChapterImages...';
    
    CREATE TABLE ChapterImages (
        ImageID INT PRIMARY KEY IDENTITY(1,1),
        ChapterID INT NOT NULL,
        ImageURL NVARCHAR(500) NOT NULL,
        ImageOrder INT NOT NULL, -- Thứ tự hiển thị ảnh trong chapter
        FileSize BIGINT, -- Kích thước file (bytes)
        Width INT, -- Chiều rộng ảnh (pixels)
        Height INT, -- Chiều cao ảnh (pixels)
        CreatedAt DATETIME DEFAULT GETDATE(),
        FOREIGN KEY (ChapterID) REFERENCES Chapters(ChapterID) ON DELETE CASCADE,
        UNIQUE(ChapterID, ImageOrder)
    );
    
    -- Tạo index để tối ưu query
    CREATE INDEX IX_ChapterImages_ChapterID_ImageOrder ON ChapterImages(ChapterID, ImageOrder);
    
    PRINT N'   ✅ Đã tạo bảng ChapterImages';
    PRINT N'   ✅ Đã tạo index IX_ChapterImages_ChapterID_ImageOrder';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Bảng ChapterImages đã tồn tại';
    
    -- Kiểm tra và tạo index nếu chưa có
    IF NOT EXISTS (
        SELECT * FROM sys.indexes 
        WHERE name = 'IX_ChapterImages_ChapterID_ImageOrder'
    )
    BEGIN
        CREATE INDEX IX_ChapterImages_ChapterID_ImageOrder ON ChapterImages(ChapterID, ImageOrder);
        PRINT N'   ✅ Đã tạo index IX_ChapterImages_ChapterID_ImageOrder';
    END
END
GO

-- =============================================
-- 3. THÊM CỘT ImageCount VÀO BẢNG Chapters
-- =============================================
PRINT N'';
PRINT N'[3/3] Kiểm tra cột ImageCount...';

IF NOT EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'Chapters' AND COLUMN_NAME = 'ImageCount'
)
BEGIN
    PRINT N'   → Đang thêm cột ImageCount...';
    
    -- Thêm cột với DEFAULT value
    ALTER TABLE Chapters
    ADD ImageCount INT DEFAULT 0;
    
    PRINT N'   ✅ Đã thêm cột ImageCount';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Cột ImageCount đã tồn tại';
END
GO

-- Cập nhật ImageCount từ ChapterImages (batch riêng)
IF EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'Chapters' AND COLUMN_NAME = 'ImageCount'
)
BEGIN
    -- Cập nhật ImageCount nếu bảng ChapterImages đã tồn tại
    IF EXISTS (SELECT * FROM sys.tables WHERE name = 'ChapterImages')
    BEGIN
        UPDATE c
        SET c.ImageCount = ISNULL(img_cnt.cnt, 0)
        FROM Chapters c
        LEFT JOIN (
            SELECT ChapterID, COUNT(*) as cnt
            FROM ChapterImages
            GROUP BY ChapterID
        ) img_cnt ON c.ChapterID = img_cnt.ChapterID;
        
        PRINT N'   ✅ Đã cập nhật ImageCount từ bảng ChapterImages';
    END
    ELSE
    BEGIN
        -- Đảm bảo tất cả chapters có ImageCount = 0
        UPDATE Chapters SET ImageCount = 0 WHERE ImageCount IS NULL;
        PRINT N'   ✅ Đã set ImageCount = 0 cho tất cả chapters';
    END
END
GO

-- =============================================
-- KẾT THÚC - HIỂN THỊ TÓM TẮT
-- =============================================
PRINT N'';
PRINT N'========================================';
PRINT N'✅ HOÀN THÀNH CẬP NHẬT DATABASE!';
PRINT N'========================================';
PRINT N'';

-- Hiển thị tóm tắt (batch riêng để tránh lỗi DECLARE)
DECLARE @StoryTypeCount INT = 0;
DECLARE @ImageCount INT = 0;
DECLARE @ChapterCount INT = 0;

-- Kiểm tra StoryType
IF EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'Series' AND COLUMN_NAME = 'StoryType'
)
BEGIN
    SELECT @StoryTypeCount = COUNT(*) FROM Series;
    PRINT N'   ✓ Cột StoryType: Đã có (' + CAST(@StoryTypeCount AS NVARCHAR) + N' truyện)';
END
ELSE
BEGIN
    PRINT N'   ✗ Cột StoryType: CHƯA CÓ';
END

-- Kiểm tra ChapterImages
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'ChapterImages')
BEGIN
    SELECT @ImageCount = COUNT(*) FROM ChapterImages;
    PRINT N'   ✓ Bảng ChapterImages: Đã có (' + CAST(@ImageCount AS NVARCHAR) + N' ảnh)';
END
ELSE
BEGIN
    PRINT N'   ✗ Bảng ChapterImages: CHƯA CÓ';
END

-- Kiểm tra ImageCount
IF EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'Chapters' AND COLUMN_NAME = 'ImageCount'
)
BEGIN
    SELECT @ChapterCount = COUNT(*) FROM Chapters;
    PRINT N'   ✓ Cột ImageCount: Đã có (' + CAST(@ChapterCount AS NVARCHAR) + N' chapters)';
END
ELSE
BEGIN
    PRINT N'   ✗ Cột ImageCount: CHƯA CÓ';
END

PRINT N'';
PRINT N'📝 CÁCH SỬ DỤNG:';
PRINT N'   - StoryType = ''Text'': Truyện chữ, lưu nội dung trong cột Content';
PRINT N'   - StoryType = ''Comic'': Truyện tranh, lưu danh sách ảnh trong bảng ChapterImages';
PRINT N'';
PRINT N'🚀 Hệ thống đã sẵn sàng hỗ trợ cả truyện chữ và truyện tranh!';
PRINT N'';
GO
