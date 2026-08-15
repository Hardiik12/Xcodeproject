package com.communityott.content.repository;

import com.communityott.content.entity.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LanguageRepository extends JpaRepository<Language, Long> {

    Optional<Language> findByCode(String code);

    Optional<Language> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    List<Language> findByActiveTrueOrderByNameAsc();

    List<Language> findAllByOrderByCreatedAtDesc();
}
