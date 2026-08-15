package com.communityott.content.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.content.dto.CategoryResponse;
import com.communityott.content.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Category Discovery API", description = "Public & consumer endpoints for retrieving active content categories")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CATEGORY_VIEW')")
    @Operation(summary = "List active categories", description = "Retrieves all active categories for catalog filtering and discovery.")
    public ApiResponse<List<CategoryResponse>> getActiveCategories() {
        List<CategoryResponse> categories = categoryService.getActiveCategories();
        return ApiResponse.success(categories, "Active categories retrieved successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CATEGORY_VIEW')")
    @Operation(summary = "Get category by ID", description = "Retrieves details for a specific category.")
    public ApiResponse<CategoryResponse> getCategoryById(@PathVariable Long id) {
        CategoryResponse category = categoryService.getCategoryById(id);
        return ApiResponse.success(category, "Category details retrieved successfully");
    }
}
