package com.communityott.content.repository;

import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentRepository extends JpaRepository<Content, Long>, JpaSpecificationExecutor<Content> {

    // Public catalog queries (strictly published content)
    Page<Content> findByStatus(ContentStatus status, Pageable pageable);

    Page<Content> findByStatusAndContentType(ContentStatus status, ContentType contentType, Pageable pageable);

    Optional<Content> findByIdAndStatus(Long id, ContentStatus status);

    List<Content> findByStatusAndIsFeaturedTrueOrderByCreatedAtDesc(ContentStatus status);

    // Admin queries
    Page<Content> findByContentType(ContentType contentType, Pageable pageable);

    boolean existsByTitleIgnoreCase(String title);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM Content c WHERE c.status = :status")
    long countByStatus(@org.springframework.data.repository.query.Param("status") ContentStatus status);
}
