package com.communityott;

import com.communityott.analytics.dto.PeriodComparisonDto;
import com.communityott.analytics.entity.AnalyticsDailyMetric;
import com.communityott.analytics.repository.AnalyticsDailyMetricRepository;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class ManagerAdminAnalyticsTest {

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
    private AnalyticsQueryService queryService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private User managerUser;
    private User adminUser;
    private User regularUser;

    private String managerToken;
    private String adminToken;
    private String userToken;

    private Content contentA;
    private Content contentB;
    private Category categoryFolk;
    private Category categoryDrama;
    private Language languageTelugu;
    private Language languageHindi;

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
                .email("user_" + suffix + "@communityott.com")
                .displayName("Regular User")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(regularUser, userRole));

        managerToken = "Bearer " + jwtTokenService.generateAccessToken(managerUser);
        adminToken = "Bearer " + jwtTokenService.generateAccessToken(adminUser);
        userToken = "Bearer " + jwtTokenService.generateAccessToken(regularUser);

        categoryFolk = categoryRepository.save(Category.builder()
                .name("Folk Art " + suffix)
                .slug("folk-art-" + suffix)
                .description("Folk heritage")
                .active(true)
                .build());

        categoryDrama = categoryRepository.save(Category.builder()
                .name("Drama " + suffix)
                .slug("drama-" + suffix)
                .description("Drama films")
                .active(true)
                .build());

        languageTelugu = languageRepository.save(Language.builder()
                .name("Telugu " + suffix)
                .code("te_" + suffix)
                .active(true)
                .build());

        languageHindi = languageRepository.save(Language.builder()
                .name("Hindi " + suffix)
                .code("hi_" + suffix)
                .active(true)
                .build());

        contentA = contentRepository.save(Content.builder()
                .title("Folk Documentary " + suffix)
                .description("Telugu folk arts")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(7200)
                .thumbnailUrl("https://media.communityott.com/thumbnails/folk.jpg")
                .originalLanguage(languageTelugu)
                .isFeatured(true)
                .build());

        contentB = contentRepository.save(Content.builder()
                .title("Drama Feature " + suffix)
                .description("Hindi cultural drama")
                .contentType(ContentType.MOVIE)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(3600)
                .thumbnailUrl("https://media.communityott.com/thumbnails/drama.jpg")
                .originalLanguage(languageHindi)
                .isFeatured(false)
                .build());

        contentCategoryRepository.save(new ContentCategory(contentA, categoryFolk));
        contentCategoryRepository.save(new ContentCategory(contentB, categoryDrama));

        contentLanguageRepository.save(new ContentLanguage(contentA, languageTelugu));
        contentLanguageRepository.save(new ContentLanguage(contentB, languageHindi));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        LocalDate lastWeek = today.minusDays(7);

        // Previous window metric (day -7)
        dailyMetricRepository.save(AnalyticsDailyMetric.builder()
                .metricDate(lastWeek)
                .content(contentA)
                .platform(Platform.IOS)
                .totalSessions(50)
                .totalPlays(40)
                .uniqueViewers(30)
                .totalWatchTimeSeconds(10000)
                .completionCount(20)
                .bufferEventCount(2)
                .errorCount(0)
                .qualityChangeCount(1)
                .build());

        // Current window metrics (yesterday & today)
        dailyMetricRepository.save(AnalyticsDailyMetric.builder()
                .metricDate(yesterday)
                .content(contentA)
                .platform(Platform.IOS)
                .totalSessions(100)
                .totalPlays(80)
                .uniqueViewers(60)
                .totalWatchTimeSeconds(25000)
                .completionCount(50)
                .bufferEventCount(5)
                .errorCount(1)
                .qualityChangeCount(3)
                .build());

        dailyMetricRepository.save(AnalyticsDailyMetric.builder()
                .metricDate(today)
                .content(contentB)
                .platform(Platform.ANDROID)
                .totalSessions(60)
                .totalPlays(50)
                .uniqueViewers(40)
                .totalWatchTimeSeconds(15000)
                .completionCount(30)
                .bufferEventCount(3)
                .errorCount(0)
                .qualityChangeCount(2)
                .build());
    }

    // ==========================================
    // 1. Period Comparison DTO Math Unit Tests
    // ==========================================

    @Test
    @DisplayName("PeriodComparisonDto correctly calculates positive growth percentage and UP trend")
    void testPeriodComparisonMath_PositiveGrowth() {
        PeriodComparisonDto dto = PeriodComparisonDto.of(150, 100);
        assertThat(dto.getCurrent()).isEqualTo(150);
        assertThat(dto.getPrevious()).isEqualTo(100);
        assertThat(dto.getGrowthPercentage()).isEqualTo(50.0);
        assertThat(dto.getTrend()).isEqualTo("UP");
    }

    @Test
    @DisplayName("PeriodComparisonDto correctly calculates negative growth percentage and DOWN trend")
    void testPeriodComparisonMath_NegativeGrowth() {
        PeriodComparisonDto dto = PeriodComparisonDto.of(80, 100);
        assertThat(dto.getCurrent()).isEqualTo(80);
        assertThat(dto.getPrevious()).isEqualTo(100);
        assertThat(dto.getGrowthPercentage()).isEqualTo(-20.0);
        assertThat(dto.getTrend()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("PeriodComparisonDto handles division by zero safely when previous is 0 and current > 0")
    void testPeriodComparisonMath_ZeroPrevious() {
        PeriodComparisonDto dto = PeriodComparisonDto.of(50, 0);
        assertThat(dto.getCurrent()).isEqualTo(50);
        assertThat(dto.getPrevious()).isEqualTo(0);
        assertThat(dto.getGrowthPercentage()).isEqualTo(100.0);
        assertThat(dto.getTrend()).isEqualTo("UP");
    }

    @Test
    @DisplayName("PeriodComparisonDto handles zero current and zero previous with 0.0 growth and FLAT trend")
    void testPeriodComparisonMath_BothZero() {
        PeriodComparisonDto dto = PeriodComparisonDto.of(0, 0);
        assertThat(dto.getCurrent()).isEqualTo(0);
        assertThat(dto.getPrevious()).isEqualTo(0);
        assertThat(dto.getGrowthPercentage()).isEqualTo(0.0);
        assertThat(dto.getTrend()).isEqualTo("FLAT");
    }

    // ==========================================
    // 2. Manager Analytics Endpoints
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/manager/analytics/overview returns 200 with period comparison for MANAGER")
    void testManagerOverview_Success() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(1);

        mockMvc.perform(get("/api/v1/manager/analytics/overview")
                        .header("Authorization", managerToken)
                        .param("startDate", start.toString())
                        .param("endDate", today.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.views.current", is(160))) // 100 (yesterday) + 60 (today)
                .andExpect(jsonPath("$.data.plays.current", is(130))) // 80 + 50
                .andExpect(jsonPath("$.data.watchTimeSeconds.current", is(40000))) // 25000 + 15000
                .andExpect(jsonPath("$.data.completedPlays.current", is(80))) // 50 + 30
                .andExpect(jsonPath("$.data.topContent", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/v1/manager/analytics/categories returns 200 with category breakdown")
    void testManagerCategories_Success() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(6);

        mockMvc.perform(get("/api/v1/manager/analytics/categories")
                        .header("Authorization", managerToken)
                        .param("startDate", start.toString())
                        .param("endDate", today.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.categories", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.categories[0].categoryName").exists())
                .andExpect(jsonPath("$.data.categories[0].totalWatchTimeSeconds", greaterThanOrEqualTo(10000)));
    }

    @Test
    @DisplayName("GET /api/v1/manager/analytics/languages returns 200 with language breakdown")
    void testManagerLanguages_Success() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(6);

        mockMvc.perform(get("/api/v1/manager/analytics/languages")
                        .header("Authorization", managerToken)
                        .param("startDate", start.toString())
                        .param("endDate", today.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.languages", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.languages[0].languageName").exists())
                .andExpect(jsonPath("$.data.languages[0].totalWatchTimeSeconds", greaterThanOrEqualTo(10000)));
    }

    @Test
    @DisplayName("GET /api/v1/manager/analytics/content returns 200 with paginated rankings")
    void testManagerContentPerformance_Success() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(6);

        mockMvc.perform(get("/api/v1/manager/analytics/content")
                        .header("Authorization", managerToken)
                        .param("from", start.toString())
                        .param("to", today.toString())
                        .param("sortBy", "WATCH_TIME")
                        .param("sortDirection", "DESC")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/v1/manager/analytics/content/{contentId} returns 200 for content detail")
    void testManagerContentDetail_Success() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(6);

        mockMvc.perform(get("/api/v1/manager/analytics/content/" + contentA.getId())
                        .header("Authorization", managerToken)
                        .param("from", start.toString())
                        .param("to", today.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.contentId", is(contentA.getId().intValue())))
                .andExpect(jsonPath("$.data.totalPlays", greaterThanOrEqualTo(80)));
    }

    @Test
    @DisplayName("GET /api/v1/manager/analytics/trends returns 200 daily trend points")
    void testManagerTrends_Success() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(6);

        mockMvc.perform(get("/api/v1/manager/analytics/trends")
                        .header("Authorization", managerToken)
                        .param("timeWindow", "LAST_7_DAYS")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.points", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/v1/manager/analytics/platforms returns 200 platform distribution")
    void testManagerPlatforms_Success() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(6);

        mockMvc.perform(get("/api/v1/manager/analytics/platforms")
                        .header("Authorization", managerToken)
                        .param("startDate", start.toString())
                        .param("endDate", today.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.platforms", hasSize(3))); // IOS, ANDROID, WEB
    }

    // ==========================================
    // 3. Admin Analytics Endpoints
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/admin/analytics/overview returns 200 for SUPER_ADMIN")
    void testAdminOverview_Success() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(6);

        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .header("Authorization", adminToken)
                        .param("startDate", start.toString())
                        .param("endDate", today.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalPlays", greaterThanOrEqualTo(130)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/analytics/system returns 200 with system inventory for SUPER_ADMIN")
    void testAdminSystem_Success() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/system")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalRegisteredUsers", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.totalPublishedContent", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.platformSummary", hasSize(3)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/analytics/users returns 200 with aggregate viewer metrics for SUPER_ADMIN")
    void testAdminUsers_Success() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(6);

        mockMvc.perform(get("/api/v1/admin/analytics/users")
                        .header("Authorization", adminToken)
                        .param("startDate", start.toString())
                        .param("endDate", today.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalRegisteredUsers", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.activeViewersInPeriod", greaterThanOrEqualTo(100)));
    }

    // ==========================================
    // 4. RBAC & Security Boundary Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/admin/analytics/system returns 403 Forbidden for MANAGER (Admin-only boundary)")
    void testAdminSystem_ForbiddenForManager() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/system")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/analytics/users returns 403 Forbidden for MANAGER (Admin-only boundary)")
    void testAdminUsers_ForbiddenForManager() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/users")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/manager/analytics/overview returns 403 Forbidden for standard USER")
    void testManagerOverview_ForbiddenForUser() throws Exception {
        mockMvc.perform(get("/api/v1/manager/analytics/overview")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/analytics/overview returns 403 Forbidden for standard USER")
    void testAdminOverview_ForbiddenForUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/manager/analytics/overview returns 401 Unauthorized when unauthenticated")
    void testManagerOverview_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/manager/analytics/overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // 5. Validation & Error Handling Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/manager/analytics/overview returns 400 for invalid platform")
    void testManagerOverview_InvalidPlatform() throws Exception {
        mockMvc.perform(get("/api/v1/manager/analytics/overview")
                        .header("Authorization", managerToken)
                        .param("platform", "INVALID_DEVICE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("ANALYTICS_INVALID_PLATFORM")));
    }

    @Test
    @DisplayName("GET /api/v1/manager/analytics/content returns 400 for invalid sort field")
    void testManagerContent_InvalidSort() throws Exception {
        mockMvc.perform(get("/api/v1/manager/analytics/content")
                        .header("Authorization", managerToken)
                        .param("sortBy", "UNSUPPORTED_COLUMN")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("ANALYTICS_INVALID_SORT")));
    }

    @Test
    @DisplayName("GET /api/v1/manager/analytics/content returns 400 for invalid pagination size")
    void testManagerContent_InvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/manager/analytics/content")
                        .header("Authorization", managerToken)
                        .param("size", "500")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("ANALYTICS_INVALID_PAGINATION")));
    }
}
