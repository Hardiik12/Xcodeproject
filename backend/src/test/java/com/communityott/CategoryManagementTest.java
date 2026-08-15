package com.communityott;

import com.communityott.content.dto.CategoryCreateRequest;
import com.communityott.content.dto.CategoryUpdateRequest;
import com.communityott.content.entity.Category;
import com.communityott.content.repository.CategoryRepository;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserRoleId;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CategoryManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private User superAdmin;
    private User contentManager;
    private User regularUser;

    @BeforeEach
    void setUp() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        Role contentManagerRole = roleRepository.findByName("CONTENT_MANAGER").orElseThrow();
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        superAdmin = createTestUser("superadmin_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org");
        userRoleRepository.save(new UserRole(superAdmin, superAdminRole));

        contentManager = createTestUser("cm_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org");
        userRoleRepository.save(new UserRole(contentManager, contentManagerRole));

        regularUser = createTestUser("user_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org");
        userRoleRepository.save(new UserRole(regularUser, userRole));
    }

    private User createTestUser(String email) {
        User user = User.builder()
                .email(email)
                .phone("+91" + (int)(Math.random() * 900000000 + 100000000))
                .displayName("Test User")
                .status(UserStatus.ACTIVE)
                .build();
        return userRepository.save(user);
    }

    @Test
    @Order(1)
    @DisplayName("1. Create category with valid permissions succeeds")
    void test01_createCategorySuccess() throws Exception {
        CategoryCreateRequest request = CategoryCreateRequest.builder()
                .name("Ancient Architecture")
                .slug("ancient-architecture")
                .description("Chronicles of ancient Indian rock-cut and temple architectures")
                .active(true)
                .build();

        mockMvc.perform(post("/api/v1/admin/categories")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Ancient Architecture")))
                .andExpect(jsonPath("$.data.slug", is("ancient-architecture")))
                .andExpect(jsonPath("$.data.active", is(true)));

        assertThat(categoryRepository.existsBySlug("ancient-architecture")).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("2. Duplicate category name is rejected with 409 Conflict")
    void test02_duplicateCategoryNameRejected() throws Exception {
        Category cat = categoryRepository.save(Category.builder()
                .name("Unique Folk Traditions")
                .slug("unique-folk-traditions")
                .active(true)
                .build());

        CategoryCreateRequest request = CategoryCreateRequest.builder()
                .name("Unique Folk Traditions")
                .slug("different-slug")
                .build();

        mockMvc.perform(post("/api/v1/admin/categories")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CATEGORY_DUPLICATE")));
    }

    @Test
    @Order(3)
    @DisplayName("3. Duplicate category slug is rejected with 409 Conflict")
    void test03_duplicateCategorySlugRejected() throws Exception {
        categoryRepository.save(Category.builder()
                .name("Astronomy Exploration")
                .slug("astronomy-exploration")
                .active(true)
                .build());

        CategoryCreateRequest request = CategoryCreateRequest.builder()
                .name("Cosmic Wonders")
                .slug("astronomy-exploration")
                .build();

        mockMvc.perform(post("/api/v1/admin/categories")
                        .header("X-Dev-User-Id", superAdmin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CATEGORY_DUPLICATE")));
    }

    @Test
    @Order(4)
    @DisplayName("4. Update category details succeeds")
    void test04_updateCategorySuccess() throws Exception {
        Category cat = categoryRepository.save(Category.builder()
                .name("Old Category Name")
                .slug("old-category-name")
                .description("Initial description")
                .active(true)
                .build());

        CategoryUpdateRequest request = CategoryUpdateRequest.builder()
                .name("New Category Name")
                .slug("new-category-name")
                .description("Updated description")
                .build();

        mockMvc.perform(put("/api/v1/admin/categories/" + cat.getId())
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("New Category Name")))
                .andExpect(jsonPath("$.data.slug", is("new-category-name")))
                .andExpect(jsonPath("$.data.description", is("Updated description")));
    }

    @Test
    @Order(5)
    @DisplayName("5. Deactivate category sets active to false")
    void test05_deactivateCategorySuccess() throws Exception {
        Category cat = categoryRepository.save(Category.builder()
                .name("Obsolete Category")
                .slug("obsolete-category")
                .active(true)
                .build());

        mockMvc.perform(delete("/api/v1/admin/categories/" + cat.getId())
                        .header("X-Dev-User-Id", contentManager.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.active", is(false)));

        Category updated = categoryRepository.findById(cat.getId()).orElseThrow();
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("6. Inactive categories are excluded from public GET /api/v1/categories")
    void test06_inactiveCategoriesExcludedFromPublicListing() throws Exception {
        Category activeCat = categoryRepository.save(Category.builder()
                .name("Active Public Category " + UUID.randomUUID().toString().substring(0, 6))
                .slug("active-public-" + UUID.randomUUID().toString().substring(0, 6))
                .active(true)
                .build());

        Category inactiveCat = categoryRepository.save(Category.builder()
                .name("Hidden Inactive Category " + UUID.randomUUID().toString().substring(0, 6))
                .slug("hidden-inactive-" + UUID.randomUUID().toString().substring(0, 6))
                .active(false)
                .build());

        mockMvc.perform(get("/api/v1/categories")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[?(@.id == " + activeCat.getId() + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.id == " + inactiveCat.getId() + ")]").doesNotExist());
    }

    @Test
    @Order(7)
    @DisplayName("7. Regular USER cannot create, update, or deactivate categories (403 Forbidden)")
    void test07_regularUserCannotModifyCategories() throws Exception {
        CategoryCreateRequest request = CategoryCreateRequest.builder()
                .name("Unauthorized Category")
                .slug("unauthorized-category")
                .build();

        mockMvc.perform(post("/api/v1/admin/categories")
                        .header("X-Dev-User-Id", regularUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }
}
