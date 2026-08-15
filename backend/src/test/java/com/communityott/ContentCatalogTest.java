package com.communityott;

import com.communityott.auth.security.JwtTokenService;
import com.communityott.content.dto.CreateContentRequest;
import com.communityott.content.dto.UpdateContentRequest;
import com.communityott.content.dto.UpdateContentStatusRequest;
import com.communityott.content.entity.AgeRating;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.content.repository.ContentRepository;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@org.springframework.transaction.annotation.Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ContentCatalogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User superAdmin;
    private User contentManager;
    private User regularUser;
    private String superAdminToken;
    private String contentManagerToken;
    private String regularUserToken;

    @BeforeEach
    void setUp() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        Role contentManagerRole = roleRepository.findByName("CONTENT_MANAGER").orElseThrow();
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        superAdmin = userRepository.save(User.builder()
                .email("sa_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("Super Admin")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(superAdmin, superAdminRole));
        superAdminToken = jwtTokenService.generateAccessToken(superAdmin);

        contentManager = userRepository.save(User.builder()
                .email("cm_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("Content Manager")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(contentManager, contentManagerRole));
        contentManagerToken = jwtTokenService.generateAccessToken(contentManager);

        regularUser = userRepository.save(User.builder()
                .email("user_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("Regular User")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(regularUser, userRole));
        regularUserToken = jwtTokenService.generateAccessToken(regularUser);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        contentRepository.deleteAll();
        if (superAdmin != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(superAdmin.getId())).toList());
            userRepository.delete(superAdmin);
        }
        if (contentManager != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(contentManager.getId())).toList());
            userRepository.delete(contentManager);
        }
        if (regularUser != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(regularUser.getId())).toList());
            userRepository.delete(regularUser);
        }
    }

    // ==========================================
    // 1. ADMIN CONTENT CREATION & MANAGEMENT
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("TEST 1: Authorized Content Manager can create content in DRAFT status")
    void test1_CreateContent_ByContentManager_SucceedsInDraft() throws Exception {
        CreateContentRequest request = CreateContentRequest.builder()
                .title("Echoes of the Handloom")
                .description("Documentary about traditional weaving.")
                .contentType(ContentType.DOCUMENTARY)
                .releaseDate(LocalDate.of(2026, 8, 15))
                .durationSeconds(3600)
                .ageRating(AgeRating.U)
                .thumbnailUrl("https://cdn.communityott.org/posters/loom.jpg")
                .bannerUrl("https://cdn.communityott.org/banners/loom_wide.jpg")
                .isFeatured(true)
                .build();

        mockMvc.perform(post("/api/v1/admin/content")
                        .header("Authorization", "Bearer " + contentManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Echoes of the Handloom"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.contentType").value("DOCUMENTARY"))
                .andExpect(jsonPath("$.data.featured").value(true));
    }

    @Test
    @Order(2)
    @DisplayName("TEST 2: Regular USER cannot create content -> 403 FORBIDDEN")
    void test2_CreateContent_ByRegularUser_Returns403() throws Exception {
        CreateContentRequest request = CreateContentRequest.builder()
                .title("Unauthorized Movie")
                .contentType(ContentType.MOVIE)
                .build();

        mockMvc.perform(post("/api/v1/admin/content")
                        .header("Authorization", "Bearer " + regularUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(3)
    @DisplayName("TEST 3: Content Manager can update content metadata")
    void test3_UpdateContent_ByContentManager_Succeeds() throws Exception {
        Content content = contentRepository.save(Content.builder()
                .title("Draft Title")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.DRAFT)
                .build());

        UpdateContentRequest updateRequest = UpdateContentRequest.builder()
                .title("Refined Title")
                .description("Updated synopsis")
                .durationSeconds(4200)
                .build();

        mockMvc.perform(put("/api/v1/admin/content/" + content.getId())
                        .header("Authorization", "Bearer " + contentManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Refined Title"))
                .andExpect(jsonPath("$.data.durationSeconds").value(4200));
    }

    @Test
    @Order(4)
    @DisplayName("TEST 4: Regular USER cannot update content metadata -> 403 FORBIDDEN")
    void test4_UpdateContent_ByRegularUser_Returns403() throws Exception {
        Content content = contentRepository.save(Content.builder()
                .title("Protected Content")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.DRAFT)
                .build());

        UpdateContentRequest updateRequest = UpdateContentRequest.builder()
                .title("Tampered Title")
                .build();

        mockMvc.perform(put("/api/v1/admin/content/" + content.getId())
                        .header("Authorization", "Bearer " + regularUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ==========================================
    // 2. CONTENT LIFECYCLE & TRANSITIONS
    // ==========================================

    @Test
    @Order(5)
    @DisplayName("TEST 5: Valid lifecycle transitions work (DRAFT -> READY -> PUBLISHED)")
    void test5_LifecycleTransitions_ValidPath() throws Exception {
        Content content = contentRepository.save(Content.builder()
                .title("Lifecycle Test Film")
                .contentType(ContentType.DOCUMENTARY)
                .durationSeconds(3600)
                .status(ContentStatus.DRAFT)
                .build());

        // 1. DRAFT -> UPLOADING
        UpdateContentStatusRequest toUploading = UpdateContentStatusRequest.builder()
                .status(ContentStatus.UPLOADING)
                .build();
        mockMvc.perform(patch("/api/v1/admin/content/" + content.getId() + "/status")
                        .header("Authorization", "Bearer " + contentManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toUploading)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UPLOADING"));

        // 2. UPLOADING -> PROCESSING
        UpdateContentStatusRequest toProcessing = UpdateContentStatusRequest.builder()
                .status(ContentStatus.PROCESSING)
                .build();
        mockMvc.perform(patch("/api/v1/admin/content/" + content.getId() + "/status")
                        .header("Authorization", "Bearer " + contentManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toProcessing)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        // 3. PROCESSING -> READY
        UpdateContentStatusRequest toReady = UpdateContentStatusRequest.builder()
                .status(ContentStatus.READY)
                .build();
        mockMvc.perform(patch("/api/v1/admin/content/" + content.getId() + "/status")
                        .header("Authorization", "Bearer " + contentManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toReady)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));

        // 4. READY -> PUBLISHED (via Super Admin with CONTENT_PUBLISH)
        UpdateContentStatusRequest toPublished = UpdateContentStatusRequest.builder()
                .status(ContentStatus.PUBLISHED)
                .build();
        mockMvc.perform(patch("/api/v1/admin/content/" + content.getId() + "/status")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toPublished)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @Order(6)
    @DisplayName("TEST 6: Invalid lifecycle transition is rejected -> 400 Bad Request")
    void test6_LifecycleTransitions_InvalidTransition_Rejected() throws Exception {
        Content content = contentRepository.save(Content.builder()
                .title("Draft Content")
                .contentType(ContentType.MOVIE)
                .status(ContentStatus.DRAFT)
                .build());

        // DRAFT -> FAILED is invalid (only PROCESSING/UPLOADING can fail)
        UpdateContentStatusRequest invalidReq = UpdateContentStatusRequest.builder()
                .status(ContentStatus.FAILED)
                .build();

        mockMvc.perform(patch("/api/v1/admin/content/" + content.getId() + "/status")
                        .header("Authorization", "Bearer " + contentManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CONTENT_STATE_TRANSITION"));
    }

    @Test
    @Order(7)
    @DisplayName("TEST 7: Content archiving works and transitions status to ARCHIVED")
    void test7_ArchiveContent_Works() throws Exception {
        Content content = contentRepository.save(Content.builder()
                .title("Old Documentary")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .build());

        mockMvc.perform(delete("/api/v1/admin/content/" + content.getId())
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        Content updated = contentRepository.findById(content.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ContentStatus.ARCHIVED);
    }

    // ==========================================
    // 3. PUBLIC CATALOG SECURITY & FILTERING
    // ==========================================

    @Test
    @Order(8)
    @DisplayName("TEST 8: Public catalog strictly returns PUBLISHED content only")
    void test8_PublicCatalog_ReturnsPublishedOnly() throws Exception {
        // Create content in various states
        contentRepository.save(Content.builder().title("Published Doc 1").contentType(ContentType.DOCUMENTARY).status(ContentStatus.PUBLISHED).build());
        contentRepository.save(Content.builder().title("Draft Doc 2").contentType(ContentType.DOCUMENTARY).status(ContentStatus.DRAFT).build());
        contentRepository.save(Content.builder().title("Processing Movie 3").contentType(ContentType.MOVIE).status(ContentStatus.PROCESSING).build());
        contentRepository.save(Content.builder().title("Failed Series 4").contentType(ContentType.SERIES).status(ContentStatus.FAILED).build());
        contentRepository.save(Content.builder().title("Archived Doc 5").contentType(ContentType.DOCUMENTARY).status(ContentStatus.ARCHIVED).build());
        contentRepository.save(Content.builder().title("Unpublished Movie 6").contentType(ContentType.MOVIE).status(ContentStatus.UNPUBLISHED).build());

        mockMvc.perform(get("/api/v1/content")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[*].title").value(org.hamcrest.Matchers.hasItem("Published Doc 1")))
                .andExpect(jsonPath("$.data.content[*].title").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Draft Doc 2"))))
                .andExpect(jsonPath("$.data.content[*].title").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Processing Movie 3"))))
                .andExpect(jsonPath("$.data.content[*].title").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Failed Series 4"))))
                .andExpect(jsonPath("$.data.content[*].title").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Archived Doc 5"))))
                .andExpect(jsonPath("$.data.content[*].title").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Unpublished Movie 6"))));
    }

    @Test
    @Order(9)
    @DisplayName("TEST 9: Public content details returns 200 for PUBLISHED and 404 for non-published")
    void test9_PublicContentDetails_PublishedVsUnpublished() throws Exception {
        Content published = contentRepository.save(Content.builder()
                .title("Visible Doc")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .build());

        Content draft = contentRepository.save(Content.builder()
                .title("Hidden Draft")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.DRAFT)
                .build());

        // 1. Published content returns 200 OK
        mockMvc.perform(get("/api/v1/content/" + published.getId())
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Visible Doc"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 2. Draft content returns 404 CONTENT_NOT_PUBLISHED
        mockMvc.perform(get("/api/v1/content/" + draft.getId())
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONTENT_NOT_PUBLISHED"));

        // 3. Non-existent content returns 404 CONTENT_NOT_FOUND
        mockMvc.perform(get("/api/v1/content/999999")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONTENT_NOT_FOUND"));
    }

    @Test
    @Order(10)
    @DisplayName("TEST 10: Featured content endpoint returns only featured published content")
    void test10_FeaturedContent_ReturnsFeaturedPublishedOnly() throws Exception {
        contentRepository.save(Content.builder().title("Featured Published").contentType(ContentType.DOCUMENTARY).status(ContentStatus.PUBLISHED).isFeatured(true).build());
        contentRepository.save(Content.builder().title("Unfeatured Published").contentType(ContentType.DOCUMENTARY).status(ContentStatus.PUBLISHED).isFeatured(false).build());
        contentRepository.save(Content.builder().title("Featured Draft").contentType(ContentType.DOCUMENTARY).status(ContentStatus.DRAFT).isFeatured(true).build());

        mockMvc.perform(get("/api/v1/content/featured")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title").value(org.hamcrest.Matchers.hasItem("Featured Published")))
                .andExpect(jsonPath("$.data[*].title").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Unfeatured Published"))))
                .andExpect(jsonPath("$.data[*].title").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Featured Draft"))));
    }
}
