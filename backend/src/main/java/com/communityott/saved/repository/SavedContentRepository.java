package com.communityott.saved.repository;

import com.communityott.saved.entity.SavedContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SavedContentRepository extends JpaRepository<SavedContent, Long> {

    @Query(value = """
            SELECT sc FROM SavedContent sc
            JOIN FETCH sc.content c
            WHERE sc.user.id = :userId
            """,
            countQuery = """
            SELECT count(sc) FROM SavedContent sc
            WHERE sc.user.id = :userId
            """)
    Page<SavedContent> findByUserIdWithContent(@Param("userId") Long userId, Pageable pageable);

    Optional<SavedContent> findByUserIdAndContentId(Long userId, Long contentId);

    boolean existsByUserIdAndContentId(Long userId, Long contentId);

    void deleteByUserIdAndContentId(Long userId, Long contentId);

    void deleteAllByUserId(Long userId);
}
