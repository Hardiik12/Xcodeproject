package com.communityott;

import com.communityott.analytics.dto.AggregationJobResponse;
import com.communityott.analytics.dto.AnalyticsOverviewResponse;
import com.communityott.analytics.entity.AnalyticsDailyMetric;
import com.communityott.analytics.repository.AnalyticsCheckpointRepository;
import com.communityott.analytics.repository.AnalyticsDailyMetricRepository;
import com.communityott.analytics.service.AnalyticsAggregationService;
import com.communityott.analytics.service.AnalyticsQueryService;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.security.JwtTokenService;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.content.entity.HlsPackageStatus;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.entity.VideoHlsPackage;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.repository.VideoHlsPackageRepository;
import com.communityott.playback.dto.PlaybackEventRequest;
import com.communityott.playback.dto.PlaybackSessionResponse;
import com.communityott.playback.dto.StartPlaybackSessionRequest;
import com.communityott.playback.entity.PlaybackEventType;
import com.communityott.playback.repository.PlaybackEventRepository;
import com.communityott.playback.repository.PlaybackSessionRepository;
import com.communityott.playback.service.PlaybackEventService;
import com.communityott.playback.service.PlaybackSessionService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
public class AnalyticsAggregationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private VideoAssetRepository videoAssetRepository;

    @Autowired
    private VideoHlsPackageRepository hlsPackageRepository;

    @Autowired
    private PlaybackSessionRepository sessionRepository;

    @Autowired
    private PlaybackSessionService sessionService;

    @Autowired
    private PlaybackEventRepository playbackEventRepository;

    @Autowired
    private PlaybackEventService playbackEventService;

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

    private User managerUser;
    private User adminUser;
    private User regularUser;

    private String managerToken;
    private String adminToken;
    private String userToken;

    private Content testContent1;
    private Content testContent2;
    private VideoAsset testVideo1;
    private VideoAsset testVideo2;

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

        testContent1 = contentRepository.save(Content.builder()
                .title("Telugu Cultural Heritage Episode 1")
                .description("Detailed exploration of Andhra cultural traditions")
                .contentType(ContentType.EPISODE)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(3600)
                .thumbnailUrl("https://media.communityott.com/thumbnails/ep1.jpg")
                .build());

        testVideo1 = videoAssetRepository.save(VideoAsset.builder()
                .content(testContent1)
                .originalFilename("heritage_ep1.mp4")
                .storageBucket("communityott-media")
                .storageKey("videos/heritage_ep1.mp4")
                .contentType("video/mp4")
                .checksumSha256("sha256-ep1-" + suffix)
                .fileSizeBytes(150_000_000L)
                .durationSeconds(3600)
                .status(VideoAssetStatus.READY)
                .build());

        hlsPackageRepository.save(VideoHlsPackage.builder()
                .videoAsset(testVideo1)
                .storageBucket("communityott-media")
                .masterPlaylistKey("hls/heritage_ep1/" + suffix + "/master.m3u8")
                .status(HlsPackageStatus.READY)
                .build());

        testContent2 = contentRepository.save(Content.builder()
                .title("Rural Weaving Documentary")
                .description("Documentary about traditional handlooms")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(1800)
                .thumbnailUrl("https://media.communityott.com/thumbnails/doc1.jpg")
                .build());

        testVideo2 = videoAssetRepository.save(VideoAsset.builder()
                .content(testContent2)
                .originalFilename("weaving_doc.mp4")
                .storageBucket("communityott-media")
                .storageKey("videos/weaving_doc.mp4")
                .contentType("video/mp4")
                .checksumSha256("sha256-doc1-" + suffix)
                .fileSizeBytes(80_000_000L)
                .durationSeconds(1800)
                .status(VideoAssetStatus.READY)
                .build());

        hlsPackageRepository.save(VideoHlsPackage.builder()
                .videoAsset(testVideo2)
                .storageBucket("communityott-media")
                .masterPlaylistKey("hls/weaving_doc/" + suffix + "/master.m3u8")
                .status(HlsPackageStatus.READY)
                .build());
    }

    private String createSessionAndEvents(User user, Content content, VideoAsset video, Platform platform) {
        StartPlaybackSessionRequest sessionReq = StartPlaybackSessionRequest.builder()
                .platform(platform)
                .deviceId("TestDevice-" + platform.name())
                .build();
        PlaybackSessionResponse sessionResp = sessionService.startSession(user.getId(), content.getId(), sessionReq);
        String sessionId = sessionResp.getPlaybackSessionId();

        // Ingest PLAY event
        playbackEventService.recordEvent(user.getId(), content.getId(), sessionId,
                PlaybackEventRequest.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(PlaybackEventType.PLAY)
                        .positionSeconds(10)
                        .durationSeconds(content.getDurationSeconds())
                        .occurredAt(Instant.now())
                        .sequence(1)
                        .build());

        // Ingest BUFFER_START event
        playbackEventService.recordEvent(user.getId(), content.getId(), sessionId,
                PlaybackEventRequest.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(PlaybackEventType.BUFFER_START)
                        .positionSeconds(100)
                        .durationSeconds(content.getDurationSeconds())
                        .occurredAt(Instant.now())
                        .sequence(2)
                        .build());

        // Ingest QUALITY_CHANGE event
        playbackEventService.recordEvent(user.getId(), content.getId(), sessionId,
                PlaybackEventRequest.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(PlaybackEventType.QUALITY_CHANGE)
                        .positionSeconds(150)
                        .durationSeconds(content.getDurationSeconds())
                        .occurredAt(Instant.now())
                        .sequence(3)
                        .build());

        // Ingest COMPLETE event
        playbackEventService.recordEvent(user.getId(), content.getId(), sessionId,
                PlaybackEventRequest.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(PlaybackEventType.COMPLETE)
                        .positionSeconds(content.getDurationSeconds())
                        .durationSeconds(content.getDurationSeconds())
                        .occurredAt(Instant.now())
                        .sequence(4)
                        .build());

        return sessionId;
    }

    @Test
    @DisplayName("1. Aggregation pipeline processes raw events and creates daily metrics")
    void testAggregationProcessing() {
        createSessionAndEvents(regularUser, testContent1, testVideo1, Platform.IOS);
        createSessionAndEvents(managerUser, testContent2, testVideo2, Platform.ANDROID);

        AggregationJobResponse response = aggregationService.runAggregation(500);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getEventsProcessed()).isGreaterThanOrEqualTo(8);
        assertThat(response.getLastProcessedEventId()).isNotNull();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var metric1 = dailyMetricRepository.findByMetricDateAndContentIdAndPlatform(today, testContent1.getId(), Platform.IOS);
        assertThat(metric1).isPresent();
        assertThat(metric1.get().getTotalPlays()).isGreaterThanOrEqualTo(1);
        assertThat(metric1.get().getCompletionCount()).isGreaterThanOrEqualTo(1);
        assertThat(metric1.get().getBufferEventCount()).isGreaterThanOrEqualTo(1);
        assertThat(metric1.get().getQualityChangeCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("2. Incremental aggregation updates checkpoint and processes only new events")
    void testIncrementalAggregationCheckpoint() {
        String sessionId = createSessionAndEvents(regularUser, testContent1, testVideo1, Platform.IOS);
        AggregationJobResponse firstRun = aggregationService.runAggregation(500);
        assertThat(firstRun.getEventsProcessed()).isEqualTo(4);

        // Second run without new events
        AggregationJobResponse secondRun = aggregationService.runAggregation(500);
        assertThat(secondRun.getEventsProcessed()).isEqualTo(0);
        assertThat(secondRun.getMessage()).isEqualTo("No new events to aggregate");

        // Add 1 new event
        playbackEventService.recordEvent(regularUser.getId(), testContent1.getId(), sessionId,
                PlaybackEventRequest.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(PlaybackEventType.PAUSE)
                        .positionSeconds(200)
                        .durationSeconds(3600)
                        .occurredAt(Instant.now())
                        .sequence(5)
                        .build());

        AggregationJobResponse thirdRun = aggregationService.runAggregation(500);
        assertThat(thirdRun.getEventsProcessed()).isEqualTo(1);
    }

    @Test
    @DisplayName("3. GET /api/v1/analytics/overview returns aggregate statistics")
    void testAnalyticsOverviewEndpoint() throws Exception {
        createSessionAndEvents(regularUser, testContent1, testVideo1, Platform.IOS);
        createSessionAndEvents(regularUser, testContent2, testVideo2, Platform.ANDROID);
        aggregationService.runAggregation(500);

        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPlays").value(2))
                .andExpect(jsonPath("$.data.completedPlays").value(2))
                .andExpect(jsonPath("$.data.bufferEvents").value(2))
                .andExpect(jsonPath("$.data.qualityChanges").value(2))
                .andExpect(jsonPath("$.data.completionRate").value(1.0));
    }

    @Test
    @DisplayName("4. GET /api/v1/analytics/content/{contentId} returns specific content metrics")
    void testContentAnalyticsEndpoint() throws Exception {
        createSessionAndEvents(regularUser, testContent1, testVideo1, Platform.IOS);
        aggregationService.runAggregation(500);

        mockMvc.perform(get("/api/v1/analytics/content/" + testContent1.getId())
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contentId").value(testContent1.getId()))
                .andExpect(jsonPath("$.data.title").value(testContent1.getTitle()))
                .andExpect(jsonPath("$.data.totalPlays").value(1))
                .andExpect(jsonPath("$.data.completedPlays").value(1));
    }

    @Test
    @DisplayName("5. GET /api/v1/analytics/trends returns time-series data points")
    void testDailyTrendsEndpoint() throws Exception {
        createSessionAndEvents(regularUser, testContent1, testVideo1, Platform.IOS);
        aggregationService.runAggregation(500);

        mockMvc.perform(get("/api/v1/analytics/trends")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.points").isArray())
                .andExpect(jsonPath("$.data.points[0].plays").value(1));
    }

    @Test
    @DisplayName("6. GET /api/v1/analytics/platforms returns breakdown by platform")
    void testPlatformAnalyticsEndpoint() throws Exception {
        createSessionAndEvents(regularUser, testContent1, testVideo1, Platform.IOS);
        createSessionAndEvents(regularUser, testContent2, testVideo2, Platform.ANDROID);
        aggregationService.runAggregation(500);

        mockMvc.perform(get("/api/v1/analytics/platforms")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platforms").isArray());
    }

    @Test
    @DisplayName("7. GET /api/v1/analytics/content/top returns ranked content")
    void testTopContentRankingEndpoint() throws Exception {
        createSessionAndEvents(regularUser, testContent1, testVideo1, Platform.IOS);
        createSessionAndEvents(regularUser, testContent2, testVideo2, Platform.ANDROID);
        aggregationService.runAggregation(500);

        mockMvc.perform(get("/api/v1/analytics/content/top")
                        .param("metric", "WATCH_TIME")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].rank").value(1));
    }

    @Test
    @DisplayName("8. POST /api/v1/analytics/aggregate triggers manual aggregation job")
    void testManualAggregationTrigger() throws Exception {
        createSessionAndEvents(regularUser, testContent1, testVideo1, Platform.IOS);

        mockMvc.perform(post("/api/v1/analytics/aggregate")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.eventsProcessed").isNumber());
    }

    @Test
    @DisplayName("9. RBAC: User without ANALYTICS_VIEW receives 403 Forbidden")
    void testUserForbiddenFromAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("10. Security: Unauthenticated request receives 401 Unauthorized")
    void testUnauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("11. Invalid date range (start after end) returns 400 Bad Request")
    void testInvalidDateRangeReturns400() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate tomorrow = today.plusDays(1);

        mockMvc.perform(get("/api/v1/analytics/overview")
                        .param("startDate", tomorrow.toString())
                        .param("endDate", today.toString())
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("12. Date range exceeding 90 days returns 400 Bad Request")
    void testExcessiveDateRangeReturns400() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate oldDate = today.minusDays(100);

        mockMvc.perform(get("/api/v1/analytics/overview")
                        .param("startDate", oldDate.toString())
                        .param("endDate", today.toString())
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("13. Non-existent content analytics returns 404 Not Found")
    void testNonExistentContentAnalyticsReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/content/999999")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("14. Empty dataset returns zeroed overview gracefully")
    void testEmptyDatasetReturnsZeroes() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AnalyticsOverviewResponse response = queryService.getOverview(today.minusDays(30), today);

        assertThat(response.getTotalViews()).isEqualTo(0);
        assertThat(response.getTotalPlays()).isEqualTo(0);
        assertThat(response.getCompletionRate()).isEqualTo(0.0);
        assertThat(response.getAverageSessionDurationSeconds()).isEqualTo(0);
    }
}
