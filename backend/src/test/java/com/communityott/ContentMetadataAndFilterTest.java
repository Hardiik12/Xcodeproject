package com.communityott;

import com.communityott.content.dto.*;
import com.communityott.content.entity.*;
import com.communityott.content.repository.*;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ContentMetadataAndFilterTest {

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
    private ContentCategoryRepository contentCategoryRepository;

    @Autowired
    private ContentLanguageRepository contentLanguageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private User superAdmin;
    private User contentManager;
    private User regularUser;

    private Category docCategory;
    private Category historyCategory;
    private Category scienceCategory;

    private Language teluguLanguage;
    private Language englishLanguage;
    private Language hindiLanguage;

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

        docCategory = categoryRepository.findBySlug("documentary").orElseGet(() ->
                categoryRepository.save(Category.builder().name("Documentary").slug("documentary").active(true).build()));
        historyCategory = categoryRepository.findBySlug("history").orElseGet(() ->
                categoryRepository.save(Category.builder().name("History").slug("history").active(true).build()));
        scienceCategory = categoryRepository.findBySlug("science").orElseGet(() ->
                categoryRepository.save(Category.builder().name("Science").slug("science").active(true).build()));

        teluguLanguage = languageRepository.findByCode("te").orElseGet(() ->
                languageRepository.save(Language.builder().name("Telugu").code("te").active(true).build()));
        englishLanguage = languageRepository.findByCode("en").orElseGet(() ->
                languageRepository.save(Language.builder().name("English").code("en").active(true).build()));
        hindiLanguage = languageRepository.findByCode("hi").orElseGet(() ->
                languageRepository.save(Language.builder().name("Hindi").code("hi").active(true).build()));
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

    private Content createPublishedContent(String title, ContentType type, AgeRating rating, Language origLang, Category cat, String tags) {
        Content content = Content.builder()
                .title(title)
                .subtitle("Subtitle for " + title)
                .description("Description for " + title)
                .shortDescription("Short preview for " + title)
                .contentType(type)
                .releaseDate(LocalDate.now().minusDays((long)(Math.random() * 30)))
                .durationSeconds(1800)
                .ageRating(rating)
                .status(ContentStatus.PUBLISHED)
                .originalLanguage(origLang)
                .tags(tags)
                .isFeatured(false)
                .build();

        Content saved = contentRepository.save(content);

        if (cat != null) {
            ContentCategory cc = new ContentCategory(saved, cat);
            saved.getContentCategories().add(cc);
        }

        if (origLang != null) {
            ContentLanguage cl = new ContentLanguage(saved, origLang);
            saved.getContentLanguages().add(cl);
        }

        return contentRepository.save(saved);
    }

    @Test
    @Order(1)
    @DisplayName("1. Create content with multiple categories and languages")
    void test01_createContentWithCategoriesAndLanguages() throws Exception {
        CreateContentRequest request = CreateContentRequest.builder()
                .title("Weavers of Pochampally")
                .subtitle("Ikat Craft Traditions")
                .description("Deep dive into the geometric Ikat weaving masters of Pochampally.")
                .shortDescription("Ikat weaving documentary.")
                .contentType(ContentType.DOCUMENTARY)
                .releaseDate(LocalDate.of(2026, 8, 15))
                .durationSeconds(2400)
                .ageRating(AgeRating.U)
                .originalLanguageId(teluguLanguage.getId())
                .tags("weaving,ikat,telangana,heritage")
                .categoryIds(List.of(docCategory.getId(), historyCategory.getId()))
                .languageIds(List.of(teluguLanguage.getId(), englishLanguage.getId()))
                .build();

        mockMvc.perform(post("/api/v1/admin/content")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.title", is("Weavers of Pochampally")))
                .andExpect(jsonPath("$.data.subtitle", is("Ikat Craft Traditions")))
                .andExpect(jsonPath("$.data.originalLanguage.code", is("te")))
                .andExpect(jsonPath("$.data.categories", hasSize(2)))
                .andExpect(jsonPath("$.data.languages", hasSize(2)));
    }

    @Test
    @Order(2)
    @DisplayName("2. Update content metadata, categories, and languages")
    void test02_updateContentMetadataAndTaxonomy() throws Exception {
        Content content = createPublishedContent("Temple Sculptures", ContentType.DOCUMENTARY, AgeRating.U, teluguLanguage, historyCategory, "temples,sculpture");

        ContentMetadataUpdateRequest metadataRequest = ContentMetadataUpdateRequest.builder()
                .subtitle("Architectural Marvels of the Kakatiyas")
                .shortDescription("Kakatiya stone temples.")
                .tags("kakatiya,ramappa,temples")
                .categoryIds(List.of(historyCategory.getId(), docCategory.getId()))
                .languageIds(List.of(teluguLanguage.getId(), englishLanguage.getId(), hindiLanguage.getId()))
                .build();

        mockMvc.perform(put("/api/v1/admin/content/" + content.getId() + "/metadata")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(metadataRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.subtitle", is("Architectural Marvels of the Kakatiyas")))
                .andExpect(jsonPath("$.data.categories", hasSize(2)))
                .andExpect(jsonPath("$.data.languages", hasSize(3)));
    }

    @Test
    @Order(3)
    @DisplayName("3. Assign and remove individual category from content")
    void test03_assignAndRemoveCategory() throws Exception {
        Content content = createPublishedContent("Space Odyssey", ContentType.DOCUMENTARY, AgeRating.U, englishLanguage, scienceCategory, "space,isro");

        // Assign history category
        mockMvc.perform(post("/api/v1/admin/content/" + content.getId() + "/categories/" + historyCategory.getId())
                        .header("X-Dev-User-Id", contentManager.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories", hasSize(2)));

        // Remove science category
        mockMvc.perform(delete("/api/v1/admin/content/" + content.getId() + "/categories/" + scienceCategory.getId())
                        .header("X-Dev-User-Id", contentManager.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories", hasSize(1)))
                .andExpect(jsonPath("$.data.categories[0].slug", is("history")));
    }

    @Test
    @Order(4)
    @DisplayName("4. Filter published catalog by category slug")
    void test04_filterCatalogByCategorySlug() throws Exception {
        createPublishedContent("History Doc 1", ContentType.DOCUMENTARY, AgeRating.U, teluguLanguage, historyCategory, "history");
        createPublishedContent("Science Doc 1", ContentType.DOCUMENTARY, AgeRating.U, englishLanguage, scienceCategory, "science");

        mockMvc.perform(get("/api/v1/content?category=history")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[*].title", hasItem("History Doc 1")))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Science Doc 1"))));
    }

    @Test
    @Order(5)
    @DisplayName("5. Filter published catalog by language code")
    void test05_filterCatalogByLanguage() throws Exception {
        createPublishedContent("Telugu Feature", ContentType.MOVIE, AgeRating.UA_13_PLUS, teluguLanguage, docCategory, "cinema");
        createPublishedContent("Hindi Feature", ContentType.MOVIE, AgeRating.UA_13_PLUS, hindiLanguage, docCategory, "cinema");

        mockMvc.perform(get("/api/v1/content?language=te")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[*].title", hasItem("Telugu Feature")))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Hindi Feature"))));
    }

    @Test
    @Order(6)
    @DisplayName("6. Filter published catalog by ContentType and AgeRating")
    void test06_filterCatalogByTypeAndAgeRating() throws Exception {
        createPublishedContent("Kids Animation", ContentType.MOVIE, AgeRating.U, englishLanguage, docCategory, "kids");
        createPublishedContent("Mature Crime Story", ContentType.SERIES, AgeRating.A, teluguLanguage, docCategory, "crime");

        mockMvc.perform(get("/api/v1/content?contentType=SERIES&ageRating=A")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].title", hasItem("Mature Crime Story")))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Kids Animation"))));
    }

    @Test
    @Order(7)
    @DisplayName("7. Combined multi-filter: Category + Language + ContentType")
    void test07_combinedMultiFilter() throws Exception {
        createPublishedContent("Target Heritage Item", ContentType.DOCUMENTARY, AgeRating.U, teluguLanguage, historyCategory, "heritage");
        createPublishedContent("Other Telugu Sci", ContentType.DOCUMENTARY, AgeRating.U, teluguLanguage, scienceCategory, "science");
        createPublishedContent("Other English History", ContentType.DOCUMENTARY, AgeRating.U, englishLanguage, historyCategory, "history");

        mockMvc.perform(get("/api/v1/content?category=history&language=te&contentType=DOCUMENTARY")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].title", hasItem("Target Heritage Item")))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Other Telugu Sci"))))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Other English History"))));
    }

    @Test
    @Order(8)
    @DisplayName("8. Search keyword filter across title, subtitle, and tags")
    void test08_searchKeywordFilter() throws Exception {
        createPublishedContent("Ancient Kalamkari", ContentType.DOCUMENTARY, AgeRating.U, teluguLanguage, historyCategory, "kalamkari,art");
        createPublishedContent("Modern AI Trends", ContentType.DOCUMENTARY, AgeRating.U, englishLanguage, scienceCategory, "ai,tech");

        mockMvc.perform(get("/api/v1/content?search=kalamkari")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].title", hasItem("Ancient Kalamkari")))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Modern AI Trends"))));
    }

    @Test
    @Order(9)
    @DisplayName("9. Strict publication boundary: Draft, Processing, Failed, Archived, and Unpublished are never returned")
    void test09_nonPublishedContentExcludedFromPublicCatalog() throws Exception {
        Content draft = Content.builder().title("Secret Draft").contentType(ContentType.MOVIE).status(ContentStatus.DRAFT).build();
        Content processing = Content.builder().title("Encoding Item").contentType(ContentType.MOVIE).status(ContentStatus.PROCESSING).build();
        Content failed = Content.builder().title("Failed Video").contentType(ContentType.MOVIE).status(ContentStatus.FAILED).build();
        Content archived = Content.builder().title("Archived Item").contentType(ContentType.MOVIE).status(ContentStatus.ARCHIVED).build();
        Content unpublished = Content.builder().title("Unpublished Item").contentType(ContentType.MOVIE).status(ContentStatus.UNPUBLISHED).build();

        contentRepository.saveAll(List.of(draft, processing, failed, archived, unpublished));

        mockMvc.perform(get("/api/v1/content")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Secret Draft"))))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Encoding Item"))))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Failed Video"))))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Archived Item"))))
                .andExpect(jsonPath("$.data.content[*].title", not(hasItem("Unpublished Item"))));
    }

    @Test
    @Order(10)
    @DisplayName("10. Pagination: Custom page size, max page size (100) capping, and metadata structure")
    void test10_paginationAndPageMetadata() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createPublishedContent("Paged Doc " + i, ContentType.DOCUMENTARY, AgeRating.U, englishLanguage, docCategory, "paged");
        }

        mockMvc.perform(get("/api/v1/content?page=0&size=2")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size", is(2)))
                .andExpect(jsonPath("$.data.number", is(0)))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.data.content", hasSize(2)));

        // Request size 200 should be capped to 100
        mockMvc.perform(get("/api/v1/content?size=200")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size", is(100)));
    }

    @Test
    @Order(11)
    @DisplayName("11. Sorting: Safe whitelisted fields work, unsafe fields return 400 Bad Request")
    void test11_sortingWhitelistAndValidation() throws Exception {
        createPublishedContent("Alpha Title", ContentType.MOVIE, AgeRating.U, englishLanguage, docCategory, "sort");
        createPublishedContent("Zeta Title", ContentType.MOVIE, AgeRating.U, englishLanguage, docCategory, "sort");

        // Sort by title ASC
        mockMvc.perform(get("/api/v1/content?sort=title,asc")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Invalid sort field
        mockMvc.perform(get("/api/v1/content?sort=password,desc")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_SORT_FIELD")));
    }

    @Test
    @Order(12)
    @DisplayName("12. Verify RBAC enforcement for content metadata and taxonomy endpoints")
    void test12_rbacMetadataUpdate() throws Exception {
        Content content = createPublishedContent("RBAC Content", ContentType.DOCUMENTARY, AgeRating.U, teluguLanguage, historyCategory, "rbac");

        ContentMetadataUpdateRequest metadataRequest = ContentMetadataUpdateRequest.builder()
                .subtitle("Unauthorized Update Attempt")
                .build();

        // USER -> 403 Forbidden
        mockMvc.perform(put("/api/v1/admin/content/" + content.getId() + "/metadata")
                        .header("X-Dev-User-Id", regularUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(metadataRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

        // CONTENT_MANAGER -> 200 OK
        mockMvc.perform(put("/api/v1/admin/content/" + content.getId() + "/metadata")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(metadataRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtitle", is("Unauthorized Update Attempt")));
    }
}
