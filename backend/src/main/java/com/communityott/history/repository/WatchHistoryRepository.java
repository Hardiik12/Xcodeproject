package com.communityott.history.repository;

import com.communityott.history.entity.WatchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    @Query("SELECT wh FROM WatchHistory wh JOIN FETCH wh.content WHERE wh.user.id = :userId AND wh.content.id = :contentId")
    Optional<WatchHistory> findByUserIdAndContentId(@Param("userId") Long userId, @Param("contentId") Long contentId);

    @Query(value = "SELECT wh FROM WatchHistory wh JOIN FETCH wh.content WHERE wh.user.id = :userId",
           countQuery = "SELECT count(wh) FROM WatchHistory wh WHERE wh.user.id = :userId")
    Page<WatchHistory> findByUserIdWithContent(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM WatchHistory wh WHERE wh.user.id = :userId AND wh.content.id = :contentId")
    int deleteByUserIdAndContentId(@Param("userId") Long userId, @Param("contentId") Long contentId);

    @Modifying
    @Query("DELETE FROM WatchHistory wh WHERE wh.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);
}
