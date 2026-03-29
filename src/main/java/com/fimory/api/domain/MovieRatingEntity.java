package com.fimory.api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "MovieRatings")
public class MovieRatingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RatingID")
    private Long id;

    @Column(name = "MovieID", nullable = false)
    private Long movieId;

    @Column(name = "UserID", nullable = false)
    private Long userId;

    @Column(name = "Rating", nullable = false)
    private Integer score;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
}
