package com.fimory.api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "SeriesRatings")
public class SeriesRatingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RatingID")
    private Long id;

    @Column(name = "SeriesID", nullable = false)
    private Long seriesId;

    @Column(name = "UserID", nullable = false)
    private Long userId;

    @Column(name = "Rating", nullable = false)
    private Integer score;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSeriesId() { return seriesId; }
    public void setSeriesId(Long seriesId) { this.seriesId = seriesId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
}
