USE Fimory;
GO

PRINT N'=== BAT DAU CAP NHAT CAU TRUC MODERATION ===';
GO

IF DB_ID(N'Fimory') IS NULL
BEGIN
    RAISERROR(N'Database Fimory khong ton tai.', 16, 1);
    RETURN;
END
GO

IF OBJECT_ID(N'[dbo].[Users]', N'U') IS NULL
BEGIN
    RAISERROR(N'Bang Users khong ton tai. Hay kiem tra lai schema goc truoc khi chay script moderation.', 16, 1);
    RETURN;
END
GO

IF COL_LENGTH(N'[dbo].[Users]', N'UserID') IS NULL
BEGIN
    RAISERROR(N'Cot Users.UserID khong ton tai. Script moderation khong the tao khoa ngoai.', 16, 1);
    RETURN;
END
GO

IF OBJECT_ID(N'[dbo].[ContentModerationJobs]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[ContentModerationJobs] (
        [JobID] INT IDENTITY(1,1) NOT NULL,
        [ContentType] NVARCHAR(20) NOT NULL,
        [ContentID] INT NOT NULL,
        [Status] NVARCHAR(20) NOT NULL CONSTRAINT [DF_ContentModerationJobs_Status] DEFAULT (N'Pending'),
        [Decision] NVARCHAR(20) NOT NULL CONSTRAINT [DF_ContentModerationJobs_Decision] DEFAULT (N'NONE'),
        [Priority] INT NOT NULL CONSTRAINT [DF_ContentModerationJobs_Priority] DEFAULT ((1)),
        [RetryCount] INT NOT NULL CONSTRAINT [DF_ContentModerationJobs_RetryCount] DEFAULT ((0)),
        [MaxRetries] INT NOT NULL CONSTRAINT [DF_ContentModerationJobs_MaxRetries] DEFAULT ((3)),
        [LockedBy] NVARCHAR(50) NULL,
        [LockedAt] DATETIME2(0) NULL,
        [NextRetryAt] DATETIME2(0) NOT NULL CONSTRAINT [DF_ContentModerationJobs_NextRetryAt] DEFAULT (GETDATE()),
        [LastError] NVARCHAR(MAX) NULL,
        [CreatedAt] DATETIME2(0) NOT NULL CONSTRAINT [DF_ContentModerationJobs_CreatedAt] DEFAULT (GETDATE()),
        [UpdatedAt] DATETIME2(0) NOT NULL CONSTRAINT [DF_ContentModerationJobs_UpdatedAt] DEFAULT (GETDATE()),
        CONSTRAINT [PK_ContentModerationJobs] PRIMARY KEY CLUSTERED ([JobID] ASC),
        CONSTRAINT [CK_ContentModerationJobs_ContentType]
            CHECK ([ContentType] IN (N'Movie', N'Series', N'Episode', N'Chapter')),
        CONSTRAINT [CK_ContentModerationJobs_Status]
            CHECK ([Status] IN (N'Pending', N'Processing', N'Completed', N'Failed', N'Cancelled')),
        CONSTRAINT [CK_ContentModerationJobs_Decision]
            CHECK ([Decision] IN (N'NONE', N'ALLOW', N'REVIEW', N'BLOCK')),
        CONSTRAINT [CK_ContentModerationJobs_Priority]
            CHECK ([Priority] >= 0),
        CONSTRAINT [CK_ContentModerationJobs_RetryCount]
            CHECK ([RetryCount] >= 0),
        CONSTRAINT [CK_ContentModerationJobs_MaxRetries]
            CHECK ([MaxRetries] >= 0)
    );

    PRINT N'Da tao bang ContentModerationJobs.';
END
ELSE
BEGIN
    PRINT N'Bo qua: Bang ContentModerationJobs da ton tai.';
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_ContentModerationJobs_Status_NextRetry'
      AND object_id = OBJECT_ID(N'[dbo].[ContentModerationJobs]')
)
BEGIN
    CREATE NONCLUSTERED INDEX [IX_ContentModerationJobs_Status_NextRetry]
        ON [dbo].[ContentModerationJobs] ([Status] ASC, [NextRetryAt] ASC)
        INCLUDE ([Priority], [RetryCount], [MaxRetries], [Decision], [ContentType], [ContentID], [UpdatedAt]);

    PRINT N'Da tao index IX_ContentModerationJobs_Status_NextRetry.';
END
ELSE
BEGIN
    PRINT N'Bo qua: Index IX_ContentModerationJobs_Status_NextRetry da ton tai.';
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_ContentModerationJobs_ContentLookup'
      AND object_id = OBJECT_ID(N'[dbo].[ContentModerationJobs]')
)
BEGIN
    CREATE NONCLUSTERED INDEX [IX_ContentModerationJobs_ContentLookup]
        ON [dbo].[ContentModerationJobs] ([ContentType] ASC, [ContentID] ASC, [CreatedAt] DESC);

    PRINT N'Da tao index IX_ContentModerationJobs_ContentLookup.';
END
ELSE
BEGIN
    PRINT N'Bo qua: Index IX_ContentModerationJobs_ContentLookup da ton tai.';
END
GO

IF OBJECT_ID(N'[dbo].[ContentModerationResults]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[ContentModerationResults] (
        [ResultID] INT IDENTITY(1,1) NOT NULL,
        [JobID] INT NOT NULL,
        [Category] NVARCHAR(50) NOT NULL,
        [ConfidenceScore] DECIMAL(5,2) NOT NULL CONSTRAINT [DF_ContentModerationResults_ConfidenceScore] DEFAULT ((0)),
        [EvidenceType] NVARCHAR(20) NOT NULL,
        [EvidencePath] NVARCHAR(500) NULL,
        [RawResponse] NVARCHAR(MAX) NULL,
        [CreatedAt] DATETIME2(0) NOT NULL CONSTRAINT [DF_ContentModerationResults_CreatedAt] DEFAULT (GETDATE()),
        CONSTRAINT [PK_ContentModerationResults] PRIMARY KEY CLUSTERED ([ResultID] ASC),
        CONSTRAINT [FK_ContentModerationResults_Job]
            FOREIGN KEY ([JobID]) REFERENCES [dbo].[ContentModerationJobs]([JobID]) ON DELETE CASCADE,
        CONSTRAINT [CK_ContentModerationResults_ConfidenceScore]
            CHECK ([ConfidenceScore] >= 0 AND [ConfidenceScore] <= 100),
        CONSTRAINT [CK_ContentModerationResults_EvidenceType]
            CHECK ([EvidenceType] IN (N'TEXT', N'IMAGE', N'VIDEO_FRAME', N'SIMILARITY', N'METADATA'))
    );

    PRINT N'Da tao bang ContentModerationResults.';
END
ELSE
BEGIN
    PRINT N'Bo qua: Bang ContentModerationResults da ton tai.';
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_ContentModerationResults_JobID'
      AND object_id = OBJECT_ID(N'[dbo].[ContentModerationResults]')
)
BEGIN
    CREATE NONCLUSTERED INDEX [IX_ContentModerationResults_JobID]
        ON [dbo].[ContentModerationResults] ([JobID] ASC, [CreatedAt] DESC);

    PRINT N'Da tao index IX_ContentModerationResults_JobID.';
END
ELSE
BEGIN
    PRINT N'Bo qua: Index IX_ContentModerationResults_JobID da ton tai.';
END
GO

IF OBJECT_ID(N'[dbo].[ContentModerationAppeals]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[ContentModerationAppeals] (
        [AppealID] INT IDENTITY(1,1) NOT NULL,
        [ContentType] NVARCHAR(20) NOT NULL,
        [ContentID] INT NOT NULL,
        [UserID] INT NOT NULL,
        [Reason] NVARCHAR(1000) NULL,
        [Status] NVARCHAR(20) NOT NULL CONSTRAINT [DF_ContentModerationAppeals_Status] DEFAULT (N'Pending'),
        [ReviewedBy] INT NULL,
        [CreatedAt] DATETIME2(0) NOT NULL CONSTRAINT [DF_ContentModerationAppeals_CreatedAt] DEFAULT (GETDATE()),
        [ReviewedAt] DATETIME2(0) NULL,
        CONSTRAINT [PK_ContentModerationAppeals] PRIMARY KEY CLUSTERED ([AppealID] ASC),
        CONSTRAINT [FK_ContentModerationAppeals_User]
            FOREIGN KEY ([UserID]) REFERENCES [dbo].[Users]([UserID]),
        CONSTRAINT [FK_ContentModerationAppeals_ReviewedBy]
            FOREIGN KEY ([ReviewedBy]) REFERENCES [dbo].[Users]([UserID]),
        CONSTRAINT [CK_ContentModerationAppeals_ContentType]
            CHECK ([ContentType] IN (N'Movie', N'Series', N'Episode', N'Chapter')),
        CONSTRAINT [CK_ContentModerationAppeals_Status]
            CHECK ([Status] IN (N'Pending', N'Approved', N'Rejected'))
    );

    PRINT N'Da tao bang ContentModerationAppeals.';
END
ELSE
BEGIN
    PRINT N'Bo qua: Bang ContentModerationAppeals da ton tai.';
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_ContentModerationAppeals_Status_CreatedAt'
      AND object_id = OBJECT_ID(N'[dbo].[ContentModerationAppeals]')
)
BEGIN
    CREATE NONCLUSTERED INDEX [IX_ContentModerationAppeals_Status_CreatedAt]
        ON [dbo].[ContentModerationAppeals] ([Status] ASC, [CreatedAt] DESC)
        INCLUDE ([ContentType], [ContentID], [UserID], [ReviewedBy]);

    PRINT N'Da tao index IX_ContentModerationAppeals_Status_CreatedAt.';
END
ELSE
BEGIN
    PRINT N'Bo qua: Index IX_ContentModerationAppeals_Status_CreatedAt da ton tai.';
END
GO

PRINT N'=== HOAN THANH CAP NHAT CSDL MODERATION ===';
GO
