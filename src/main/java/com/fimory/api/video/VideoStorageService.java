package com.fimory.api.video;

import com.fimory.api.config.VideoProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class VideoStorageService {

    private final VideoProperties videoProperties;

    public VideoStorageService(VideoProperties videoProperties) {
        this.videoProperties = videoProperties;
    }

    public Path ensureAndGetOriginalRoot() {
        Path root = Path.of(videoProperties.getStorageRoot(), videoProperties.getOriginalDir())
                .toAbsolutePath()
                .normalize();
        createDirectory(root);
        return root;
    }

    public Path ensureAndGetHlsRoot() {
        Path root = Path.of(videoProperties.getStorageRoot(), videoProperties.getHlsDir())
                .toAbsolutePath()
                .normalize();
        createDirectory(root);
        return root;
    }

    public String storeOriginal(Path sourceFile, String extension) {
        Path originalRoot = ensureAndGetOriginalRoot();
        String name = UUID.randomUUID() + extension;
        Path target = originalRoot.resolve(name).normalize();
        if (!target.startsWith(originalRoot)) {
            throw new IllegalStateException("Invalid target path");
        }
        try {
            Files.copy(sourceFile, target);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to store original file: " + ex.getMessage(), ex);
        }
        return target.toString();
    }

    public Path resolveOriginalPath(String absoluteOriginalPath) {
        return Path.of(absoluteOriginalPath).toAbsolutePath().normalize();
    }

    public Path ensureHlsDirForVideo(Long videoId) {
        Path hlsRoot = ensureAndGetHlsRoot();
        Path videoDir = hlsRoot.resolve(String.valueOf(videoId)).normalize();
        if (!videoDir.startsWith(hlsRoot)) {
            throw new IllegalStateException("Invalid HLS path");
        }
        createDirectory(videoDir);
        return videoDir;
    }

    public Path resolveHlsVideoDir(Long videoId) {
        Path hlsRoot = ensureAndGetHlsRoot();
        Path videoDir = hlsRoot.resolve(String.valueOf(videoId)).normalize();
        if (!videoDir.startsWith(hlsRoot)) {
            throw new IllegalStateException("Invalid HLS path");
        }
        return videoDir;
    }

    public Path resolveWatermarkLogoPath() {
        return Path.of(videoProperties.getWatermarkLogoPath()).toAbsolutePath().normalize();
    }

    public String buildRelativeMasterPath(Long videoId) {
        return videoId + "/master.m3u8";
    }

    private void createDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create directory: " + dir + ". " + ex.getMessage(), ex);
        }
    }
}
