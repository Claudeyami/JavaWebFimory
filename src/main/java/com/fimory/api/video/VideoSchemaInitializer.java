package com.fimory.api.video;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class VideoSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public VideoSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute("""
                IF OBJECT_ID('Videos', 'U') IS NULL
                BEGIN
                    CREATE TABLE Videos (
                        VideoID BIGINT IDENTITY(1,1) PRIMARY KEY,
                        OriginalPath NVARCHAR(1000) NOT NULL,
                        HlsPath NVARCHAR(1000) NULL,
                        Status NVARCHAR(30) NOT NULL,
                        CreatedAt DATETIME2 NOT NULL,
                        ErrorMessage NVARCHAR(2000) NULL
                    );
                    CREATE INDEX IX_Videos_Status ON Videos(Status);
                    CREATE INDEX IX_Videos_CreatedAt ON Videos(CreatedAt DESC);
                END
                """);
    }
}
