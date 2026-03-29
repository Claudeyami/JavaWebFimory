package com.fimory.api.rating;

import com.fimory.api.domain.MovieRatingEntity;
import com.fimory.api.domain.SeriesRatingEntity;
import com.fimory.api.repository.MovieRatingRepository;
import com.fimory.api.repository.SeriesRatingRepository;
import com.fimory.api.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class RatingService {

    private final MovieRatingRepository movieRatingRepository;
    private final SeriesRatingRepository seriesRatingRepository;

    public RatingService(MovieRatingRepository movieRatingRepository, SeriesRatingRepository seriesRatingRepository) {
        this.movieRatingRepository = movieRatingRepository;
        this.seriesRatingRepository = seriesRatingRepository;
    }

    public RatingSummaryDto getMovieRatings(Long movieId, Long userId) {
        List<MovieRatingEntity> ratings = movieRatingRepository.findByMovieId(movieId);
        double average = ratings.stream()
                .map(MovieRatingEntity::getScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        Integer myRating = userId == null ? null : movieRatingRepository.findByMovieIdAndUserId(movieId, userId).map(MovieRatingEntity::getScore).orElse(null);
        return new RatingSummaryDto(average, ratings.size(), myRating);
    }

    @Transactional
    public RatingSummaryDto rateMovie(Long movieId, AuthenticatedUser user, RatingRequest request) {
        MovieRatingEntity entity = movieRatingRepository.findByMovieIdAndUserId(movieId, user.userId())
                .orElseGet(() -> {
                    MovieRatingEntity rating = new MovieRatingEntity();
                    rating.setMovieId(movieId);
                    rating.setUserId(user.userId());
                    return rating;
                });

        entity.setScore(request.score());
        movieRatingRepository.save(entity);
        return getMovieRatings(movieId, user.userId());
    }

    public RatingSummaryDto getSeriesRatings(Long seriesId, Long userId) {
        List<SeriesRatingEntity> ratings = seriesRatingRepository.findBySeriesId(seriesId);
        double average = ratings.stream()
                .map(SeriesRatingEntity::getScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        Integer myRating = userId == null ? null : seriesRatingRepository.findBySeriesIdAndUserId(seriesId, userId).map(SeriesRatingEntity::getScore).orElse(null);
        return new RatingSummaryDto(average, ratings.size(), myRating);
    }

    @Transactional
    public RatingSummaryDto rateSeries(Long seriesId, AuthenticatedUser user, RatingRequest request) {
        SeriesRatingEntity entity = seriesRatingRepository.findBySeriesIdAndUserId(seriesId, user.userId())
                .orElseGet(() -> {
                    SeriesRatingEntity rating = new SeriesRatingEntity();
                    rating.setSeriesId(seriesId);
                    rating.setUserId(user.userId());
                    return rating;
                });

        entity.setScore(request.score());
        seriesRatingRepository.save(entity);
        return getSeriesRatings(seriesId, user.userId());
    }
}
