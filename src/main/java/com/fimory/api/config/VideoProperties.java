package com.fimory.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.video")
public class VideoProperties {

    private String storageRoot = "video-storage";
    private String originalDir = "original";
    private String hlsDir = "hls";
    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    private String watermarkLogoPath = "uploads/watermark/logo.png";
    private Integer hlsSegmentDurationSec = 6;
    private Integer streamTokenTtlSec = 3600;
    private String streamTokenSecret = "change-this-secret-for-production";
    private DynamicWatermarkMode dynamicWatermarkMode = DynamicWatermarkMode.FRONTEND_OVERLAY;

    public enum DynamicWatermarkMode {
        FRONTEND_OVERLAY,
        SERVER_SIDE_PERSONALIZED
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public String getOriginalDir() {
        return originalDir;
    }

    public void setOriginalDir(String originalDir) {
        this.originalDir = originalDir;
    }

    public String getHlsDir() {
        return hlsDir;
    }

    public void setHlsDir(String hlsDir) {
        this.hlsDir = hlsDir;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public String getFfprobePath() {
        return ffprobePath;
    }

    public void setFfprobePath(String ffprobePath) {
        this.ffprobePath = ffprobePath;
    }

    public String getWatermarkLogoPath() {
        return watermarkLogoPath;
    }

    public void setWatermarkLogoPath(String watermarkLogoPath) {
        this.watermarkLogoPath = watermarkLogoPath;
    }

    public Integer getHlsSegmentDurationSec() {
        return hlsSegmentDurationSec;
    }

    public void setHlsSegmentDurationSec(Integer hlsSegmentDurationSec) {
        this.hlsSegmentDurationSec = hlsSegmentDurationSec;
    }

    public Integer getStreamTokenTtlSec() {
        return streamTokenTtlSec;
    }

    public void setStreamTokenTtlSec(Integer streamTokenTtlSec) {
        this.streamTokenTtlSec = streamTokenTtlSec;
    }

    public String getStreamTokenSecret() {
        return streamTokenSecret;
    }

    public void setStreamTokenSecret(String streamTokenSecret) {
        this.streamTokenSecret = streamTokenSecret;
    }

    public DynamicWatermarkMode getDynamicWatermarkMode() {
        return dynamicWatermarkMode;
    }

    public void setDynamicWatermarkMode(DynamicWatermarkMode dynamicWatermarkMode) {
        this.dynamicWatermarkMode = dynamicWatermarkMode;
    }
}
