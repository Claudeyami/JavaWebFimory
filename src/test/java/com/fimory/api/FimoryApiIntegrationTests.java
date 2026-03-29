package com.fimory.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fimory.api.domain.*;
import com.fimory.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FimoryApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private MovieEpisodeRepository movieEpisodeRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @BeforeEach
    void setup() {
        chapterRepository.deleteAll();
        seriesRepository.deleteAll();
        movieEpisodeRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        RoleEntity admin = new RoleEntity();
        admin.setId(1L);
        admin.setName("ADMIN");
        roleRepository.save(admin);

        RoleEntity userRole = new RoleEntity();
        userRole.setId(2L);
        userRole.setName("USER");
        roleRepository.save(userRole);

        UserEntity adminUser = new UserEntity();
        adminUser.setEmail("admin@fimory.local");
        adminUser.setDisplayName("Admin");
        adminUser.setPasswordHash("123456");
        adminUser.setRole(admin);
        userRepository.save(adminUser);

        UserEntity normalUser = new UserEntity();
        normalUser.setEmail("user@fimory.local");
        normalUser.setDisplayName("User");
        normalUser.setPasswordHash("123456");
        normalUser.setRole(userRole);
        userRepository.save(normalUser);

        MovieEntity movie = new MovieEntity();
        movie.setSlug("movie-1");
        movie.setTitle("Movie 1");
        movie.setDescription("desc");
        movie = movieRepository.save(movie);

        MovieEpisodeEntity episode = new MovieEpisodeEntity();
        episode.setMovieId(movie.getId());
        episode.setEpisodeNumber(1);
        episode.setTitle("Episode 1");
        episode.setVideoUrl("/storage/ep1.mp4");
        movieEpisodeRepository.save(episode);

        SeriesEntity series = new SeriesEntity();
        series.setSlug("story-1");
        series.setTitle("Story 1");
        series = seriesRepository.save(series);

        ChapterEntity chapter = new ChapterEntity();
        chapter.setSeriesId(series.getId());
        chapter.setChapterNumber(1);
        chapter.setTitle("Chapter 1");
        chapterRepository.save(chapter);
    }

    @Test
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listMoviesShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].Slug").value("movie-1"));
    }

    @Test
    void movieDetailShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/movies/movie-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.Title").value("Movie 1"));
    }

    @Test
    void movieEpisodesShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/movies/movie-1/episodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].EpisodeNumber").value(1));
    }

    @Test
    void storiesShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/stories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].Slug").value("story-1"));
    }

    @Test
    void chaptersShouldReturnOk() throws Exception {
        Long seriesId = seriesRepository.findBySlug("story-1").orElseThrow().getId();
        mockMvc.perform(get("/api/stories/{seriesId}/chapters", seriesId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ChapterNumber").value(1));
    }

    @Test
    void authRoleShouldRequireHeaderAuth() throws Exception {
        mockMvc.perform(get("/api/auth/role"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authRoleShouldReturnRoleWhenHeaderExists() throws Exception {
        mockMvc.perform(get("/api/auth/role").header("x-user-email", "admin@fimory.local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void meShouldReturnCurrentUser() throws Exception {
        mockMvc.perform(get("/api/me").header("x-user-email", "user@fimory.local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("user@fimory.local"));
    }

    @Test
    void registerShouldCreateUser() throws Exception {
        String payload = objectMapper.writeValueAsString(new RegisterPayload("new@fimory.local", "123456", "New User"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("new@fimory.local"));
    }

    @Test
    void adminMoviesShouldAllowAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/movies").header("x-user-email", "admin@fimory.local"))
                .andExpect(status().isOk());
    }

    @Test
    void adminMoviesShouldRejectUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/movies").header("x-user-email", "user@fimory.local"))
                .andExpect(status().isForbidden());
    }

    private record RegisterPayload(String email, String password, String displayName) {
    }
}
