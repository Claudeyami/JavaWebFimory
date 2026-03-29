package com.fimory.api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "Chapters")
public class ChapterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ChapterID")
    private Long id;

    @Column(name = "SeriesID", nullable = false)
    private Long seriesId;

    @Column(name = "ChapterNumber")
    private Integer chapterNumber;

    @Column(name = "Title")
    private String title;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(Long seriesId) {
        this.seriesId = seriesId;
    }

    public Integer getChapterNumber() {
        return chapterNumber;
    }

    public void setChapterNumber(Integer chapterNumber) {
        this.chapterNumber = chapterNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
