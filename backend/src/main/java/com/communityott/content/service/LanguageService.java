package com.communityott.content.service;

import com.communityott.common.exception.LanguageDuplicateException;
import com.communityott.common.exception.LanguageNotFoundException;
import com.communityott.content.dto.LanguageCreateRequest;
import com.communityott.content.dto.LanguageResponse;
import com.communityott.content.dto.LanguageUpdateRequest;
import com.communityott.content.entity.Language;
import com.communityott.content.repository.LanguageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageService {

    private final LanguageRepository languageRepository;

    @Transactional(readOnly = true)
    public List<LanguageResponse> getActiveLanguages() {
        return languageRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(LanguageResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LanguageResponse> getAllLanguagesForAdmin() {
        return languageRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(LanguageResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public LanguageResponse getLanguageById(Long id) {
        Language language = languageRepository.findById(id)
                .orElseThrow(() -> new LanguageNotFoundException(id));
        return LanguageResponse.fromEntity(language);
    }

    @Transactional
    public LanguageResponse createLanguage(LanguageCreateRequest request, Long adminUserId) {
        String trimmedName = request.getName().trim();
        String normalizedCode = request.getCode().trim().toLowerCase();

        if (languageRepository.existsByName(trimmedName)) {
            throw new LanguageDuplicateException("Language with name '" + trimmedName + "' already exists");
        }

        if (languageRepository.existsByCode(normalizedCode)) {
            throw new LanguageDuplicateException("Language with code '" + normalizedCode + "' already exists");
        }

        Language language = Language.builder()
                .name(trimmedName)
                .code(normalizedCode)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Language saved = languageRepository.save(language);
        log.info("Language created [id={}, name={}, code={}] by admin [userId={}]", saved.getId(), saved.getName(), saved.getCode(), adminUserId);
        return LanguageResponse.fromEntity(saved);
    }

    @Transactional
    public LanguageResponse updateLanguage(Long id, LanguageUpdateRequest request, Long adminUserId) {
        Language language = languageRepository.findById(id)
                .orElseThrow(() -> new LanguageNotFoundException(id));

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String trimmedName = request.getName().trim();
            if (!trimmedName.equalsIgnoreCase(language.getName()) && languageRepository.existsByName(trimmedName)) {
                throw new LanguageDuplicateException("Language with name '" + trimmedName + "' already exists");
            }
            language.setName(trimmedName);
        }

        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            String normalizedCode = request.getCode().trim().toLowerCase();
            if (!normalizedCode.equalsIgnoreCase(language.getCode()) && languageRepository.existsByCode(normalizedCode)) {
                throw new LanguageDuplicateException("Language with code '" + normalizedCode + "' already exists");
            }
            language.setCode(normalizedCode);
        }

        if (request.getActive() != null) {
            language.setActive(request.getActive());
        }

        Language updated = languageRepository.save(language);
        log.info("Language updated [id={}, name={}, active={}] by admin [userId={}]", updated.getId(), updated.getName(), updated.isActive(), adminUserId);
        return LanguageResponse.fromEntity(updated);
    }

    @Transactional
    public LanguageResponse deactivateLanguage(Long id, Long adminUserId) {
        Language language = languageRepository.findById(id)
                .orElseThrow(() -> new LanguageNotFoundException(id));

        language.setActive(false);
        Language saved = languageRepository.save(language);
        log.info("Language deactivated [id={}, name={}] by admin [userId={}]", saved.getId(), saved.getName(), adminUserId);
        return LanguageResponse.fromEntity(saved);
    }
}
