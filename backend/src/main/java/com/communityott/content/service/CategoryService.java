package com.communityott.content.service;

import com.communityott.common.exception.CategoryDuplicateException;
import com.communityott.common.exception.CategoryNotFoundException;
import com.communityott.content.dto.CategoryCreateRequest;
import com.communityott.content.dto.CategoryResponse;
import com.communityott.content.dto.CategoryUpdateRequest;
import com.communityott.content.entity.Category;
import com.communityott.content.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategoriesForAdmin() {
        return categoryRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
        return CategoryResponse.fromEntity(category);
    }

    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request, Long adminUserId) {
        String trimmedName = request.getName().trim();
        String slug = request.getSlug() != null && !request.getSlug().trim().isEmpty()
                ? slugify(request.getSlug())
                : slugify(trimmedName);

        if (categoryRepository.existsByName(trimmedName)) {
            throw new CategoryDuplicateException("Category with name '" + trimmedName + "' already exists");
        }

        if (categoryRepository.existsBySlug(slug)) {
            throw new CategoryDuplicateException("Category with slug '" + slug + "' already exists");
        }

        Category category = Category.builder()
                .name(trimmedName)
                .slug(slug)
                .description(request.getDescription())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Category created [id={}, name={}, slug={}] by admin [userId={}]", saved.getId(), saved.getName(), saved.getSlug(), adminUserId);
        return CategoryResponse.fromEntity(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request, Long adminUserId) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String trimmedName = request.getName().trim();
            if (!trimmedName.equalsIgnoreCase(category.getName()) && categoryRepository.existsByName(trimmedName)) {
                throw new CategoryDuplicateException("Category with name '" + trimmedName + "' already exists");
            }
            category.setName(trimmedName);
        }

        if (request.getSlug() != null && !request.getSlug().trim().isEmpty()) {
            String slug = slugify(request.getSlug());
            if (!slug.equalsIgnoreCase(category.getSlug()) && categoryRepository.existsBySlug(slug)) {
                throw new CategoryDuplicateException("Category with slug '" + slug + "' already exists");
            }
            category.setSlug(slug);
        }

        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }

        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }

        Category updated = categoryRepository.save(category);
        log.info("Category updated [id={}, name={}, active={}] by admin [userId={}]", updated.getId(), updated.getName(), updated.isActive(), adminUserId);
        return CategoryResponse.fromEntity(updated);
    }

    @Transactional
    public CategoryResponse deactivateCategory(Long id, Long adminUserId) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        category.setActive(false);
        Category saved = categoryRepository.save(category);
        log.info("Category deactivated [id={}, name={}] by admin [userId={}]", saved.getId(), saved.getName(), adminUserId);
        return CategoryResponse.fromEntity(saved);
    }

    private String slugify(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
}
