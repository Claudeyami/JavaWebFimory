package com.fimory.api.domain;

import com.fimory.api.video.VideoStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "Videos")
public class VideoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VideoID")
    private Long id;

    @Column(name = "OriginalPath", nullable = false, length = 1000)
    private String originalPath;

    @Column(name = "HlsPath", length = 1000)
    private String hlsPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 30)
    private VideoStatus status;

    @Column(name = "CreatedAt", nullable = false)
    private Instant createdAt;

    @Column(name = "ErrorMessage", length = 2000)
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalPath() {
        return originalPath;
    }

    public void setOriginalPath(String originalPath) {
        this.originalPath = originalPath;
    }

    public String getHlsPath() {
        return hlsPath;
    }

    public void setHlsPath(String hlsPath) {
        this.hlsPath = hlsPath;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public void setStatus(VideoStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
