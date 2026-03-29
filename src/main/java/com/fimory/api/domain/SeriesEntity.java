package com.fimory.api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "Series")
public class SeriesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SeriesID")
    private Long id;

    @Column(name = "Slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "Title", nullable = false)
    private String title;

    @Column(name = "Description")
    private String description;

    @Column(name = "CoverURL")
    private String coverUrl;

    @Column(name = "UploaderID", nullable = false)
    private Long uploaderId;

    @Column(name = "Status")
    private String status;

    @Column(name = "IsFree")
    private Boolean isFree;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsFree() {
        return isFree;
    }

    public void setIsFree(Boolean free) {
        isFree = free;
    }
}
