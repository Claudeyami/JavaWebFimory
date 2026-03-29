# Video Backend (Spring Boot + FFmpeg + HLS)

This module is integrated into the existing backend and provides:

- Upload API (`POST /api/videos/upload`)
- Async transcoding to HLS (720p, 480p)
- Static watermark during transcoding
- Token-protected streaming endpoints
- Dynamic watermark instruction endpoint

## Project Structure

```
src/main/java/com/fimory/api/
  config/
    VideoProperties.java
    VideoModuleConfig.java
  domain/
    VideoEntity.java
  repository/
    VideoRepository.java
  video/
    VideoController.java
    VideoService.java
    VideoProcessingService.java
    VideoStorageService.java
    FfmpegService.java
    VideoStreamTokenService.java
    StreamTokenPayload.java
    VideoDto.java
    VideoStatus.java
    VideoSchemaInitializer.java
    JsonUtil.java
```

## API Endpoints

- `POST /api/videos/upload` (multipart field `file`)
- `GET /api/videos/{id}` (video metadata/status)
- `GET /api/videos/{id}/stream` (auth required, returns master playlist with signed links)
- `GET /api/videos/{id}/variants/{variant}/index.m3u8?token=...`
- `GET /api/videos/{id}/segments/{variant}/{segmentName}?token=...`
- `GET /api/videos/{id}/watermark?token=...`

## FFmpeg Transcoding Strategy

Each upload is transcoded to:

- `720p/index.m3u8` + `segment_XXX.ts`
- `480p/index.m3u8` + `segment_XXX.ts`
- `master.m3u8` linking both variants

Static watermark is applied via overlay (bottom-right).

Equivalent FFmpeg pattern:

```bash
ffmpeg -y -i input.mp4 -i logo.png \
  -filter_complex "[0:v]scale=1280:-2:force_original_aspect_ratio=decrease[v0];[v0][1:v]overlay=W-w-20:H-h-20,format=yuv420p[vout]" \
  -map [vout] -map 0:a? \
  -c:v libx264 -preset veryfast -profile:v main \
  -g 48 -keyint_min 48 -sc_threshold 0 \
  -b:v 2800k -maxrate 2800k -bufsize 5000k \
  -c:a aac -b:a 128k -ar 48000 -ac 2 \
  -hls_time 6 -hls_playlist_type vod -hls_flags independent_segments \
  -hls_segment_filename 720p/segment_%03d.ts 720p/index.m3u8
```

## Storage Layout

```
video-storage/
  original/
    <uuid>.mp4
  hls/
    <videoId>/
      master.m3u8
      720p/
        index.m3u8
        segment_001.ts
      480p/
        index.m3u8
        segment_001.ts
```

## Dynamic Watermark (Anti-piracy)

### Option A: Server-side personalized stream (stronger)
- Re-transcode/overlay per user/session.
- Pros: hard to bypass, watermark burned into stream.
- Cons: expensive CPU/GPU, high infra cost.

### Option B: Frontend dynamic overlay (current recommendation)
- API provides `userId` + timestamps for moving watermark layer in player.
- Pros: low server cost, easy to scale.
- Cons: weaker against advanced capture workflows.

## Production Scaling

1. Store original + HLS output on object storage (S3 compatible).
2. Put CDN in front of HLS segments (CloudFront/Cloudflare).
3. Move transcoding to worker queue:
   - API writes DB job
   - workers consume and run FFmpeg
4. Add ABR ladder expansion (1080p/360p/mobile).
5. Add thumbnail generation and preview sprites.
6. Use signed URLs/JWT with short TTL and optional IP binding.
7. Add chunked upload (TUS or multipart chunk APIs) for very large files.

## Performance Notes

- HLS segment size/time can be tuned (`app.video.hls-segment-duration-sec`).
- Keep playlists short-lived in cache; segments can be cached longer.
- Avoid reading entire segment files into memory (controller uses range streaming).
- Prefer async transcoding + non-blocking upload response.

