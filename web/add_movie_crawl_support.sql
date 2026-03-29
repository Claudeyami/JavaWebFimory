-- =============================================
-- THÊM HỖ TRỢ CRAWL PHIM TỪ TRANG VN
-- Script này an toàn để chạy nhiều lần
-- Tự động kiểm tra và chỉ tạo phần chưa có
-- =============================================

USE Fimory;
GO

PRINT N'========================================';
PRINT N'BẮT ĐẦU CẬP NHẬT DATABASE CHO CRAWL PHIM';
PRINT N'========================================';
PRINT N'';

-- =============================================
-- 1. THÊM CÁC CỘT VÀO BẢNG MovieEpisodes
-- =============================================
PRINT N'[1/4] Kiểm tra các cột trong MovieEpisodes...';

-- Thêm cột SeasonNumber (nếu chưa có)
IF NOT EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'MovieEpisodes' AND COLUMN_NAME = 'SeasonNumber'
)
BEGIN
    PRINT N'   → Đang thêm cột SeasonNumber...';
    ALTER TABLE MovieEpisodes
    ADD SeasonNumber INT NULL DEFAULT 1;
    PRINT N'   ✅ Đã thêm cột SeasonNumber';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Cột SeasonNumber đã tồn tại';
END
GO

-- Thêm cột VideoBackupURL
IF NOT EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'MovieEpisodes' AND COLUMN_NAME = 'VideoBackupURL'
)
BEGIN
    PRINT N'   → Đang thêm cột VideoBackupURL...';
    ALTER TABLE MovieEpisodes
    ADD VideoBackupURL NVARCHAR(500) NULL;
    PRINT N'   ✅ Đã thêm cột VideoBackupURL';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Cột VideoBackupURL đã tồn tại';
END
GO

-- Thêm cột SubtitleURL
IF NOT EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'MovieEpisodes' AND COLUMN_NAME = 'SubtitleURL'
)
BEGIN
    PRINT N'   → Đang thêm cột SubtitleURL...';
    ALTER TABLE MovieEpisodes
    ADD SubtitleURL NVARCHAR(500) NULL;
    PRINT N'   ✅ Đã thêm cột SubtitleURL';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Cột SubtitleURL đã tồn tại';
END
GO

-- Thêm cột ServerName (tên server video: Vietsub, Thuyết minh, Server1, Server2, etc.)
IF NOT EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'MovieEpisodes' AND COLUMN_NAME = 'ServerName'
)
BEGIN
    PRINT N'   → Đang thêm cột ServerName...';
    ALTER TABLE MovieEpisodes
    ADD ServerName NVARCHAR(100) NULL;
    PRINT N'   ✅ Đã thêm cột ServerName';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Cột ServerName đã tồn tại';
END
GO

-- Thêm cột CrawlSource (nguồn crawl: phimmoi, hdonline, etc.)
IF NOT EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'MovieEpisodes' AND COLUMN_NAME = 'CrawlSource'
)
BEGIN
    PRINT N'   → Đang thêm cột CrawlSource...';
    ALTER TABLE MovieEpisodes
    ADD CrawlSource NVARCHAR(100) NULL;
    PRINT N'   ✅ Đã thêm cột CrawlSource';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Cột CrawlSource đã tồn tại';
END
GO

-- Thêm cột CrawlEpisodeURL (URL gốc của episode khi crawl)
IF NOT EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'MovieEpisodes' AND COLUMN_NAME = 'CrawlEpisodeURL'
)
BEGIN
    PRINT N'   → Đang thêm cột CrawlEpisodeURL...';
    ALTER TABLE MovieEpisodes
    ADD CrawlEpisodeURL NVARCHAR(500) NULL;
    PRINT N'   ✅ Đã thêm cột CrawlEpisodeURL';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Cột CrawlEpisodeURL đã tồn tại';
END
GO

-- =============================================
-- 2. THÊM CỘT VÀO BẢNG Movies
-- =============================================
PRINT N'';
PRINT N'[2/4] Kiểm tra các cột trong Movies...';

-- Thêm cột CrawlSource vào Movies (nguồn crawl phim)
IF NOT EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'Movies' AND COLUMN_NAME = 'CrawlSource'
)
BEGIN
    PRINT N'   → Đang thêm cột CrawlSource...';
    ALTER TABLE Movies
    ADD CrawlSource NVARCHAR(100) NULL;
    PRINT N'   ✅ Đã thêm cột CrawlSource';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Cột CrawlSource đã tồn tại';
END
GO

-- Thêm cột CrawlMovieURL (URL gốc của phim khi crawl)
IF NOT EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'Movies' AND COLUMN_NAME = 'CrawlMovieURL'
)
BEGIN
    PRINT N'   → Đang thêm cột CrawlMovieURL...';
    ALTER TABLE Movies
    ADD CrawlMovieURL NVARCHAR(500) NULL;
    PRINT N'   ✅ Đã thêm cột CrawlMovieURL';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Cột CrawlMovieURL đã tồn tại';
END
GO

-- Thêm cột IMDBScore (nếu chưa có - có thể đã có từ TMDB)
IF NOT EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'Movies' AND COLUMN_NAME = 'IMDBScore'
)
BEGIN
    PRINT N'   → Đang thêm cột IMDBScore...';
    ALTER TABLE Movies
    ADD IMDBScore DECIMAL(3,1) NULL;
    PRINT N'   ✅ Đã thêm cột IMDBScore';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Cột IMDBScore đã tồn tại';
END
GO

-- =============================================
-- 3. TẠO BẢNG MovieServers (nếu cần lưu nhiều server cho 1 episode)
-- =============================================
PRINT N'';
PRINT N'[3/4] Kiểm tra bảng MovieServers...';

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'MovieServers')
BEGIN
    PRINT N'   → Đang tạo bảng MovieServers...';
    
    CREATE TABLE MovieServers (
        ServerID INT PRIMARY KEY IDENTITY(1,1),
        EpisodeID INT NOT NULL,
        ServerName NVARCHAR(100) NOT NULL, -- Vietsub, Thuyết minh, Server1, Server2, etc.
        VideoURL NVARCHAR(500) NOT NULL,
        VideoBackupURL NVARCHAR(500) NULL,
        SubtitleURL NVARCHAR(500) NULL,
        Quality NVARCHAR(20) NULL, -- 480p, 720p, 1080p, FullHD, etc.
        DisplayOrder INT DEFAULT 0, -- Thứ tự hiển thị
        IsActive BIT DEFAULT 1,
        CreatedAt DATETIME DEFAULT GETDATE(),
        UpdatedAt DATETIME DEFAULT GETDATE(),
        FOREIGN KEY (EpisodeID) REFERENCES MovieEpisodes(EpisodeID) ON DELETE CASCADE
    );
    
    -- Tạo index để tối ưu query
    CREATE INDEX IX_MovieServers_EpisodeID ON MovieServers(EpisodeID);
    CREATE INDEX IX_MovieServers_DisplayOrder ON MovieServers(DisplayOrder);
    
    PRINT N'   ✅ Đã tạo bảng MovieServers';
    PRINT N'   ✅ Đã tạo index IX_MovieServers_EpisodeID';
    PRINT N'   ✅ Đã tạo index IX_MovieServers_DisplayOrder';
END
ELSE
BEGIN
    PRINT N'   ⚠️ Bảng MovieServers đã tồn tại';
    
    -- Kiểm tra và tạo index nếu chưa có
    IF NOT EXISTS (
        SELECT * FROM sys.indexes 
        WHERE name = 'IX_MovieServers_EpisodeID'
    )
    BEGIN
        CREATE INDEX IX_MovieServers_EpisodeID ON MovieServers(EpisodeID);
        PRINT N'   ✅ Đã tạo index IX_MovieServers_EpisodeID';
    END
    
    IF NOT EXISTS (
        SELECT * FROM sys.indexes 
        WHERE name = 'IX_MovieServers_DisplayOrder'
    )
    BEGIN
        CREATE INDEX IX_MovieServers_DisplayOrder ON MovieServers(DisplayOrder);
        PRINT N'   ✅ Đã tạo index IX_MovieServers_DisplayOrder';
    END
END
GO

-- =============================================
-- 4. CẬP NHẬT UNIQUE CONSTRAINT CHO MovieEpisodes
-- =============================================
PRINT N'';
PRINT N'[4/4] Kiểm tra unique constraint cho MovieEpisodes...';

-- Kiểm tra xem có unique constraint (MovieID, EpisodeNumber) không
-- Nếu có SeasonNumber, cần unique (MovieID, SeasonNumber, EpisodeNumber)
IF EXISTS (
    SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'MovieEpisodes' AND COLUMN_NAME = 'SeasonNumber'
)
BEGIN
    -- Kiểm tra xem đã có unique constraint với SeasonNumber chưa
    IF NOT EXISTS (
        SELECT * FROM sys.indexes 
        WHERE object_id = OBJECT_ID('MovieEpisodes')
        AND name = 'UQ_MovieEpisodes_MovieID_SeasonNumber_EpisodeNumber'
    )
    BEGIN
        -- Xóa constraint cũ nếu có (MovieID, EpisodeNumber)
        IF EXISTS (
            SELECT * FROM sys.indexes 
            WHERE object_id = OBJECT_ID('MovieEpisodes')
            AND name LIKE 'UQ_MovieEpisodes%'
        )
        BEGIN
            DECLARE @constraintName NVARCHAR(255);
            SELECT @constraintName = name 
            FROM sys.indexes 
            WHERE object_id = OBJECT_ID('MovieEpisodes')
            AND name LIKE 'UQ_MovieEpisodes%';
            
            EXEC('ALTER TABLE MovieEpisodes DROP CONSTRAINT ' + @constraintName);
            PRINT N'   → Đã xóa constraint cũ: ' + @constraintName;
        END
        
        -- Tạo unique constraint mới với SeasonNumber
        ALTER TABLE MovieEpisodes
        ADD CONSTRAINT UQ_MovieEpisodes_MovieID_SeasonNumber_EpisodeNumber 
        UNIQUE (MovieID, SeasonNumber, EpisodeNumber);
        
        PRINT N'   ✅ Đã tạo unique constraint với SeasonNumber';
    END
    ELSE
    BEGIN
        PRINT N'   ⚠️ Unique constraint với SeasonNumber đã tồn tại';
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

-- Hiển thị tóm tắt
DECLARE @EpisodeCount INT = 0;
DECLARE @ServerCount INT = 0;

SELECT @EpisodeCount = COUNT(*) FROM MovieEpisodes;
SELECT @ServerCount = COUNT(*) FROM MovieServers;

PRINT N'📊 TÓM TẮT:';
PRINT N'   ✓ MovieEpisodes: Đã thêm các cột hỗ trợ crawl';
PRINT N'     - SeasonNumber (INT)';
PRINT N'     - VideoBackupURL (NVARCHAR)';
PRINT N'     - SubtitleURL (NVARCHAR)';
PRINT N'     - ServerName (NVARCHAR)';
PRINT N'     - CrawlSource (NVARCHAR)';
PRINT N'     - CrawlEpisodeURL (NVARCHAR)';
PRINT N'';
PRINT N'   ✓ Movies: Đã thêm các cột hỗ trợ crawl';
PRINT N'     - CrawlSource (NVARCHAR)';
PRINT N'     - CrawlMovieURL (NVARCHAR)';
PRINT N'     - IMDBScore (DECIMAL)';
PRINT N'';
PRINT N'   ✓ MovieServers: Bảng mới để lưu nhiều server cho 1 episode';
PRINT N'     - ServerID (PK)';
PRINT N'     - EpisodeID (FK)';
PRINT N'     - ServerName, VideoURL, VideoBackupURL, SubtitleURL';
PRINT N'     - Quality, DisplayOrder, IsActive';
PRINT N'';
PRINT N'📝 CÁCH SỬ DỤNG:';
PRINT N'   - Dùng MovieEpisodes cho trường hợp đơn giản (1 server/episode)';
PRINT N'   - Dùng MovieServers cho trường hợp phức tạp (nhiều server/episode)';
PRINT N'   - SeasonNumber: Hỗ trợ phim bộ có nhiều mùa';
PRINT N'   - CrawlSource: Lưu nguồn crawl (phimmoi, hdonline, etc.)';
PRINT N'';
PRINT N'🚀 Hệ thống đã sẵn sàng hỗ trợ crawl phim từ trang VN!';
PRINT N'';
GO

