package com.fimory.api.video;

import com.fimory.api.domain.VideoEntity;
import com.fimory.api.repository.VideoRepository;
import com.fimory.api.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class VideoService {

    private static final long MAX_UPLOAD_BYTES = 5L * 1024L * 1024L * 1024L; // 5GB

    private final VideoRepository videoRepository;
    private final VideoStorageService videoStorageService;
    private final VideoProcessingService videoProcessingService;
    private final VideoStreamTokenService videoStreamTokenService;

    public VideoService(VideoRepository videoRepository,
                        VideoStorageService videoStorageService,
                        VideoProcessingService videoProcessingService,
                        VideoStreamTokenService videoStreamTokenService) {
        this.videoRepository = videoRepository;
        this.videoStorageService = videoStorageService;
        this.videoProcessingService = videoProcessingService;
        this.videoStreamTokenService = videoStreamTokenService;
    }

    @Transactional
    public VideoDto uploadVideo(MultipartFile file, AuthenticatedUser user) {
        validateUpload(file);

        String ext = getExtension(file.getOriginalFilename());
        Path tempFile;
        try {
            tempFile = Files.createTempFile("upload-video-", ext);
            file.transferTo(tempFile);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot persist uploaded file: " + ex.getMessage(), ex);
        }

        String originalPath = videoStorageService.storeOriginal(tempFile, ext);

        try {
            Files.deleteIfExists(tempFile);
        } catch (Exception ignored) {
        }

        VideoEntity entity = new VideoEntity();
        entity.setOriginalPath(originalPath);
        entity.setStatus(VideoStatus.PROCESSING);
        entity.setCreatedAt(Instant.now());
        VideoEntity saved = videoRepository.save(entity);

        videoProcessingService.processVideoAsync(saved.getId());
        return toDto(saved);
    }

    public VideoDto getVideo(Long id) {
        VideoEntity entity = videoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found"));
        return toDto(entity);
    }

    public String issueStreamToken(Long videoId, Long userId) {
        VideoEntity entity = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found"));
        if (entity.getStatus() != VideoStatus.READY) {
            throw new IllegalStateException("Video is not ready for streaming");
        }
        return videoStreamTokenService.issueToken(videoId, userId);
    }

    public StreamTokenPayload verifyToken(String token, Long videoId) {
        StreamTokenPayload payload = videoStreamTokenService.verify(token);
        if (!videoId.equals(payload.videoId())) {
            throw new IllegalArgumentException("Token does not match video id");
        }
        return payload;
    }

    public Path resolveMasterPlaylist(Long videoId) {
        VideoEntity entity = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found"));
        if (entity.getStatus() != VideoStatus.READY) {
            throw new IllegalStateException("Video is not ready");
        }
        Path videoDir = videoStorageService.resolveHlsVideoDir(videoId);
        return videoDir.resolve("master.m3u8").normalize();
    }

    public Path resolveVariantPlaylist(Long videoId, String variant) {
        validateVariant(variant);
        Path videoDir = videoStorageService.resolveHlsVideoDir(videoId);
        Path playlist = videoDir.resolve(variant).resolve("index.m3u8").normalize();
        if (!playlist.startsWith(videoDir)) {
            throw new IllegalStateException("Invalid variant path");
        }
        if (!Files.exists(playlist)) {
            throw new IllegalArgumentException("Variant playlist not found");
        }
        return playlist;
    }

    public Path resolveSegment(Long videoId, String variant, String segmentName) {
        validateVariant(variant);
        if (segmentName == null || !segmentName.endsWith(".ts") || segmentName.contains("..") || segmentName.contains("/") || segmentName.contains("\\")) {
            throw new IllegalArgumentException("Invalid segment name");
        }
        Path videoDir = videoStorageService.resolveHlsVideoDir(videoId);
        Path segment = videoDir.resolve(variant).resolve(segmentName).normalize();
        if (!segment.startsWith(videoDir)) {
            throw new IllegalStateException("Invalid segment path");
        }
        if (!Files.exists(segment)) {
            throw new IllegalArgumentException("Segment not found");
        }
        return segment;
    }

    public String rewriteMasterPlaylist(String raw, Long videoId, String token) {
        String[] lines = raw.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.endsWith("/index.m3u8")) {
                String variant = line.substring(0, line.indexOf('/'));
                sb.append("/api/videos/")
                        .append(videoId)
                        .append("/variants/")
                        .append(variant)
                        .append("/index.m3u8?token=")
                        .append(token)
                        .append('\n');
            } else {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    public String rewriteVariantPlaylist(String raw, Long videoId, String variant, String token, StreamTokenPayload tokenPayload) {
        String[] lines = raw.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                sb.append(line).append('\n');
                continue;
            }
            sb.append("/api/videos/")
                    .append(videoId)
                    .append("/segments/")
                    .append(variant)
                    .append("/")
                    .append(line)
                    .append("?token=")
                    .append(token)
                    .append("&uid=")
                    .append(tokenPayload.userId())
                    .append("&ts=")
                    .append(Instant.now().getEpochSecond())
                    .append('\n');
        }
        return sb.toString();
    }

    public Map<String, Object> dynamicWatermarkInstruction(StreamTokenPayload payload) {
        return Map.of(
                "userId", payload.userId(),
                "issuedAt", payload.issuedAtEpochSec(),
                "serverTimestamp", Instant.now().toString(),
                "recommendation", "Render as moving overlay in player to reduce piracy"
        );
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Video file is required");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File is too large. Maximum: 5GB");
        }

        String filename = file.getOriginalFilename();
        String ext = getExtension(filename).toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!".mp4".equals(ext) && !".mov".equals(ext)) {
            throw new IllegalArgumentException("Unsupported video type. Allowed: mp4, mov");
        }
        if (!contentType.isBlank()
                && !contentType.contains("mp4")
                && !contentType.contains("quicktime")
                && !contentType.contains("octet-stream")) {
            throw new IllegalArgumentException("Invalid content type for video upload");
        }
    }

    private void validateVariant(String variant) {
        if (!"720p".equals(variant) && !"480p".equals(variant)) {
            throw new IllegalArgumentException("Unsupported variant: " + variant);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return ".mp4";
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0) {
            return ".mp4";
        }
        return filename.substring(idx);
    }

    private VideoDto toDto(VideoEntity entity) {
        return new VideoDto(
                entity.getId(),
                entity.getOriginalPath(),
                entity.getHlsPath(),
                entity.getStatus().name(),
                entity.getCreatedAt(),
                entity.getErrorMessage()
        );
    }
}
