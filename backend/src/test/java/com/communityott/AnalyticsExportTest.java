package com.communityott;

import com.communityott.analytics.dto.AnalyticsExportResponse;
import com.communityott.analytics.entity.AnalyticsDailyMetric;
import com.communityott.analytics.repository.AnalyticsDailyMetricRepository;
import com.communityott.analytics.service.AnalyticsExportService;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.security.JwtTokenService;
import com.communityott.content.entity.Category;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentCategory;
import com.communityott.content.entity.ContentLanguage;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.content.entity.Language;
import com.communityott.content.repository.CategoryRepository;
import com.communityott.content.repository.ContentCategoryRepository;
import com.communityott.content.repository.ContentLanguageRepository;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.LanguageRepository;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AnalyticsExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

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
    private AnalyticsDailyMetricRepository dailyMetricRepository;

    @Autowired
    private AnalyticsExportService exportService;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User managerUser;
    private User adminUser;
    private User regularUser;

    private String managerToken;
    private String adminToken;
    private String userToken;

    private Content contentFolk;
    private Content contentDrama;
    private Content contentOrphan;
    private Category categoryFolk;
    private Category categoryDrama;
    private Language languageTelugu;
    private Language languageHindi;

    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        Role managerRole = roleRepository.findByName("MANAGER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("MANAGER").description("Manager").isSystemRole(true).build()));
        Role adminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("SUPER_ADMIN").description("Super Admin").isSystemRole(true).build()));
        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("USER").description("User").isSystemRole(true).build()));

        long suffix = Math.abs(System.nanoTime() % 1000000000L);

        managerUser = userRepository.save(User.builder()
                .email("export_mgr_" + suffix + "@communityott.com")
                .displayName("Export Manager")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(managerUser, managerRole));

        adminUser = userRepository.save(User.builder()
                .email("export_adm_" + suffix + "@communityott.com")
                .displayName("Export Admin")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(adminUser, adminRole));

        regularUser = userRepository.save(User.builder()
                .email("export_usr_" + suffix + "@communityott.com")
                .displayName("Export Regular User")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(regularUser, userRole));

        managerToken = "Bearer " + jwtTokenService.generateAccessToken(managerUser);
        adminToken = "Bearer " + jwtTokenService.generateAccessToken(adminUser);
        userToken = "Bearer " + jwtTokenService.generateAccessToken(regularUser);

        categoryFolk = categoryRepository.save(Category.builder()
                .name("Export Folk " + suffix)
                .slug("export-folk-" + suffix)
                .description("Folk category")
                .active(true)
                .build());

        categoryDrama = categoryRepository.save(Category.builder()
                .name("Export Drama " + suffix)
                .slug("export-drama-" + suffix)
                .description("Drama category")
                .active(true)
                .build());

        languageTelugu = languageRepository.save(Language.builder()
                .name("Export Telugu " + suffix)
                .code("ex_te_" + suffix)
                .active(true)
                .build());

        languageHindi = languageRepository.save(Language.builder()
                .name("Export Hindi " + suffix)
                .code("ex_hi_" + suffix)
                .active(true)
                .build());

        contentFolk = contentRepository.save(Content.builder()
                .title("Folk Documentary " + suffix)
                .description("Telugu folk arts")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(7200)
                .thumbnailUrl("https://media.communityott.com/thumbnails/folk.jpg")
                .originalLanguage(languageTelugu)
                .isFeatured(true)
                .build());

        contentDrama = contentRepository.save(Content.builder()
                .title("Drama Film " + suffix)
                .description("Hindi drama")
                .contentType(ContentType.MOVIE)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(5400)
                .thumbnailUrl("https://media.communityott.com/thumbnails/drama.jpg")
                .originalLanguage(languageHindi)
                .isFeatured(false)
                .build());

        contentOrphan = contentRepository.save(Content.builder()
                .title("Orphan Content " + suffix)
                .description("No category or language assigned")
                .contentType(ContentType.MOVIE)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(1200)
                .thumbnailUrl("https://media.communityott.com/thumbnails/orphan.jpg")
                .isFeatured(false)
                .build());


        contentCategoryRepository.save(new ContentCategory(contentFolk, categoryFolk));
        contentCategoryRepository.save(new ContentCategory(contentDrama, categoryDrama));

        contentLanguageRepository.save(new ContentLanguage(contentFolk, languageTelugu));
        contentLanguageRepository.save(new ContentLanguage(contentDrama, languageHindi));

        testDate = LocalDate.now(ZoneOffset.UTC).minusDays(2);

        // ContentFolk on iOS
        dailyMetricRepository.save(AnalyticsDailyMetric.builder()
                .metricDate(testDate)
                .content(contentFolk)
                .platform(Platform.IOS)
                .totalSessions(120)
                .totalPlays(150)
                .uniqueViewers(95)
                .totalWatchTimeSeconds(42000)
                .completionCount(70)
                .bufferEventCount(12)
                .errorCount(2)
                .qualityChangeCount(8)
                .build());

        // ContentDrama on ANDROID
        dailyMetricRepository.save(AnalyticsDailyMetric.builder()
                .metricDate(testDate)
                .content(contentDrama)
                .platform(Platform.ANDROID)
                .totalSessions(80)
                .totalPlays(100)
                .uniqueViewers(60)
                .totalWatchTimeSeconds(30000)
                .completionCount(50)
                .bufferEventCount(5)
                .errorCount(0)
                .qualityChangeCount(3)
                .build());

        // ContentOrphan on WEB (0 plays edge case)
        dailyMetricRepository.save(AnalyticsDailyMetric.builder()
                .metricDate(testDate)
                .content(contentOrphan)
                .platform(Platform.WEB)
                .totalSessions(10)
                .totalPlays(0)
                .uniqueViewers(8)
                .totalWatchTimeSeconds(0)
                .completionCount(0)
                .bufferEventCount(0)
                .errorCount(0)
                .qualityChangeCount(0)
                .build());
    }

    // ==========================================
    // 1. Contract Envelope & Version Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/analytics/export returns 200 with contract version 'analytics-contract-v1'")
    void testExport_ContractVersionAndEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("from", testDate.toString())
                        .param("to", testDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.contract_version", is("analytics-contract-v1")))
                .andExpect(jsonPath("$.data.generated_at").exists())
                .andExpect(jsonPath("$.data.from", is(testDate.toString())))
                .andExpect(jsonPath("$.data.to", is(testDate.toString())))
                .andExpect(jsonPath("$.data.page", is(0)))
                .andExpect(jsonPath("$.data.size", is(100)))
                .andExpect(jsonPath("$.data.total_records", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.records", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/export returns records with snake_case field serialization and exact types")
    void testExport_SnakeCaseFieldSerialization() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("from", testDate.toString())
                        .param("to", testDate.toString())
                        .param("content_id", contentFolk.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].date", is(testDate.toString())))
                .andExpect(jsonPath("$.data.records[0].content_id", is(contentFolk.getId().intValue())))
                .andExpect(jsonPath("$.data.records[0].category_id", is(categoryFolk.getId().intValue())))
                .andExpect(jsonPath("$.data.records[0].language_id", is(languageTelugu.getId().intValue())))
                .andExpect(jsonPath("$.data.records[0].platform", is("IOS")))
                .andExpect(jsonPath("$.data.records[0].sessions", is(120)))
                .andExpect(jsonPath("$.data.records[0].plays", is(150)))
                .andExpect(jsonPath("$.data.records[0].unique_viewers", is(95)))
                .andExpect(jsonPath("$.data.records[0].watch_time_seconds", is(42000)))
                .andExpect(jsonPath("$.data.records[0].completed_plays", is(70)))
                .andExpect(jsonPath("$.data.records[0].completion_rate", is(0.4667)))
                .andExpect(jsonPath("$.data.records[0].buffering_events", is(12)))
                .andExpect(jsonPath("$.data.records[0].playback_errors", is(2)))
                .andExpect(jsonPath("$.data.records[0].quality_changes", is(8)));
    }

    // ==========================================
    // 2. Filtering Tests (Platform, Content, Category, Language)
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/analytics/export filters by platform accurately")
    void testExport_PlatformFilter() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("from", testDate.toString())
                        .param("to", testDate.toString())
                        .param("platform", "ANDROID")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].platform", is("ANDROID")))
                .andExpect(jsonPath("$.data.records[0].content_id", is(contentDrama.getId().intValue())));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/export filters by category_id accurately")
    void testExport_CategoryFilter() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("from", testDate.toString())
                        .param("to", testDate.toString())
                        .param("category_id", categoryFolk.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].category_id", is(categoryFolk.getId().intValue())))
                .andExpect(jsonPath("$.data.records[0].content_id", is(contentFolk.getId().intValue())));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/export filters by language_id accurately")
    void testExport_LanguageFilter() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("from", testDate.toString())
                        .param("to", testDate.toString())
                        .param("language_id", languageHindi.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].language_id", is(languageHindi.getId().intValue())))
                .andExpect(jsonPath("$.data.records[0].content_id", is(contentDrama.getId().intValue())));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/export handles nullable category and language fields without errors")
    void testExport_NullableCategoryAndLanguage() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("from", testDate.toString())
                        .param("to", testDate.toString())
                        .param("content_id", contentOrphan.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].category_id", nullValue()))
                .andExpect(jsonPath("$.data.records[0].language_id", nullValue()))
                .andExpect(jsonPath("$.data.records[0].plays", is(0)))
                .andExpect(jsonPath("$.data.records[0].completion_rate", is(0.0)));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/export returns empty list when no metrics match date range")
    void testExport_EmptyResults() throws Exception {
        LocalDate past = LocalDate.now(ZoneOffset.UTC).minusDays(80);
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("from", past.toString())
                        .param("to", past.plusDays(1).toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(0)))
                .andExpect(jsonPath("$.data.total_records", is(0)));
    }

    // ==========================================
    // 3. Pagination & Limit Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/analytics/export paginates records correctly with has_next indicator")
    void testExport_Pagination() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("from", testDate.toString())
                        .param("to", testDate.toString())
                        .param("page", "0")
                        .param("size", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page", is(0)))
                .andExpect(jsonPath("$.data.size", is(1)))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.has_next", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/export rejects size > 100 with HTTP 400 Bad Request")
    void testExport_ExceededPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("size", "101")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("ANALYTICS_INVALID_PAGINATION")));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/export rejects negative page with HTTP 400 Bad Request")
    void testExport_NegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("page", "-1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("ANALYTICS_INVALID_PAGINATION")));
    }

    // ==========================================
    // 4. Validation & Error Handling Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/analytics/export rejects invalid platform with HTTP 400 Bad Request")
    void testExport_InvalidPlatform() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("platform", "SMART_TV")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("ANALYTICS_INVALID_PLATFORM")));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/export rejects date range when from > to with HTTP 400")
    void testExport_FromAfterTo() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("from", today.toString())
                        .param("to", today.minusDays(5).toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_DATE_RANGE")));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/export rejects date range > 90 days with HTTP 400")
    void testExport_DateRangeExceeds90Days() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("from", today.minusDays(95).toString())
                        .param("to", today.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_DATE_RANGE")));
    }

    // ==========================================
    // 5. Privacy & Zero-PII Guarantees
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/analytics/export response contains zero PII fields")
    void testExport_ZeroPiiGuaranteed() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", managerToken)
                        .param("from", testDate.toString())
                        .param("to", testDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("email");
        assertThat(json).doesNotContain("phone");
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("user_id");
        assertThat(json).doesNotContain("ip_address");
        assertThat(json).doesNotContain("session_token");
        assertThat(json).doesNotContain("user_agent");
        assertThat(json).doesNotContain("device_id");
    }

    // ==========================================
    // 6. Security & RBAC Authorization Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/analytics/export allows SUPER_ADMIN with ANALYTICS_VIEW")
    void testExport_AllowedForSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", adminToken)
                        .param("from", testDate.toString())
                        .param("to", testDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/export returns 403 Forbidden for standard USER")
    void testExport_ForbiddenForUser() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/analytics/export returns 401 Unauthorized for unauthenticated requests")
    void testExport_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/export")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
