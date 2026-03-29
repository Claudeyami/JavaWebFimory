package com.fimory.api.video;

import com.fimory.api.domain.VideoEntity;
import com.fimory.api.repository.VideoRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

@Service
public class VideoProcessingService {

    private final VideoRepository videoRepository;
    private final VideoStorageService videoStorageService;
    private final FfmpegService ffmpegService;

    public VideoProcessingService(VideoRepository videoRepository,
                                  VideoStorageService videoStorageService,
                                  FfmpegService ffmpegService) {
        this.videoRepository = videoRepository;
        this.videoStorageService = videoStorageService;
        this.ffmpegService = ffmpegService;
    }

    @Async("videoProcessingExecutor")
    @Transactional
    public void processVideoAsync(Long videoId) {
        VideoEntity video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found: " + videoId));

        Path input = videoStorageService.resolveOriginalPath(video.getOriginalPath());
        Path outputDir = videoStorageService.ensureHlsDirForVideo(videoId);
        Path logo = videoStorageService.resolveWatermarkLogoPath();

        try {
            ffmpegService.transcodeToHls(input, outputDir, logo);
            video.setHlsPath(videoStorageService.buildRelativeMasterPath(videoId));
            video.setStatus(VideoStatus.READY);
            video.setErrorMessage(null);
        } catch (Exception ex) {
            video.setStatus(VideoStatus.FAILED);
            video.setErrorMessage(ex.getMessage());
        }

        videoRepository.save(video);
    }
}
