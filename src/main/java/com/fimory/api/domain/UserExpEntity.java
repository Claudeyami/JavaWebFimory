package com.fimory.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "UserExp")
public class UserExpEntity {

    @Id
    @Column(name = "UserID")
    private Long userId;

    @Column(name = "TotalExp", nullable = false)
    private Integer totalExp;

    @Column(name = "MaxExp", nullable = false)
    private Integer maxExp;

    @Column(name = "CurrentLevel", nullable = false)
    private Integer currentLevel;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getTotalExp() {
        return totalExp;
    }

    public void setTotalExp(Integer totalExp) {
        this.totalExp = totalExp;
    }

    public Integer getMaxExp() {
        return maxExp;
    }

    public void setMaxExp(Integer maxExp) {
        this.maxExp = maxExp;
    }

    public Integer getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(Integer currentLevel) {
        this.currentLevel = currentLevel;
    }
}
