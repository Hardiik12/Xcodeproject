package com.communityott.content.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.content.dto.CategoryCreateRequest;
import com.communityott.content.dto.CategoryResponse;
import com.communityott.content.dto.CategoryUpdateRequest;
import com.communityott.content.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@Tag(name = "Admin Category Management API", description = "Administrative endpoints for managing content categories")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class CategoryManagementController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CATEGORY_VIEW')")
    @Operation(summary = "List all categories (Admin)", description = "Retrieves all categories including inactive ones for management.")
    public ApiResponse<List<CategoryResponse>> getAllCategories() {
        List<CategoryResponse> categories = categoryService.getAllCategoriesForAdmin();
        return ApiResponse.success(categories, "All categories retrieved successfully");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CATEGORY_CREATE')")
    @Operation(summary = "Create category", description = "Creates a new category in the catalog taxonomy. Requires CATEGORY_CREATE permission.")
    public ApiResponse<CategoryResponse> createCategory(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @Valid @RequestBody CategoryCreateRequest request) {

        CategoryResponse category = categoryService.createCategory(request, principal.getUserId());
        return ApiResponse.success(category, "Category created successfully");
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CATEGORY_UPDATE')")
    @Operation(summary = "Update category", description = "Updates an existing category. Requires CATEGORY_UPDATE permission.")
    public ApiResponse<CategoryResponse> updateCategory(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateRequest request) {

        CategoryResponse category = categoryService.updateCategory(id, request, principal.getUserId());
        return ApiResponse.success(category, "Category updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CATEGORY_DELETE')")
    @Operation(summary = "Deactivate category", description = "Deactivates a category (soft-delete / active=false) safely without breaking content associations.")
    public ApiResponse<CategoryResponse> deactivateCategory(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id) {

        CategoryResponse category = categoryService.deactivateCategory(id, principal.getUserId());
        return ApiResponse.success(category, "Category deactivated successfully");
    }
}
