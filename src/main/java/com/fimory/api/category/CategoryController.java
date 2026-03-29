package com.fimory.api.category;

import com.fimory.api.common.ApiResponse;
import com.fimory.api.movie.MovieDto;
import com.fimory.api.movie.MovieService;
import com.fimory.api.series.SeriesDto;
import com.fimory.api.series.SeriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CategoryController {

    private final CategoryService categoryService;
    private final MovieService movieService;
    private final SeriesService seriesService;

    public CategoryController(CategoryService categoryService, MovieService movieService, SeriesService seriesService) {
        this.categoryService = categoryService;
        this.movieService = movieService;
        this.seriesService = seriesService;
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> categories() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getAll()));
    }

    @GetMapping("/categories/{categoryId}/movies")
    public ResponseEntity<ApiResponse<List<MovieDto>>> moviesByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.ok(movieService.getMoviesByCategory(categoryId)));
    }

    @GetMapping("/categories/{categoryId}/series")
    public ResponseEntity<ApiResponse<List<SeriesDto>>> storiesByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.ok(seriesService.getStoriesByCategory(categoryId)));
    }
}
