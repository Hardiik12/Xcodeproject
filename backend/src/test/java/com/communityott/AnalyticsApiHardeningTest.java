package com.communityott;

import com.communityott.analytics.dto.AnalyticsOverviewResponse;
import com.communityott.analytics.dto.AnalyticsTrendResponse;
import com.communityott.analytics.dto.ContentAnalyticsResponse;
import com.communityott.analytics.dto.ContentRankingItemDto;
import com.communityott.analytics.dto.PlatformAnalyticsResponse;
import com.communityott.analytics.entity.AnalyticsDailyMetric;
import com.communityott.analytics.repository.AnalyticsCheckpointRepository;
import com.communityott.analytics.repository.AnalyticsDailyMetricRepository;
import com.communityott.analytics.service.AnalyticsAggregationService;
import com.communityott.analytics.service.AnalyticsQueryService;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.security.JwtTokenService;
import com.communityott.content.entity.Category;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentCategory;
import com.communityott.content.entity.ContentLanguage;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.content.entity.Language;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.repository.CategoryRepository;
import com.communityott.content.repository.ContentCategoryRepository;
import com.communityott.content.repository.ContentLanguageRepository;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.LanguageRepository;
import com.communityott.content.repository.VideoAssetRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AnalyticsApiHardeningTest {

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
    private VideoAssetRepository videoAssetRepository;

    @Autowired
    private AnalyticsDailyMetricRepository dailyMetricRepository;

    @Autowired
    private AnalyticsCheckpointRepository checkpointRepository;

    @Autowired
    private AnalyticsAggregationService aggregationService;

    @Autowired
    private AnalyticsQueryService queryService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private User managerUser;
    private User adminUser;
    private User regularUser;

    private String managerToken;
    private String adminToken;
    private String userToken;

    private Content testContent1;
    private Content testContent2;
    private Category testCategory;
    private Language testLanguage;

    @BeforeEach
    void setUp() {
        try {
            Set<String> keys = redisTemplate.keys("communityott:analytics:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {}

        Role managerRole = roleRepository.findByName("MANAGER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("MANAGER").description("Manager").isSystemRole(true).build()));
        Role adminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("SUPER_ADMIN").description("Super Admin").isSystemRole(true).build()));
        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("USER").description("User").isSystemRole(true).build()));

        long suffix = Math.abs(System.nanoTime() % 1000000000L);

        managerUser = userRepository.save(User.builder()
                .email("manager_" + suffix + "@communityott.com")
                .displayName("Manager User")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(managerUser, managerRole));

        adminUser = userRepository.save(User.builder()
                .email("admin_" + suffix + "@communityott.com")
                .displayName("Admin User")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(adminUser, adminRole));

        regularUser = userRepository.save(User.builder()
                .email("viewer_" + suffix + "@communityott.com")
                .displayName("Viewer User")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(regularUser, userRole));

        managerToken = "Bearer " + jwtTokenService.generateAccessToken(managerUser);
        adminToken = "Bearer " + jwtTokenService.generateAccessToken(adminUser);
        userToken = "Bearer " + jwtTokenService.generateAccessToken(regularUser);

        testCategory = categoryRepository.save(Category.builder()
                .name("Culture & Heritage " + suffix)
                .slug("culture-heritage-" + suffix)
                .active(true)
                .build());

        testLanguage = languageRepository.save(Language.builder()
                .name("Telugu " + suffix)
                .code("te_" + suffix)
                .active(true)
                .build());

        testContent1 = contentRepository.save(Content.builder()
                .title("Telugu Cultural Showcase 1")
                .description("Documentary on traditional arts")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(7200)
                .thumbnailUrl("https://media.communityott.com/thumbnails/doc1.jpg")
                .originalLanguage(testLanguage)
                .build());

        testContent2 = contentRepository.save(Content.builder()
                .title("Telugu Heritage Series 2")
                .description("History of regional festivals")
                .contentType(ContentType.SERIES)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(3600)
                .thumbnailUrl("https://media.communityott.com/thumbnails/doc2.jpg")
                .originalLanguage(testLanguage)
                .build());

        contentCategoryRepository.save(new ContentCategory(testContent1, testCategory));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);

        dailyMetricRepository.save(AnalyticsDailyMetric.builder()
                .metricDate(today)
                .content(testContent1)
                .platform(Platform.IOS)
                .totalSessions(100)
                .totalPlays(90)
                .uniqueViewers(80)
                .totalWatchTimeSeconds(54000)
                .completionCount(70)
                .pauseCount(15)
                .seekCount(10)
                .bufferEventCount(2)
                .errorCount(1)
                .qualityChangeCount(5)
                .build());

        dailyMetricRepository.save(AnalyticsDailyMetric.builder()
                .metricDate(today)
                .content(testContent1)
                .platform(Platform.ANDROID)
                .totalSessions(50)
                .totalPlays(45)
                .uniqueViewers(40)
                .totalWatchTimeSeconds(27000)
                .completionCount(35)
                .pauseCount(5)
                .seekCount(3)
                .bufferEventCount(1)
                .errorCount(0)
                .qualityChangeCount(2)
                .build());

        dailyMetricRepository.save(AnalyticsDailyMetric.builder()
                .metricDate(yesterday)
                .content(testContent2)
                .platform(Platform.WEB)
                .totalSessions(80)
                .totalPlays(75)
                .uniqueViewers(60)
                .totalWatchTimeSeconds(40000)
                .completionCount(50)
                .pauseCount(10)
                .seekCount(4)
                .bufferEventCount(3)
                .errorCount(2)
                .qualityChangeCount(4)
                .build());
    }

    @Test
    @DisplayName("1. Overview API: Success with default date range")
    void testOverviewSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalViews").value(230))
                .andExpect(jsonPath("$.data.totalPlays").value(210))
                .andExpect(jsonPath("$.data.uniqueViewers").value(180))
                .andExpect(jsonPath("$.data.totalWatchTimeSeconds").value(121000))
                .andExpect(jsonPath("$.data.completedPlays").value(155))
                .andExpect(jsonPath("$.data.completionRate").value(0.67))
                .andExpect(jsonPath("$.data.playbackErrors").value(3))
                .andExpect(jsonPath("$.data.bufferEvents").value(6));
    }

    @Test
    @DisplayName("2. Overview API: Supports 'from' and 'to' parameter aliases")
    void testOverviewWithFromAndToAliases() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalViews").value(150))
                .andExpect(jsonPath("$.data.totalPlays").value(135));
    }

    @Test
    @DisplayName("3. Overview API: Supports 'timeWindow' parameter (TODAY, YESTERDAY, LAST_7_DAYS)")
    void testOverviewTimeWindows() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .param("timeWindow", "TODAY")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalViews").value(150));

        mockMvc.perform(get("/api/v1/analytics/overview")
                        .param("timeWindow", "YESTERDAY")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalViews").value(80));
    }

    @Test
    @DisplayName("4. Overview API: Platform filter limits results to target platform")
    void testOverviewPlatformFilter() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .param("platform", "IOS")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalViews").value(100))
                .andExpect(jsonPath("$.data.totalPlays").value(90))
                .andExpect(jsonPath("$.data.totalWatchTimeSeconds").value(54000));
    }

    @Test
    @DisplayName("5. Overview API: Invalid platform string returns 400 Bad Request")
    void testOverviewInvalidPlatform() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .param("platform", "PLAYSTATION")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ANALYTICS_INVALID_PLATFORM"));
    }

    @Test
    @DisplayName("6. Content Analytics: Success with valid content ID")
    void testContentAnalyticsSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/{contentId}", testContent1.getId())
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contentId").value(testContent1.getId()))
                .andExpect(jsonPath("$.data.title").value("Telugu Cultural Showcase 1"))
                .andExpect(jsonPath("$.data.totalViews").value(150))
                .andExpect(jsonPath("$.data.totalPlays").value(135))
                .andExpect(jsonPath("$.data.totalWatchTimeSeconds").value(81000));
    }

    @Test
    @DisplayName("7. Content Analytics: Non-existent content returns 404 Not Found")
    void testContentAnalyticsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/999999")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONTENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("8. Daily Trends API: Returns active daily time-series points")
    void testDailyTrendsContinuous() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(3);

        mockMvc.perform(get("/api/v1/analytics/trends")
                        .param("startDate", start.toString())
                        .param("endDate", today.toString())
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points", hasSize(2)))
                .andExpect(jsonPath("$.data.points[0].views").value(80))  // yesterday
                .andExpect(jsonPath("$.data.points[1].views").value(150)); // today
    }

    @Test
    @DisplayName("9. Platform Analytics API: Returns all platforms (IOS, ANDROID, WEB)")
    void testPlatformAnalyticsComplete() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/platforms")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platforms", hasSize(3)))
                .andExpect(jsonPath("$.data.platforms[?(@.platform == 'IOS')].sessions").value(100))
                .andExpect(jsonPath("$.data.platforms[?(@.platform == 'ANDROID')].sessions").value(50))
                .andExpect(jsonPath("$.data.platforms[?(@.platform == 'WEB')].sessions").value(80));
    }

    @Test
    @DisplayName("10. Top Content API: Default sorting by WATCH_TIME DESC")
    void testTopContentDefaultSorting() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].contentId").value(testContent1.getId()))
                .andExpect(jsonPath("$.data.content[0].totalWatchTimeSeconds").value(81000))
                .andExpect(jsonPath("$.data.content[1].contentId").value(testContent2.getId()))
                .andExpect(jsonPath("$.data.content[1].totalWatchTimeSeconds").value(40000));
    }

    @Test
    @DisplayName("11. Top Content API: Sorting by VIEWS ASC and DESC")
    void testTopContentSortingByViews() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("sortBy", "VIEWS")
                        .param("sortDirection", "ASC")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].contentId").value(testContent2.getId()))
                .andExpect(jsonPath("$.data.content[0].totalPlays").value(75));

        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("sortBy", "VIEWS")
                        .param("sortDirection", "DESC")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].contentId").value(testContent1.getId()))
                .andExpect(jsonPath("$.data.content[0].totalPlays").value(135));
    }

    @Test
    @DisplayName("12. Top Content API: Sorting by UNIQUE_VIEWERS")
    void testTopContentSortingByUniqueViewers() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("sortBy", "UNIQUE_VIEWERS")
                        .param("sortDirection", "DESC")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].contentId").value(testContent1.getId()));
    }

    @Test
    @DisplayName("13. Top Content API: Sorting by COMPLETIONS")
    void testTopContentSortingByCompletions() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("sortBy", "COMPLETIONS")
                        .param("sortDirection", "DESC")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].contentId").value(testContent1.getId()))
                .andExpect(jsonPath("$.data.content[0].completionCount").value(105));
    }

    @Test
    @DisplayName("14. Top Content API: Category filter limits rankings to specified category")
    void testTopContentCategoryFilter() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("categoryId", testCategory.getId().toString())
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].contentId").value(testContent1.getId()));
    }

    @Test
    @DisplayName("15. Top Content API: Language filter limits rankings to specified language")
    void testTopContentLanguageFilter() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("languageId", testLanguage.getId().toString())
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    @DisplayName("16. Top Content API: Bounded pagination validation (negative page returns 400)")
    void testTopContentInvalidPage() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("page", "-1")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ANALYTICS_INVALID_PAGINATION"));
    }

    @Test
    @DisplayName("17. Top Content API: Out-of-bounds size returns 400 (size = 0 or size = 150)")
    void testTopContentInvalidSize() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("size", "0")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ANALYTICS_INVALID_PAGINATION"));

        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("size", "150")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ANALYTICS_INVALID_PAGINATION"));
    }

    @Test
    @DisplayName("18. Top Content API: Invalid sort field returns 400 Bad Request")
    void testTopContentInvalidSortField() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("sortBy", "INVALID_FIELD")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ANALYTICS_INVALID_SORT"));
    }

    @Test
    @DisplayName("19. Top Content API: Invalid sort direction returns 400 Bad Request")
    void testTopContentInvalidSortDirection() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("sortDirection", "SIDEWAYS")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ANALYTICS_INVALID_SORT"));
    }

    @Test
    @DisplayName("20. Date Range Validation: Start date after end date returns 400")
    void testStartDateAfterEndDate() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .param("startDate", today.toString())
                        .param("endDate", today.minusDays(5).toString())
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("21. Date Range Validation: Range exceeding 90 days returns 400")
    void testDateRangeExceeding90Days() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .param("startDate", today.minusDays(95).toString())
                        .param("endDate", today.toString())
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("22. Cache Isolation: Different platform filters do not share cached overview")
    void testCacheKeyIsolation() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .param("platform", "IOS")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalViews").value(100));

        mockMvc.perform(get("/api/v1/analytics/overview")
                        .param("platform", "ANDROID")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalViews").value(50));
    }

    @Test
    @DisplayName("23. RBAC: SUPER_ADMIN is allowed full access")
    void testSuperAdminAccessAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("24. RBAC: Regular USER receives 403 Forbidden")
    void testRegularUserForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("25. RBAC: Unauthenticated request receives 401 Unauthorized")
    void testUnauthenticatedUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("26. Privacy Guarantee: No PII exposed in API responses")
    void testPrivacyGuaranteeNoPii() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        assertThat(responseJson)
                .doesNotContain("email")
                .doesNotContain("phone")
                .doesNotContain("ipAddress")
                .doesNotContain("password")
                .doesNotContain("refreshToken");
    }

    @Test
    @DisplayName("27. Manual Aggregation Endpoint: Triggerable by MANAGER")
    void testTriggerAggregationJob() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/aggregate")
                        .param("batchSize", "100")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }
}

