USE Fimory;
GO

-- BƯỚC 1: Kiểm tra các phim có thể trùng lặp
PRINT N'=== BƯỚC 1: Kiểm tra phim trùng lặp ===';
SELECT 
    m1.MovieID as MovieID1,
    m1.Title as Title1,
    m1.Slug as Slug1,
    m1.CreatedAt as CreatedAt1,
    (SELECT COUNT(*) FROM dbo.MovieEpisodes WHERE MovieID = m1.MovieID) as Episodes1,
    m2.MovieID as MovieID2,
    m2.Title as Title2,
    m2.Slug as Slug2,
    m2.CreatedAt as CreatedAt2,
    (SELECT COUNT(*) FROM dbo.MovieEpisodes WHERE MovieID = m2.MovieID) as Episodes2
FROM dbo.Movies m1
CROSS JOIN dbo.Movies m2
WHERE m1.MovieID < m2.MovieID
  AND (
    m1.Title = m2.Title
    OR m1.Slug LIKE m2.Slug + '%'
    OR m2.Slug LIKE m1.Slug + '%'
  )
ORDER BY m1.CreatedAt DESC;
GO

-- BƯỚC 2: Xem chi tiết tất cả phim và tập phim
PRINT N'=== BƯỚC 2: Chi tiết tất cả phim ===';
SELECT 
    m.MovieID,
    m.Title,
    m.Slug,
    m.Status,
    m.CreatedAt,
    COUNT(me.EpisodeID) as EpisodeCount,
    STRING_AGG('Tập ' + CAST(me.EpisodeNumber AS VARCHAR) + ': ' + ISNULL(me.Title, 'N/A'), ' | ') WITHIN GROUP (ORDER BY me.EpisodeNumber) as EpisodeList
FROM dbo.Movies m
LEFT JOIN dbo.MovieEpisodes me ON m.MovieID = me.MovieID
GROUP BY m.MovieID, m.Title, m.Slug, m.Status, m.CreatedAt
ORDER BY m.CreatedAt DESC;
GO