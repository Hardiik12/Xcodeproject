package com.communityott;

import com.communityott.auth.entity.Platform;
import com.communityott.auth.security.JwtTokenService;
import com.communityott.content.delivery.DeliveryMode;
import com.communityott.content.delivery.MediaDeliveryProperties;
import com.communityott.content.entity.*;
import com.communityott.content.repository.*;
import com.communityott.history.entity.WatchHistory;
import com.communityott.history.repository.WatchHistoryRepository;
import com.communityott.history.service.WatchHistoryService;
import com.communityott.playback.config.PlaybackProperties;
import com.communityott.playback.dto.PlaybackHeartbeatRequest;
import com.communityott.playback.dto.PlaybackProgressRequest;
import com.communityott.playback.dto.StartPlaybackSessionRequest;
import com.communityott.playback.entity.PlaybackSession;
import com.communityott.playback.entity.PlaybackSessionStatus;
import com.communityott.playback.repository.PlaybackSessionRepository;
import com.communityott.playback.repository.WatchProgressRepository;
import com.communityott.playback.service.PlaybackSessionService;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class WatchHistoryTest {

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
    private VideoAssetRepository videoAssetRepository;

    @Autowired
    private VideoHlsPackageRepository videoHlsPackageRepository;

    @Autowired
    private VideoHlsVariantRepository videoHlsVariantRepository;

    @Autowired
    private PlaybackSessionRepository playbackSessionRepository;

    @Autowired
    private WatchProgressRepository watchProgressRepository;

    @Autowired
    private WatchHistoryRepository watchHistoryRepository;

    @Autowired
    private WatchHistoryService watchHistoryService;

    @Autowired
    private PlaybackSessionService playbackSessionService;

    @Autowired
    private PlaybackProperties playbackProperties;

    @Autowired
    private MediaDeliveryProperties deliveryProperties;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User testUser1;
    private User testUser2;
    private String user1Token;
    private String user2Token;

    private Content publishedContent1;
    private Content publishedContent2;
    private Content publishedContent3;

    @BeforeEach
    void setUp() {
        deliveryProperties.setMode(DeliveryMode.LOCAL);
        deliveryProperties.getRateLimit().setEnabled(false);

        // Clear redis keys matching rate limit prefixes
        var keys = redisTemplate.keys("communityott:ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("USER")
                        .description("Standard OTT User")
                        .isSystemRole(true)
                        .build()));

        long uniqueSuffix = Math.abs(System.nanoTime() % 1000000000L);

        // Setup Test Users
        testUser1 = userRepository.save(User.builder()
                .email("user1_" + uniqueSuffix + "@communityott.com")
                .displayName("Watch History User 1")
                .phone("+9191" + String.format("%08d", uniqueSuffix % 100000000L))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(testUser1, userRole));

        testUser2 = userRepository.save(User.builder()
                .email("user2_" + uniqueSuffix + "@communityott.com")
                .displayName("Watch History User 2")
                .phone("+9192" + String.format("%08d", uniqueSuffix % 100000000L))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(testUser2, userRole));

        user1Token = jwtTokenService.generateAccessToken(testUser1);
        user2Token = jwtTokenService.generateAccessToken(testUser2);

        // Setup Published Contents with ready VideoAsset & HLS Packages
        publishedContent1 = createReadyContent("Documentary 1: Telangana Weaving " + uniqueSuffix, 3600);
        publishedContent2 = createReadyContent("Documentary 2: Kuchipudi Dance " + uniqueSuffix, 1800);
        publishedContent3 = createReadyContent("Documentary 3: Folk Songs " + uniqueSuffix, 2400);
    }

    private Content createReadyContent(String title, int duration) {
        Content content = contentRepository.save(Content.builder()
                .title(title)
                .subtitle("A cultural heritage journey")
                .description("Detailed description for " + title)
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(duration)
                .thumbnailUrl("https://media.communityott.com/thumbs/" + System.nanoTime() + ".jpg")
                .bannerUrl("https://media.communityott.com/banners/" + System.nanoTime() + ".jpg")
                .build());

        VideoAsset asset = videoAssetRepository.save(VideoAsset.builder()
                .content(content)
                .originalFilename("source_" + System.nanoTime() + ".mp4")
                .checksumSha256("sha256_" + System.nanoTime())
                .fileSizeBytes(50L * 1024L * 1024L)
                .contentType("video/mp4")
                .storageBucket("communityott-media")
                .storageKey("sources/" + System.nanoTime() + "/source.mp4")
                .durationSeconds(duration)
                .width(1920)
                .height(1080)
                .bitrateKbps(5000)
                .status(VideoAssetStatus.READY)
                .build());

        VideoHlsPackage hlsPackage = videoHlsPackageRepository.save(VideoHlsPackage.builder()
                .videoAsset(asset)
                .storageBucket("communityott-media")
                .masterPlaylistKey("hls/" + content.getId() + "/" + asset.getId() + "/master.m3u8")
                .status(HlsPackageStatus.READY)
                .build());

        videoHlsVariantRepository.save(VideoHlsVariant.builder()
                .hlsPackage(hlsPackage)
                .resolution("1080p")
                .width(1920)
                .height(1080)
                .bandwidthBps(5000000L)
                .averageBandwidthBps(4500000L)
                .codecs("avc1.640028,mp4a.40.2")
                .frameRate(24.0)
                .playlistKey("hls/" + content.getId() + "/" + asset.getId() + "/1080p/playlist.m3u8")
                .initSegmentKey("hls/" + content.getId() + "/" + asset.getId() + "/1080p/init.mp4")
                .targetDurationSeconds(6)
                .segmentCount(10)
                .status(HlsVariantStatus.READY)
                .build());

        return content;
    }

    @Test
    @DisplayName("Test 01: Unauthenticated request to get history returns 401")
    void test01_getHistory_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Test 02: Empty history returns empty page")
    void test02_getHistory_Empty() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/history")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("Test 03: Playback session creation alone does NOT create watch history if no progress")
    void test03_startSessionOnly_DoesNotCreateHistory() throws Exception {
        StartPlaybackSessionRequest request = StartPlaybackSessionRequest.builder()
                .deviceId("iphone-15")
                .platform(Platform.IOS)
                .build();

        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent1.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Verify history is still empty
        long historyCount = watchHistoryRepository.countByUserId(testUser1.getId());
        assertThat(historyCount).isZero();
    }

    @Test
    @DisplayName("Test 04: Progress update creates and updates watch history record")
    void test04_progressUpdate_CreatesWatchHistory() throws Exception {
        // Start session
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent1.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(StartPlaybackSessionRequest.builder()
                                .deviceId("iphone-15-pro")
                                .platform(Platform.IOS)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(sessionResult.getResponse().getContentAsString());
        String sessionId = root.path("data").path("playbackSessionId").asText();

        // Report Progress at 600s
        PlaybackProgressRequest progressRequest = PlaybackProgressRequest.builder()
                .positionSeconds(600)
                .durationSeconds(3600)
                .build();

        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions/{sessionId}/progress",
                        publishedContent1.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressRequest)))
                .andExpect(status().isOk());

        // Get History
        mockMvc.perform(get("/api/v1/users/me/history")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].contentId").value(publishedContent1.getId()))
                .andExpect(jsonPath("$.data.content[0].title").value(publishedContent1.getTitle()))
                .andExpect(jsonPath("$.data.content[0].watchedSeconds").value(600))
                .andExpect(jsonPath("$.data.content[0].durationSeconds").value(3600))
                .andExpect(jsonPath("$.data.content[0].platform").value("IOS"))
                .andExpect(jsonPath("$.data.content[0].deviceId").value("iphone-15-pro"))
                .andExpect(jsonPath("$.data.content[0].completed").value(false))
                .andExpect(jsonPath("$.data.content[0].contentAvailable").value(true));
    }

    @Test
    @DisplayName("Test 05: Heartbeat with position creates and updates watch history")
    void test05_heartbeatWithPosition_UpdatesHistory() throws Exception {
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent2.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(StartPlaybackSessionRequest.builder()
                                .deviceId("android-pixel-8")
                                .platform(Platform.ANDROID)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .path("data").path("playbackSessionId").asText();

        // Send Heartbeat at 120s
        PlaybackHeartbeatRequest heartbeat = PlaybackHeartbeatRequest.builder()
                .positionSeconds(120)
                .build();

        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions/{sessionId}/heartbeat",
                        publishedContent2.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(heartbeat)))
                .andExpect(status().isOk());

        // Verify history has entry for Content 2
        WatchHistory history = watchHistoryRepository.findByUserIdAndContentId(testUser1.getId(), publishedContent2.getId())
                .orElseThrow();
        assertThat(history.getWatchedSeconds()).isEqualTo(120);
        assertThat(history.getPlatform()).isEqualTo(Platform.ANDROID);
        assertThat(history.getDeviceId()).isEqualTo("android-pixel-8");
    }

    @Test
    @DisplayName("Test 06: Completion threshold marks history completed")
    void test06_completion_MarksHistoryCompleted() throws Exception {
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent1.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .path("data").path("playbackSessionId").asText();

        // Report 96% progress (3456 / 3600)
        PlaybackProgressRequest progressRequest = PlaybackProgressRequest.builder()
                .positionSeconds(3456)
                .durationSeconds(3600)
                .build();

        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions/{sessionId}/progress",
                        publishedContent1.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressRequest)))
                .andExpect(status().isOk());

        WatchHistory history = watchHistoryRepository.findByUserIdAndContentId(testUser1.getId(), publishedContent1.getId())
                .orElseThrow();
        assertThat(history.getCompleted()).isTrue();
        assertThat(history.getCompletionPercentage()).isGreaterThanOrEqualTo(95.0);
    }

    @Test
    @DisplayName("Test 07: Rewatch updates lastWatchedAt and bubbles item to the top")
    void test07_rewatch_BubblesToTop() throws Exception {
        // User 1 watches Content 1
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent1, "sess-1", 500, 3600, "dev-1", Platform.WEB);
        Thread.sleep(50); // slight time shift

        // User 1 watches Content 2 (now Content 2 is newest)
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent2, "sess-2", 300, 1800, "dev-1", Platform.WEB);
        Thread.sleep(50);

        // Fetch history -> Content 2 first, then Content 1
        MvcResult r1 = mockMvc.perform(get("/api/v1/users/me/history")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list1 = objectMapper.readTree(r1.getResponse().getContentAsString()).path("data").path("content");
        assertThat(list1.get(0).path("contentId").asLong()).isEqualTo(publishedContent2.getId());
        assertThat(list1.get(1).path("contentId").asLong()).isEqualTo(publishedContent1.getId());

        // User 1 rewatches Content 1
        Thread.sleep(50);
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent1, "sess-3", 100, 3600, "dev-1", Platform.IOS);

        // Fetch history again -> Content 1 must now be first
        MvcResult r2 = mockMvc.perform(get("/api/v1/users/me/history")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list2 = objectMapper.readTree(r2.getResponse().getContentAsString()).path("data").path("content");
        assertThat(list2.get(0).path("contentId").asLong()).isEqualTo(publishedContent1.getId());
        assertThat(list2.get(1).path("contentId").asLong()).isEqualTo(publishedContent2.getId());
    }

    @Test
    @DisplayName("Test 08: Multi-device watch activity preserves history across devices")
    void test08_multiDevice_PreservesHistory() throws Exception {
        // Watch Content 1 on iPhone
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent1, "sess-ios", 600, 3600, "iphone-15", Platform.IOS);

        // Watch Content 2 on Android
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent2, "sess-android", 400, 1800, "pixel-8", Platform.ANDROID);

        mockMvc.perform(get("/api/v1/users/me/history")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].platform").value("ANDROID"))
                .andExpect(jsonPath("$.data.content[1].platform").value("IOS"));
    }

    @Test
    @DisplayName("Test 09: User A cannot view User B's watch history")
    void test09_userIsolation_CannotViewOtherHistory() throws Exception {
        // User 1 has history for Content 1
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent1, "sess-1", 600, 3600, "dev-1", Platform.WEB);

        // User 2 has history for Content 2
        watchHistoryService.recordViewing(testUser2.getId(), publishedContent2, "sess-2", 400, 1800, "dev-2", Platform.WEB);

        // User 1 requests history -> only Content 1 returned
        mockMvc.perform(get("/api/v1/users/me/history")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].contentId").value(publishedContent1.getId()));

        // User 2 requests history -> only Content 2 returned
        mockMvc.perform(get("/api/v1/users/me/history")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].contentId").value(publishedContent2.getId()));
    }

    @Test
    @DisplayName("Test 10: Delete single history item removes only targeted item and is idempotent")
    void test10_deleteSingleItem_SuccessAndIdempotent() throws Exception {
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent1, "sess-1", 600, 3600, "dev-1", Platform.WEB);
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent2, "sess-2", 400, 1800, "dev-1", Platform.WEB);

        assertThat(watchHistoryRepository.countByUserId(testUser1.getId())).isEqualTo(2);

        // Delete Content 1 from history
        mockMvc.perform(delete("/api/v1/users/me/history/{contentId}", publishedContent1.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Content 1 removed, Content 2 remains
        assertThat(watchHistoryRepository.countByUserId(testUser1.getId())).isEqualTo(1);
        assertThat(watchHistoryRepository.findByUserIdAndContentId(testUser1.getId(), publishedContent1.getId())).isEmpty();
        assertThat(watchHistoryRepository.findByUserIdAndContentId(testUser1.getId(), publishedContent2.getId())).isPresent();

        // Idempotent delete of Content 1 again -> succeeds
        mockMvc.perform(delete("/api/v1/users/me/history/{contentId}", publishedContent1.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Test 11: User A deleting history item does not delete User B's history for the same content")
    void test11_deleteSingleItem_UserIsolation() throws Exception {
        // Both users watched Content 1
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent1, "sess-1", 600, 3600, "dev-1", Platform.WEB);
        watchHistoryService.recordViewing(testUser2.getId(), publishedContent1, "sess-2", 800, 3600, "dev-2", Platform.WEB);

        // User 1 deletes Content 1
        mockMvc.perform(delete("/api/v1/users/me/history/{contentId}", publishedContent1.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk());

        // User 1's history is empty, User 2's history is intact
        assertThat(watchHistoryRepository.findByUserIdAndContentId(testUser1.getId(), publishedContent1.getId())).isEmpty();
        assertThat(watchHistoryRepository.findByUserIdAndContentId(testUser2.getId(), publishedContent1.getId())).isPresent();
    }

    @Test
    @DisplayName("Test 12: Clear all history deletes entire history for user without affecting other users")
    void test12_clearAllHistory() throws Exception {
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent1, "sess-1", 600, 3600, "dev-1", Platform.WEB);
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent2, "sess-2", 400, 1800, "dev-1", Platform.WEB);
        watchHistoryService.recordViewing(testUser2.getId(), publishedContent1, "sess-3", 700, 3600, "dev-2", Platform.WEB);

        // User 1 clears all history
        mockMvc.perform(delete("/api/v1/users/me/history")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // User 1 has 0 history items, User 2 has 1 item
        assertThat(watchHistoryRepository.countByUserId(testUser1.getId())).isZero();
        assertThat(watchHistoryRepository.countByUserId(testUser2.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 13: History pagination with max page size bounding")
    void test13_pagination_AndMaxPageSize() throws Exception {
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent1, "sess-1", 600, 3600, "dev-1", Platform.WEB);
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent2, "sess-2", 400, 1800, "dev-1", Platform.WEB);
        watchHistoryService.recordViewing(testUser1.getId(), publishedContent3, "sess-3", 500, 2400, "dev-1", Platform.WEB);

        // Request page 0, size 2
        mockMvc.perform(get("/api/v1/users/me/history?page=0&size=2")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        // Request page 1, size 2
        mockMvc.perform(get("/api/v1/users/me/history?page=1&size=2")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        // Request size=10000 -> bounded to 50 max
        mockMvc.perform(get("/api/v1/users/me/history?page=0&size=10000")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    @DisplayName("Test 14: End session with position commits watch history")
    void test14_endSessionWithPosition_UpdatesHistory() throws Exception {
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent3.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .path("data").path("playbackSessionId").asText();

        // End session with 1200s position
        PlaybackProgressRequest endReq = PlaybackProgressRequest.builder()
                .positionSeconds(1200)
                .durationSeconds(2400)
                .build();

        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions/{sessionId}/end",
                        publishedContent3.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(endReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"));

        WatchHistory history = watchHistoryRepository.findByUserIdAndContentId(testUser1.getId(), publishedContent3.getId())
                .orElseThrow();
        assertThat(history.getWatchedSeconds()).isEqualTo(1200);
        assertThat(history.getDurationSeconds()).isEqualTo(2400);
        assertThat(history.getCompletionPercentage()).isEqualTo(50.0);
    }
}
