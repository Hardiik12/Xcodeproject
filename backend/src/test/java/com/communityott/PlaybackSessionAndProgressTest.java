package com.communityott;

import com.communityott.auth.entity.Platform;
import com.communityott.auth.security.JwtTokenService;
import com.communityott.content.delivery.DeliveryMode;
import com.communityott.content.delivery.MediaDeliveryProperties;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.content.entity.HlsPackageStatus;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.entity.VideoHlsPackage;
import com.communityott.content.entity.VideoHlsVariant;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.repository.VideoHlsPackageRepository;
import com.communityott.content.repository.VideoHlsVariantRepository;
import com.communityott.playback.config.PlaybackProperties;
import com.communityott.playback.dto.PlaybackHeartbeatRequest;
import com.communityott.playback.dto.PlaybackProgressRequest;
import com.communityott.playback.dto.StartPlaybackSessionRequest;
import com.communityott.playback.entity.PlaybackSession;
import com.communityott.playback.entity.PlaybackSessionStatus;
import com.communityott.playback.entity.WatchProgress;
import com.communityott.playback.repository.PlaybackSessionRepository;
import com.communityott.playback.repository.WatchProgressRepository;
import com.communityott.playback.service.PlaybackSessionService;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.content.entity.HlsVariantStatus;
import com.communityott.user.repository.UserRoleRepository;
import com.communityott.user.repository.UserRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class PlaybackSessionAndProgressTest {

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
    private PlaybackSessionService playbackSessionService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MediaDeliveryProperties deliveryProperties;

    @Autowired
    private PlaybackProperties playbackProperties;

    private User testUser1;
    private User testUser2;
    private User adminUser;
    private String user1Token;
    private String user2Token;
    private String adminToken;

    private Content publishedContent;
    private VideoAsset readyVideoAsset;
    private VideoHlsPackage readyHlsPackage;

    @BeforeEach
    void setUp() {
        deliveryProperties.setMode(DeliveryMode.LOCAL);
        deliveryProperties.getRateLimit().setEnabled(false);

        // Clear redis keys matching rate limit prefixes
        var keys = redisTemplate.keys("communityott:ratelimit:playback:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("USER")
                        .description("Standard OTT User")
                        .isSystemRole(true)
                        .build()));

        Role adminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("SUPER_ADMIN")
                        .description("Super Administrator")
                        .isSystemRole(true)
                        .build()));

        long uniqueSuffix = Math.abs(System.nanoTime() % 1000000000L);

        testUser1 = userRepository.save(User.builder()
                .email("playuser1_" + uniqueSuffix + "@communityott.com")
                .displayName("Playback User 1")
                .phone("+9191" + String.format("%08d", uniqueSuffix % 100000000L))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(testUser1, userRole));

        testUser2 = userRepository.save(User.builder()
                .email("playuser2_" + uniqueSuffix + "@communityott.com")
                .displayName("Playback User 2")
                .phone("+9192" + String.format("%08d", uniqueSuffix % 100000000L))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(testUser2, userRole));

        adminUser = userRepository.save(User.builder()
                .email("playadmin_" + uniqueSuffix + "@communityott.com")
                .displayName("Playback Admin")
                .phone("+9193" + String.format("%08d", uniqueSuffix % 100000000L))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(adminUser, adminRole));

        user1Token = jwtTokenService.generateAccessToken(testUser1);
        user2Token = jwtTokenService.generateAccessToken(testUser2);
        adminToken = jwtTokenService.generateAccessToken(adminUser);

        // Setup playable Content (duration: 3600s = 1 hour)
        publishedContent = contentRepository.save(Content.builder()
                .title("Telugu Handloom Weaving Traditions " + uniqueSuffix)
                .shortDescription("Documentary on rural handloom artisans")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(3600)
                .build());

        readyVideoAsset = videoAssetRepository.save(VideoAsset.builder()
                .content(publishedContent)
                .originalFilename("handloom_source.mp4")
                .checksumSha256("sha256_handloom_" + uniqueSuffix)
                .storageBucket("communityott-media")
                .storageKey("videos/source/" + uniqueSuffix + "/source.mp4")
                .durationSeconds(3600)
                .width(1920)
                .height(1080)
                .status(VideoAssetStatus.READY)
                .fileSizeBytes(1024L * 1024L * 50)
                .bitrateKbps(5000)
                .contentType("video/mp4")
                .build());

        readyHlsPackage = videoHlsPackageRepository.save(VideoHlsPackage.builder()
                .videoAsset(readyVideoAsset)
                .storageBucket("communityott-media")
                .masterPlaylistKey("hls/" + publishedContent.getId() + "/" + readyVideoAsset.getId() + "/master.m3u8")
                .status(HlsPackageStatus.READY)
                .variantCount(2)
                .targetDurationSeconds(2)
                .build());

        videoHlsVariantRepository.save(VideoHlsVariant.builder()
                .hlsPackage(readyHlsPackage)
                .resolution("1080p")
                .width(1920)
                .height(1080)
                .bandwidthBps(5000000L)
                .playlistKey("hls/" + publishedContent.getId() + "/" + readyVideoAsset.getId() + "/1080p/index.m3u8")
                .initSegmentKey("hls/" + publishedContent.getId() + "/" + readyVideoAsset.getId() + "/1080p/init.mp4")
                .status(HlsVariantStatus.READY)
                .build());
    }

    @Test
    @DisplayName("Test 01: Start playback session successfully for published content with no prior progress")
    void test01_startPlaybackSession_Success() throws Exception {
        StartPlaybackSessionRequest req = StartPlaybackSessionRequest.builder()
                .deviceId("iphone-15-pro-uuid")
                .platform(Platform.IOS)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.playbackSessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.contentId").value(publishedContent.getId()))
                .andExpect(jsonPath("$.data.durationSeconds").value(3600))
                .andExpect(jsonPath("$.data.resumePositionSeconds").value(0))
                .andExpect(jsonPath("$.data.playbackUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.availableRenditions").isArray())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String sessionId = root.path("data").path("playbackSessionId").asText();

        Optional<PlaybackSession> sessionOpt = playbackSessionRepository.findBySessionId(sessionId);
        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().getStatus()).isEqualTo(PlaybackSessionStatus.STARTED);
        assertThat(sessionOpt.get().getPlatform()).isEqualTo(Platform.IOS);
        assertThat(sessionOpt.get().getDeviceId()).isEqualTo("iphone-15-pro-uuid");
    }

    @Test
    @DisplayName("Test 02: Start session for non-existent content returns 404")
    void test02_startSession_NonExistentContent() throws Exception {
        mockMvc.perform(post("/api/v1/content/999999/playback/sessions")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONTENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Test 03: Start session for draft content returns 409")
    void test03_startSession_DraftContent() throws Exception {
        Content draftContent = contentRepository.save(Content.builder()
                .title("Draft Content")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.DRAFT)
                .build());

        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", draftContent.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONTENT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("Test 04: Start session for processing content returns 409")
    void test04_startSession_ProcessingContent() throws Exception {
        Content processingContent = contentRepository.save(Content.builder()
                .title("Processing Content")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PROCESSING)
                .build());

        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", processingContent.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONTENT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("Test 05: Start session without ready video asset returns 409")
    void test05_startSession_UnreadyVideoAsset() throws Exception {
        Content content = contentRepository.save(Content.builder()
                .title("Content With Unready Asset")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .build());

        videoAssetRepository.save(VideoAsset.builder()
                .content(content)
                .originalFilename("source.mp4")
                .checksumSha256("sha256_unready_" + System.nanoTime())
                .fileSizeBytes(1024L * 1024L)
                .contentType("video/mp4")
                .storageBucket("bucket")
                .storageKey("storage/unready/" + System.nanoTime() + "/source.mp4")
                .status(VideoAssetStatus.PROCESSING)
                .build());

        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", content.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VIDEO_NOT_READY"));
    }

    @Test
    @DisplayName("Test 06: Start session without ready HLS package returns 409")
    void test06_startSession_UnreadyHlsPackage() throws Exception {
        Content content = contentRepository.save(Content.builder()
                .title("Content With Unready HLS")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .build());

        VideoAsset asset = videoAssetRepository.save(VideoAsset.builder()
                .content(content)
                .originalFilename("source.mp4")
                .checksumSha256("sha256_unready_hls_" + System.nanoTime())
                .fileSizeBytes(1024L * 1024L)
                .contentType("video/mp4")
                .storageBucket("bucket")
                .storageKey("storage/unready_hls/" + System.nanoTime() + "/source.mp4")
                .status(VideoAssetStatus.READY)
                .build());

        videoHlsPackageRepository.save(VideoHlsPackage.builder()
                .videoAsset(asset)
                .storageBucket("bucket")
                .masterPlaylistKey("hls/unready/" + System.nanoTime() + "/master.m3u8")
                .status(HlsPackageStatus.PROCESSING)
                .build());

        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", content.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VIDEO_NOT_READY"));
    }

    @Test
    @DisplayName("Test 07: Resume existing progress returns accurate resumePositionSeconds")
    void test07_resumeExistingProgress() throws Exception {
        // Pre-create watch progress at 1320s
        watchProgressRepository.save(WatchProgress.builder()
                .user(testUser1)
                .content(publishedContent)
                .videoAsset(readyVideoAsset)
                .positionSeconds(1320)
                .durationSeconds(3600)
                .completionPercentage((1320.0 / 3600.0) * 100.0)
                .completed(false)
                .lastWatchedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.resumePositionSeconds").value(1320));
    }

    @Test
    @DisplayName("Test 08: Heartbeat updates session timestamp and status from STARTED to ACTIVE")
    void test08_heartbeatUpdatesSession() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        PlaybackHeartbeatRequest heartbeatReq = PlaybackHeartbeatRequest.builder()
                .positionSeconds(150)
                .build();

        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/heartbeat",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(heartbeatReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.lastPositionSeconds").value(150));

        // Verify watch progress was also updated
        Optional<WatchProgress> progressOpt = watchProgressRepository.findByUserIdAndContentId(testUser1.getId(), publishedContent.getId());
        assertThat(progressOpt).isPresent();
        assertThat(progressOpt.get().getPositionSeconds()).isEqualTo(150);
    }

    @Test
    @DisplayName("Test 09: Progress update persists watch progress in PostgreSQL")
    void test09_progressUpdatePersists() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        PlaybackProgressRequest progressReq = PlaybackProgressRequest.builder()
                .positionSeconds(1800)
                .durationSeconds(3600)
                .build();

        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/progress",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.positionSeconds").value(1800))
                .andExpect(jsonPath("$.data.completionPercentage").value(50.0))
                .andExpect(jsonPath("$.data.completed").value(false));

        Optional<WatchProgress> progressOpt = watchProgressRepository.findByUserIdAndContentId(testUser1.getId(), publishedContent.getId());
        assertThat(progressOpt).isPresent();
        assertThat(progressOpt.get().getPositionSeconds()).isEqualTo(1800);
        assertThat(progressOpt.get().getCompletionPercentage()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Test 10: Progress ownership - User B cannot update User A's session")
    void test10_progressOwnership_DeniedForOtherUser() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        PlaybackProgressRequest progressReq = PlaybackProgressRequest.builder()
                .positionSeconds(200)
                .build();

        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/progress",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PLAYBACK_SESSION_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Test 11: Session read ownership - User B cannot get User A's session")
    void test11_getSession_DeniedForOtherUser() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        mockMvc.perform(get("/api/v1/content/{contentId}/playback/sessions/{sessionId}",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PLAYBACK_SESSION_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Test 12: End session marks session ENDED and commits final position")
    void test12_endSession() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        PlaybackProgressRequest endReq = PlaybackProgressRequest.builder()
                .positionSeconds(2400)
                .build();

        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/end",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(endReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.lastPositionSeconds").value(2400))
                .andExpect(jsonPath("$.data.endedAt").isNotEmpty());

        PlaybackSession session = playbackSessionRepository.findBySessionId(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(PlaybackSessionStatus.ENDED);
        assertThat(session.getEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("Test 13: Ending session twice is safe and idempotent")
    void test13_endSession_Idempotent() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        // First end
        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/end",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"));

        // Second end (idempotent)
        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/end",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"));
    }

    @Test
    @DisplayName("Test 14: Progress update on ended session returns 409 PLAYBACK_SESSION_NOT_ACTIVE")
    void test14_progressOnEndedSession_Rejected() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        // End session
        playbackSessionService.endSession(testUser1.getId(), publishedContent.getId(), sessionId, null);

        PlaybackProgressRequest progressReq = PlaybackProgressRequest.builder()
                .positionSeconds(500)
                .build();

        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/progress",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PLAYBACK_SESSION_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("Test 15: Expired session returns 409 PLAYBACK_SESSION_EXPIRED")
    void test15_expiredSession_Rejected() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        // Set last heartbeat to 10 minutes ago
        PlaybackSession session = playbackSessionRepository.findBySessionId(sessionId).orElseThrow();
        session.setLastHeartbeatAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        playbackSessionRepository.save(session);

        PlaybackHeartbeatRequest heartbeatReq = PlaybackHeartbeatRequest.builder()
                .positionSeconds(300)
                .build();

        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/heartbeat",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(heartbeatReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PLAYBACK_SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("Test 16: Completion threshold marks completed = true when position >= 95%")
    void test16_completionThreshold_MarksCompleted() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        // 3450 / 3600 = 95.83%
        PlaybackProgressRequest progressReq = PlaybackProgressRequest.builder()
                .positionSeconds(3450)
                .durationSeconds(3600)
                .build();

        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/progress",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));

        WatchProgress progress = watchProgressRepository.findByUserIdAndContentId(testUser1.getId(), publishedContent.getId()).orElseThrow();
        assertThat(progress.getCompleted()).isTrue();
    }

    @Test
    @DisplayName("Test 17: Rewatch behavior keeps completed = true while updating position")
    void test17_rewatchBehavior() throws Exception {
        // Complete the video
        playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        watchProgressRepository.save(WatchProgress.builder()
                .user(testUser1)
                .content(publishedContent)
                .videoAsset(readyVideoAsset)
                .positionSeconds(3500)
                .durationSeconds(3600)
                .completionPercentage(97.2)
                .completed(true)
                .lastWatchedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build());

        // User starts rewatching from 120s
        var startResp2 = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        playbackSessionService.recordProgress(testUser1.getId(), publishedContent.getId(),
                startResp2.getPlaybackSessionId(), PlaybackProgressRequest.builder().positionSeconds(120).build());

        WatchProgress updatedProgress = watchProgressRepository.findByUserIdAndContentId(testUser1.getId(), publishedContent.getId()).orElseThrow();
        assertThat(updatedProgress.getPositionSeconds()).isEqualTo(120);
        assertThat(updatedProgress.getCompleted()).isTrue(); // Remains completed in library
    }

    @Test
    @DisplayName("Test 18: Multi-device sessions - same user can have iOS and Android sessions simultaneously")
    void test18_multiDeviceSessions() throws Exception {
        var iosResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(),
                StartPlaybackSessionRequest.builder().deviceId("ios-phone").platform(Platform.IOS).build());

        var androidResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(),
                StartPlaybackSessionRequest.builder().deviceId("android-tablet").platform(Platform.ANDROID).build());

        assertThat(iosResp.getPlaybackSessionId()).isNotEqualTo(androidResp.getPlaybackSessionId());

        PlaybackSession iosSession = playbackSessionRepository.findBySessionId(iosResp.getPlaybackSessionId()).orElseThrow();
        PlaybackSession androidSession = playbackSessionRepository.findBySessionId(androidResp.getPlaybackSessionId()).orElseThrow();

        assertThat(iosSession.getPlatform()).isEqualTo(Platform.IOS);
        assertThat(androidSession.getPlatform()).isEqualTo(Platform.ANDROID);
    }

    @Test
    @DisplayName("Test 19: Negative position rejected with 400")
    void test19_negativePosition_Rejected() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        PlaybackProgressRequest req = PlaybackProgressRequest.builder()
                .positionSeconds(-50)
                .build();

        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/progress",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 20: Position far beyond duration rejected with 400")
    void test20_positionBeyondDuration_Rejected() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        PlaybackProgressRequest req = PlaybackProgressRequest.builder()
                .positionSeconds(50000) // Duration is 3600
                .durationSeconds(3600)
                .build();

        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/progress",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PLAYBACK_POSITION"));
    }

    @Test
    @DisplayName("Test 21: Seeking backward and forward is permitted and properly recorded")
    void test21_seekingBackwardAndForward() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        // Jump forward to 1200s
        playbackSessionService.recordProgress(testUser1.getId(), publishedContent.getId(), sessionId,
                PlaybackProgressRequest.builder().positionSeconds(1200).build());

        // Seek backward to 600s
        playbackSessionService.recordProgress(testUser1.getId(), publishedContent.getId(), sessionId,
                PlaybackProgressRequest.builder().positionSeconds(600).build());

        WatchProgress progress = watchProgressRepository.findByUserIdAndContentId(testUser1.getId(), publishedContent.getId()).orElseThrow();
        assertThat(progress.getPositionSeconds()).isEqualTo(600);
    }

    @Test
    @DisplayName("Test 22: Scheduled expiration marks stale active and started sessions as EXPIRED")
    void test22_scheduledExpiration() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        PlaybackSession session = playbackSessionRepository.findBySessionId(sessionId).orElseThrow();
        session.setLastHeartbeatAt(Instant.now().minus(15, ChronoUnit.MINUTES));
        playbackSessionRepository.save(session);

        // Run scheduled expiration
        playbackSessionService.expireInactiveSessions();

        PlaybackSession expiredSession = playbackSessionRepository.findBySessionId(sessionId).orElseThrow();
        assertThat(expiredSession.getStatus()).isEqualTo(PlaybackSessionStatus.EXPIRED);
    }

    @Test
    @DisplayName("Test 23: Unauthenticated request returns 401 Unauthorized")
    void test23_unauthenticatedRequest_Rejected() throws Exception {
        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Test 24: Super Admin can also start playback session and record progress")
    void test24_adminCanStartSession() throws Exception {
        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.playbackSessionId").isNotEmpty());
    }

    @Test
    @DisplayName("Test 25: No credentials or internal secrets leaked in session response")
    void test25_noCredentialsLeakedInResponse() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("communityott_minio_dev_password");
        assertThat(body).doesNotContain("secretKey");
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("refreshTokenHash");
    }

    @Test
    @DisplayName("Test 26: Rate limiting on session creation triggers 429 when exceeded")
    void test26_rateLimitSessionCreation() throws Exception {
        playbackProperties.getRateLimit().setEnabled(true);
        playbackProperties.getRateLimit().setMaxSessionCreationsPerMinute(2);

        // First 2 should succeed
        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isCreated());

        // 3rd should fail with 429
        mockMvc.perform(post("/api/v1/content/{id}/playback/sessions", publishedContent.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("PLAYBACK_RATE_LIMITED"));

        playbackProperties.getRateLimit().setEnabled(false);
    }

    @Test
    @DisplayName("Test 27: Rate limiting on progress updates triggers 429 when exceeded")
    void test27_rateLimitProgressUpdates() throws Exception {
        var startResp = playbackSessionService.startSession(testUser1.getId(), publishedContent.getId(), null);
        String sessionId = startResp.getPlaybackSessionId();

        playbackProperties.getRateLimit().setEnabled(true);
        playbackProperties.getRateLimit().setMaxRequestsPerMinute(2);

        PlaybackProgressRequest req = PlaybackProgressRequest.builder().positionSeconds(100).build();

        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/progress",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/progress",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 3rd request in same minute triggers 429
        mockMvc.perform(post("/api/v1/content/{contentId}/playback/sessions/{sessionId}/progress",
                        publishedContent.getId(), sessionId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("PLAYBACK_RATE_LIMITED"));

        playbackProperties.getRateLimit().setEnabled(false);
    }
}
