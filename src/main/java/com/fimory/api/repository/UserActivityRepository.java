package com.fimory.api.repository;

import com.fimory.api.domain.UserExpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserActivityRepository extends JpaRepository<UserExpEntity, Long> {

    @Modifying
    @Transactional
    @Query(value = "EXEC sp_Fimory_WatchEpisode_AddExp @p_UserID = :userId, @p_EpisodeID = :episodeId", nativeQuery = true)
    void addWatchEpisodeExp(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    @Modifying
    @Transactional
    @Query(value = "EXEC sp_Fimory_ReadChapter_AddExp @p_UserID = :userId, @p_ChapterID = :chapterId", nativeQuery = true)
    void addReadChapterExp(@Param("userId") Long userId, @Param("chapterId") Long chapterId);
}
