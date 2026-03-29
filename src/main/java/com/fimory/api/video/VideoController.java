package com.fimory.api.video;

import com.fimory.api.common.ApiResponse;
import com.fimory.api.security.AuthenticatedUser;
import com.fimory.api.security.CurrentUserProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Map;

@RestController
@RequestMapping({"/videos", "/api/videos"})
public class VideoController {

    private final VideoService videoService;
    private final CurrentUserProvider currentUserProvider;

    public VideoController(VideoService videoService, CurrentUserProvider currentUserProvider) {
        this.videoService = videoService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VideoDto>> uploadVideo(@RequestPart("file") MultipartFile file) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        VideoDto dto = videoService.uploadVideo(file, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VideoDto>> getVideo(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(videoService.getVideo(id)));
    }

    @GetMapping(value = "/{id}/stream")
    public ResponseEntity<String> streamMaster(@PathVariable Long id) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        String token = videoService.issueStreamToken(id, user.userId());
        Path master = videoService.resolveMasterPlaylist(id);

        try {
            String raw = Files.readString(master);
            String rewritten = videoService.rewriteMasterPlaylist(raw, id, token);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .cacheControl(CacheControl.noStore())
                    .header("X-Stream-Token", token)
                    .body(rewritten);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read master playlist", ex);
        }
    }

    @GetMapping(value = "/{id}/variants/{variant}/index.m3u8")
    public ResponseEntity<String> streamVariant(@PathVariable Long id,
                                                @PathVariable String variant,
                                                @RequestParam String token) {
        StreamTokenPayload payload = videoService.verifyToken(token, id);
        Path variantPlaylist = videoService.resolveVariantPlaylist(id, variant);

        try {
            String raw = Files.readString(variantPlaylist);
            String rewritten = videoService.rewriteVariantPlaylist(raw, id, variant, token, payload);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .cacheControl(CacheControl.maxAge(Duration.ofSeconds(5)).cachePrivate())
                    .body(rewritten);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read variant playlist", ex);
        }
    }

    @GetMapping("/{id}/segments/{variant}/{segmentName}")
    public ResponseEntity<Resource> streamSegment(@PathVariable Long id,
                                                  @PathVariable String variant,
                                                  @PathVariable String segmentName,
                                                  @RequestParam String token,
                                                  @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        videoService.verifyToken(token, id);
        Path segment = videoService.resolveSegment(id, variant, segmentName);
        return rangeResource(segment, rangeHeader, MediaType.valueOf("video/MP2T"));
    }

    @GetMapping("/{id}/watermark")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dynamicWatermarkHint(@PathVariable Long id,
                                                                                  @RequestParam String token) {
        StreamTokenPayload payload = videoService.verifyToken(token, id);
        return ResponseEntity.ok(ApiResponse.ok(videoService.dynamicWatermarkInstruction(payload)));
    }

    private ResponseEntity<Resource> rangeResource(Path file, String rangeHeader, MediaType mediaType) {
        try {
            FileSystemResource resource = new FileSystemResource(file);
            long fileLength = resource.contentLength();

            if (rangeHeader == null || rangeHeader.isBlank() || !rangeHeader.startsWith("bytes=")) {
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .contentLength(fileLength)
                        .body(resource);
            }

            var ranges = HttpRange.parseRanges(rangeHeader);
            if (ranges.isEmpty()) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                        .build();
            }
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(fileLength);
            long end = range.getRangeEnd(fileLength);
            long contentLength = end - start + 1;
            Resource partial = new InputStreamResource(new RangeInputStream(file, start, contentLength));
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength)
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                    .contentLength(contentLength)
                    .body(partial);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot stream segment", ex);
        }
    }

    private static final class RangeInputStream extends java.io.InputStream {
        private final java.io.RandomAccessFile file;
        private long remaining;

        private RangeInputStream(Path path, long start, long length) throws java.io.IOException {
            this.file = new java.io.RandomAccessFile(path.toFile(), "r");
            this.file.seek(start);
            this.remaining = length;
        }

        @Override
        public int read() throws java.io.IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = file.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int count = file.read(b, off, toRead);
            if (count > 0) {
                remaining -= count;
            }
            return count;
        }

        @Override
        public void close() throws java.io.IOException {
            file.close();
        }
    }
}
