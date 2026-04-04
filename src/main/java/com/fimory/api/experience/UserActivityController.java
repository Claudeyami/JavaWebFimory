package com.fimory.api.experience;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activity")
public class UserActivityController {

    private final ExperienceService experienceService;

    public UserActivityController(ExperienceService experienceService) {
        this.experienceService = experienceService;
    }

    @PostMapping("/watch-episode")
    public ResponseEntity<String> finishWatchingEpisode(@RequestParam Long userId,
                                                        @RequestParam Long episodeId) {
        boolean success = experienceService.gainExpFromWatching(userId, episodeId);
        if (success) {
            return ResponseEntity.ok("Thêm EXP và Điểm thưởng xem phim thành công!");
        }
        return ResponseEntity.badRequest()
                .body("Lỗi: Không thể cộng EXP (Có thể do đã xem tập này rồi hoặc dữ liệu chưa đúng).");
    }

    @PostMapping("/read-chapter")
    public ResponseEntity<String> finishReadingChapter(@RequestParam Long userId,
                                                       @RequestParam Long chapterId) {
        boolean success = experienceService.gainExpFromReading(userId, chapterId);
        if (success) {
            return ResponseEntity.ok("Thêm EXP và Điểm thưởng đọc truyện thành công!");
        }
        return ResponseEntity.badRequest()
                .body("Lỗi: Không thể cộng EXP (Có thể do đã đọc chapter này rồi hoặc dữ liệu chưa đúng).");
    }
}
