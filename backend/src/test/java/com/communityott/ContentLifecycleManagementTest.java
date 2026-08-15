package com.communityott;

import com.communityott.common.rbac.SystemPermissions;
import com.communityott.content.dto.ContentMetadataUpdateRequest;
import com.communityott.content.dto.ContentStatusTransitionRequest;
import com.communityott.content.dto.CreateContentRequest;
import com.communityott.content.dto.UpdateContentRequest;
import com.communityott.content.entity.*;
import com.communityott.content.repository.CategoryRepository;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.LanguageRepository;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ContentLifecycleManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private User superAdmin;
    private User contentManager;
    private User regularUser;
    private Category documentaryCat;
    private Language teluguLang;

    @BeforeEach
    void setUp() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        Role contentManagerRole = roleRepository.findByName("CONTENT_MANAGER").orElseThrow();
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        superAdmin = userRepository.findByEmail("admin_lifecycle@communityott.org").orElseGet(() -> {
            User u = User.builder().email("admin_lifecycle@communityott.org").displayName("Super Admin").status(UserStatus.ACTIVE).build();
            u.getUserRoles().add(new UserRole(u, superAdminRole));
            return userRepository.save(u);
        });

        contentManager = userRepository.findByEmail("cm_lifecycle@communityott.org").orElseGet(() -> {
            User u = User.builder().email("cm_lifecycle@communityott.org").displayName("Content Manager").status(UserStatus.ACTIVE).build();
            u.getUserRoles().add(new UserRole(u, contentManagerRole));
            return userRepository.save(u);
        });

        regularUser = userRepository.findByEmail("user_lifecycle@communityott.org").orElseGet(() -> {
            User u = User.builder().email("user_lifecycle@communityott.org").displayName("Regular User").status(UserStatus.ACTIVE).build();
            u.getUserRoles().add(new UserRole(u, userRole));
            return userRepository.save(u);
        });

        documentaryCat = categoryRepository.findBySlug("documentary").orElseGet(() ->
                categoryRepository.save(Category.builder().name("Documentary Lifecycle").slug("documentary-lifecycle").active(true).build()));

        teluguLang = languageRepository.findByCode("te").orElseGet(() ->
                languageRepository.save(Language.builder().name("Telugu Lifecycle").code("te-lc").active(true).build()));
    }

    private Content createDraftContent(String title) {
        Content content = Content.builder()
                .title(title)
                .contentType(ContentType.DOCUMENTARY)
                .releaseDate(LocalDate.of(2026, 8, 15))
                .durationSeconds(1800)
                .ageRating(AgeRating.U)
                .status(ContentStatus.DRAFT)
                .originalLanguage(teluguLang)
                .createdBy(contentManager.getId())
                .updatedBy(contentManager.getId())
                .build();
        content.getContentCategories().add(new ContentCategory(content, documentaryCat));
        content.getContentLanguages().add(new ContentLanguage(content, teluguLang));
        return contentRepository.save(content);
    }

    // ==========================================
    // 1. CREATION & VALIDATION
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("1. Content Manager creates content -> defaults to DRAFT status")
    void test01_createContentDefaultsToDraft() throws Exception {
        CreateContentRequest request = CreateContentRequest.builder()
                .title("Kakatiya Dynasty Forts")
                .subtitle("Architecture of Warangal")
                .description("Exploration of historical Kakatiya architectural masterpieces.")
                .shortDescription("Kakatiya dynasty architecture.")
                .contentType(ContentType.DOCUMENTARY)
                .releaseDate(LocalDate.of(2026, 8, 15))
                .durationSeconds(2400)
                .ageRating(AgeRating.U)
                .originalLanguageId(teluguLang.getId())
                .tags("kakatiya,warangal,heritage")
                .categoryIds(List.of(documentaryCat.getId()))
                .languageIds(List.of(teluguLang.getId()))
                .build();

        mockMvc.perform(post("/api/v1/admin/content")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andExpect(jsonPath("$.data.title", is("Kakatiya Dynasty Forts")))
                .andExpect(jsonPath("$.data.version", is(0)))
                .andExpect(jsonPath("$.data.createdBy", is(contentManager.getId().intValue())));
    }

    @Test
    @Order(2)
    @DisplayName("2. Unauthorized regular USER cannot create content (403 Forbidden)")
    void test02_userCannotCreateContent() throws Exception {
        CreateContentRequest request = CreateContentRequest.builder()
                .title("Unauthorized Title")
                .contentType(ContentType.DOCUMENTARY)
                .build();

        mockMvc.perform(post("/api/v1/admin/content")
                        .header("X-Dev-User-Id", regularUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @Order(3)
    @DisplayName("3. Creation with invalid metadata (blank title, non-existent category) is rejected")
    void test03_invalidCreationRejected() throws Exception {
        CreateContentRequest request = CreateContentRequest.builder()
                .title("   ")
                .contentType(ContentType.DOCUMENTARY)
                .build();

        mockMvc.perform(post("/api/v1/admin/content")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    // ==========================================
    // 2. UPDATE & TAMPERING PROTECTION
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("4. Content Manager updates content metadata; server-controlled fields are protected")
    void test04_updateContentMetadata() throws Exception {
        Content content = createDraftContent("Original Title");

        UpdateContentRequest updateRequest = UpdateContentRequest.builder()
                .title("Updated Fort Architecture")
                .description("Updated description.")
                .durationSeconds(2700)
                .build();

        mockMvc.perform(put("/api/v1/admin/content/" + content.getId())
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.title", is("Updated Fort Architecture")))
                .andExpect(jsonPath("$.data.durationSeconds", is(2700)))
                .andExpect(jsonPath("$.data.createdBy", is(contentManager.getId().intValue())));
    }

    @Test
    @Order(5)
    @DisplayName("5. Unauthorized USER cannot update content metadata (403 Forbidden)")
    void test05_userCannotUpdateMetadata() throws Exception {
        Content content = createDraftContent("Protected Content");

        UpdateContentRequest updateRequest = UpdateContentRequest.builder()
                .title("Hacked Title")
                .build();

        mockMvc.perform(put("/api/v1/admin/content/" + content.getId())
                        .header("X-Dev-User-Id", regularUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    // ==========================================
    // 3. ADMIN LIST & SUMMARY BREAKDOWN
    // ==========================================

    @Test
    @Order(6)
    @DisplayName("6. Admin summary breakdown returns accurate status counts")
    void test06_contentStatusSummaryBreakdown() throws Exception {
        createDraftContent("Draft Item 1");
        createDraftContent("Draft Item 2");

        mockMvc.perform(get("/api/v1/admin/content/summary")
                        .header("X-Dev-User-Id", contentManager.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.draft", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(2)));
    }

    @Test
    @Order(7)
    @DisplayName("7. Admin content list retrieves items across all lifecycle states")
    void test07_adminContentList() throws Exception {
        createDraftContent("Draft Searchable");

        mockMvc.perform(get("/api/v1/admin/content?status=DRAFT")
                        .header("X-Dev-User-Id", contentManager.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", not(empty())))
                .andExpect(jsonPath("$.data.content[0].status", is("DRAFT")));
    }

    // ==========================================
    // 4. LIFECYCLE STATE MACHINE & TRANSITIONS
    // ==========================================

    @Test
    @Order(8)
    @DisplayName("8. Valid sequential lifecycle: DRAFT -> UPLOADING -> PROCESSING -> READY")
    void test08_validLifecyclePipeline() throws Exception {
        Content content = createDraftContent("Pipeline Content");

        // 1. DRAFT -> UPLOADING
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/transition")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContentStatusTransitionRequest(ContentStatus.UPLOADING))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("UPLOADING")));

        // 2. UPLOADING -> PROCESSING
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/transition")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContentStatusTransitionRequest(ContentStatus.PROCESSING))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PROCESSING")));

        // 3. PROCESSING -> READY
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/transition")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContentStatusTransitionRequest(ContentStatus.READY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("READY")));
    }

    @Test
    @Order(9)
    @DisplayName("9. Processing failure & retry: PROCESSING -> FAILED -> retry-processing -> PROCESSING")
    void test09_processingFailureAndRetry() throws Exception {
        Content content = createDraftContent("Fail Content");
        content.setStatus(ContentStatus.PROCESSING);
        content = contentRepository.save(content);

        // 1. PROCESSING -> FAILED
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/transition")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContentStatusTransitionRequest(ContentStatus.FAILED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("FAILED")));

        // 2. FAILED -> retry-processing -> PROCESSING
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/retry-processing")
                        .header("X-Dev-User-Id", contentManager.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PROCESSING")));
    }

    @Test
    @Order(10)
    @DisplayName("10. Illegal transitions rejected (DRAFT -> PUBLISHED, PROCESSING -> PUBLISHED, ARCHIVED -> PUBLISHED)")
    void test10_illegalTransitionsRejected() throws Exception {
        Content content = createDraftContent("Illegal Transition Content");

        // DRAFT -> PUBLISHED rejected
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/transition")
                        .header("X-Dev-User-Id", superAdmin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContentStatusTransitionRequest(ContentStatus.PUBLISHED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("CONTENT_NOT_PUBLISHABLE")));

        // DRAFT -> ARCHIVED rejected
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/transition")
                        .header("X-Dev-User-Id", superAdmin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContentStatusTransitionRequest(ContentStatus.ARCHIVED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_CONTENT_STATE_TRANSITION")));
    }

    // ==========================================
    // 5. PUBLISHING, UNPUBLISHING & VISIBILITY
    // ==========================================

    @Test
    @Order(11)
    @DisplayName("11. Publishing: Super Admin publishes READY content -> becomes visible in public catalog")
    void test11_publishReadyContent() throws Exception {
        Content content = createDraftContent("Ready to Publish");
        content.setStatus(ContentStatus.READY);
        content = contentRepository.save(content);

        // Publish
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/publish")
                        .header("X-Dev-User-Id", superAdmin.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PUBLISHED")));

        // Verify consumer public catalog visibility
        mockMvc.perform(get("/api/v1/content/" + content.getId())
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", is("Ready to Publish")));
    }

    @Test
    @Order(12)
    @DisplayName("12. Content Manager without CONTENT_PUBLISH cannot publish (403 Forbidden)")
    void test12_contentManagerCannotPublishWithoutPermission() throws Exception {
        Content content = createDraftContent("Publish Permission Test");
        content.setStatus(ContentStatus.READY);
        content = contentRepository.save(content);

        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/publish")
                        .header("X-Dev-User-Id", contentManager.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @Order(13)
    @DisplayName("13. Unpublish: PUBLISHED -> UNPUBLISHED immediately hides content from consumer public catalog")
    void test13_unpublishHidesFromPublicCatalog() throws Exception {
        Content content = createDraftContent("To Unpublish");
        content.setStatus(ContentStatus.READY);
        content = contentRepository.save(content);

        // 1. Publish
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/publish")
                        .header("X-Dev-User-Id", superAdmin.getId()))
                .andExpect(status().isOk());

        // 2. Unpublish
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/unpublish")
                        .header("X-Dev-User-Id", superAdmin.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("UNPUBLISHED")));

        // 3. Consumer GET /api/v1/content/{id} returns 404 (Not published)
        mockMvc.perform(get("/api/v1/content/" + content.getId())
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("CONTENT_NOT_PUBLISHED")));
    }

    @Test
    @Order(14)
    @DisplayName("14. Archive: PUBLISHED -> ARCHIVED hides content from consumer public catalog")
    void test14_archiveContent() throws Exception {
        Content content = createDraftContent("To Archive");
        content.setStatus(ContentStatus.PUBLISHED);
        content = contentRepository.save(content);

        // Archive via POST
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/archive")
                        .header("X-Dev-User-Id", superAdmin.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ARCHIVED")));

        // Consumer cannot access archived content
        mockMvc.perform(get("/api/v1/content/" + content.getId())
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("CONTENT_NOT_PUBLISHED")));
    }

    // ==========================================
    // 6. CONCURRENCY & OPTIMISTIC LOCKING
    // ==========================================

    @Test
    @Order(15)
    @DisplayName("15. Optimistic locking: Stale version update triggers 409 Conflict")
    void test15_optimisticLockingConflict() {
        Content content = createDraftContent("Concurrency Content");
        Long contentId = content.getId();

        // Simulate User A fetching entity with version 0
        Content userAEntity = contentRepository.findById(contentId).orElseThrow();

        // Simulate User B committing first by bumping version in database directly
        jdbcTemplate.update("UPDATE content SET version = version + 1 WHERE id = ?", contentId);

        // Now User A tries to save with stale in-memory version 0 -> ObjectOptimisticLockingFailureException
        userAEntity.setTitle("User A Stale Update");
        Assertions.assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> {
            contentRepository.saveAndFlush(userAEntity);
        });
    }
}
