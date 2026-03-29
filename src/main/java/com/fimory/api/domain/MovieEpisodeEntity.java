package com.fimory.api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "MovieEpisodes")
public class MovieEpisodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EpisodeID")
    private Long id;

    @Column(name = "MovieID", nullable = false)
    private Long movieId;

    @Column(name = "EpisodeNumber")
    private Integer episodeNumber;

    @Column(name = "Title")
    private String title;

    @Column(name = "VideoURL")
    private String videoUrl;

    @Column(name = "Duration")
    private Integer duration;

    @Column(name = "ViewCount")
    private Long viewCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMovieId() {
        return movieId;
    }

    public void setMovieId(Long movieId) {
        this.movieId = movieId;
    }

    public Integer getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(Integer episodeNumber) {
        this.episodeNumber = episodeNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }
}
