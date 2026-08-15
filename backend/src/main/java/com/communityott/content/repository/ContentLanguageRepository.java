package com.communityott.content.repository;

import com.communityott.content.entity.ContentLanguage;
import com.communityott.content.entity.ContentLanguageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentLanguageRepository extends JpaRepository<ContentLanguage, ContentLanguageId> {

    List<ContentLanguage> findByContentId(Long contentId);

    List<ContentLanguage> findByLanguageId(Long languageId);

    void deleteByContentId(Long contentId);

    void deleteByContentIdAndLanguageId(Long contentId, Long languageId);

    boolean existsByContentIdAndLanguageId(Long contentId, Long languageId);
}
