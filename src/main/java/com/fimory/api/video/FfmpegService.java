package com.fimory.api.video;

import com.fimory.api.config.VideoProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class FfmpegService {

    private final VideoProperties videoProperties;

    public FfmpegService(VideoProperties videoProperties) {
        this.videoProperties = videoProperties;
    }

    public void transcodeToHls(Path inputFile, Path outputDir, Path logoPath) {
        try {
            Files.createDirectories(outputDir.resolve("720p"));
            Files.createDirectories(outputDir.resolve("480p"));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create HLS output directories", ex);
        }

        boolean logoExists = logoPath != null && Files.exists(logoPath);

        runFfmpeg(buildVariantCommand(inputFile, outputDir.resolve("720p"), logoPath, logoExists, 1280, "2800k", "128k"));
        runFfmpeg(buildVariantCommand(inputFile, outputDir.resolve("480p"), logoPath, logoExists, 854, "1400k", "96k"));

        writeMasterPlaylist(outputDir);
    }

    private List<String> buildVariantCommand(Path inputFile,
                                             Path variantDir,
                                             Path logoPath,
                                             boolean logoExists,
                                             int width,
                                             String bitrate,
                                             String audioBitrate) {
        String segmentFile = variantDir.resolve("segment_%03d.ts").toString();
        String playlistFile = variantDir.resolve("index.m3u8").toString();

        List<String> command = new ArrayList<>();
        command.add(videoProperties.getFfmpegPath());
        command.add("-y");
        command.add("-i");
        command.add(inputFile.toString());

        if (logoExists) {
            command.add("-i");
            command.add(logoPath.toString());
            command.add("-filter_complex");
            command.add("[0:v]scale=" + width + ":-2:force_original_aspect_ratio=decrease[v0];[v0][1:v]overlay=W-w-20:H-h-20,format=yuv420p[vout]");
            command.add("-map");
            command.add("[vout]");
        } else {
            command.add("-vf");
            command.add("scale=" + width + ":-2:force_original_aspect_ratio=decrease,format=yuv420p");
        }

        command.add("-map");
        command.add("0:a?");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-profile:v");
        command.add("main");
        command.add("-g");
        command.add("48");
        command.add("-keyint_min");
        command.add("48");
        command.add("-sc_threshold");
        command.add("0");
        command.add("-b:v");
        command.add(bitrate);
        command.add("-maxrate");
        command.add(bitrate);
        command.add("-bufsize");
        command.add("5000k");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add(audioBitrate);
        command.add("-ar");
        command.add("48000");
        command.add("-ac");
        command.add("2");
        command.add("-hls_time");
        command.add(String.valueOf(videoProperties.getHlsSegmentDurationSec()));
        command.add("-hls_playlist_type");
        command.add("vod");
        command.add("-hls_flags");
        command.add("independent_segments");
        command.add("-hls_segment_filename");
        command.add(segmentFile);
        command.add(playlistFile);
        return command;
    }

    private void writeMasterPlaylist(Path outputDir) {
        String master = "#EXTM3U\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720\n"
                + "720p/index.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1600000,RESOLUTION=854x480\n"
                + "480p/index.m3u8\n";
        try {
            Files.writeString(outputDir.resolve("master.m3u8"), master);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write master playlist", ex);
        }
    }

    private void runFfmpeg(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("FFmpeg failed with exit code " + exitCode + ". Output:\n" + output);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("FFmpeg execution failed: " + ex.getMessage(), ex);
        }
    }
}
