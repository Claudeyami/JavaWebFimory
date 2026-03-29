package com.fimory.api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "UserPreferences")
public class UserPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PreferenceID")
    private Long id;

    @Column(name = "UserID", nullable = false, unique = true)
    private Long userId;

    @Column(name = "Language")
    private String language;

    @Column(name = "Theme")
    private String theme;

    @Column(name = "AutoPlay")
    private Boolean autoPlay;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public Boolean getAutoPlay() {
        return autoPlay;
    }

    public void setAutoPlay(Boolean autoPlay) {
        this.autoPlay = autoPlay;
    }
}
