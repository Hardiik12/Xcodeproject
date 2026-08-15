package com.communityott.content.repository;

import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentCategory;
import com.communityott.content.entity.ContentCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentCategoryRepository extends JpaRepository<ContentCategory, ContentCategoryId> {

    List<ContentCategory> findByContentId(Long contentId);

    List<ContentCategory> findByCategoryId(Long categoryId);

    void deleteByContentId(Long contentId);

    void deleteByContentIdAndCategoryId(Long contentId, Long categoryId);

    boolean existsByContentIdAndCategoryId(Long contentId, Long categoryId);
}
