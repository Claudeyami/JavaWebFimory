package com.fimory.api.experience;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/experience")
public class ExperienceController {

    private final ExperienceService experienceService;

    public ExperienceController(ExperienceService experienceService) {
        this.experienceService = experienceService;
    }

    @GetMapping("/user")
    public Map<String, Object> getUserExperience(@RequestParam String email) {
        return experienceService.getUserExperience(email);
    }

    @PostMapping("/movie-watch")
    public Map<String, Object> awardMovieWatchExp(@RequestBody Map<String, Object> payload) {
        String email = payload == null ? null : asString(payload.get("email"));
        Long episodeId = asLong(payload == null ? null : payload.get("episodeId"));
        Long movieId = asLong(payload == null ? null : payload.get("movieId"));
        return experienceService.awardMovieWatchExp(email, episodeId, movieId);
    }

    @PostMapping("/chapter-complete")
    public Map<String, Object> awardChapterCompleteExp(@RequestBody Map<String, Object> payload) {
        String email = payload == null ? null : asString(payload.get("email"));
        Long chapterId = asLong(payload == null ? null : payload.get("chapterId"));
        return experienceService.awardChapterReadExp(email, chapterId);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
