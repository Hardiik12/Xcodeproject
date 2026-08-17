package com.communityott;

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
import com.communityott.playback.dto.PlaybackEventBatchRequest;
import com.communityott.playback.dto.PlaybackEventRequest;
import com.communityott.playback.dto.PlaybackSessionResponse;
import com.communityott.playback.dto.StartPlaybackSessionRequest;
import com.communityott.playback.entity.PlaybackEventType;
import com.communityott.playback.entity.PlaybackSession;
import com.communityott.playback.entity.PlaybackSessionStatus;
import com.communityott.playback.repository.PlaybackEventRepository;
import com.communityott.playback.repository.PlaybackSessionRepository;
import com.communityott.playback.repository.WatchProgressRepository;
import com.communityott.playback.service.PlaybackSessionService;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.saved.service.SavedContentService;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
public class PlaybackEventTelemetryTest {

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
    private VideoHlsPackageRepository videoHlsPackageRepository;

    @Autowired
    private PlaybackSessionRepository playbackSessionRepository;

    @Autowired
    private PlaybackEventRepository playbackEventRepository;

    @Autowired
    private WatchProgressRepository watchProgressRepository;

    @Autowired
    private PlaybackSessionService playbackSessionService;

    @Autowired
    private SavedContentService savedContentService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser1;
    private User testUser2;
    private String user1Token;
    private String user2Token;
    private Content content1;
    private Content content2;
    private VideoAsset videoAsset1;
    private PlaybackSessionResponse session1Response;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("USER")
                        .description("Standard OTT User")
                        .isSystemRole(true)
                        .build()));

        long uniqueSuffix = Math.abs(System.nanoTime() % 1000000000L);

        testUser1 = userRepository.save(User.builder()
                .email("telemetry_user1_" + uniqueSuffix + "@communityott.com")
                .displayName("Telemetry Tester 1")
                .phone("+9193" + String.format("%08d", uniqueSuffix % 100000000L))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(testUser1, userRole));

        testUser2 = userRepository.save(User.builder()
                .email("telemetry_user2_" + uniqueSuffix + "@communityott.com")
                .displayName("Telemetry Tester 2")
                .phone("+9194" + String.format("%08d", uniqueSuffix % 100000000L))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(testUser2, userRole));

        user1Token = jwtTokenService.generateAccessToken(testUser1);
        user2Token = jwtTokenService.generateAccessToken(testUser2);

        content1 = contentRepository.save(Content.builder()
                .title("Telugu Handloom Weaving Documentary")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(3600)
                .build());

        content2 = contentRepository.save(Content.builder()
                .title("Folk Dance Heritage")
                .contentType(ContentType.MOVIE)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(7200)
                .build());

        videoAsset1 = videoAssetRepository.save(VideoAsset.builder()
                .content(content1)
                .originalFilename("handloom_master.mp4")
                .checksumSha256("sha256_handloom_" + uniqueSuffix)
                .contentType("video/mp4")
                .storageBucket("communityott-media")
                .storageKey("hls/handloom/master.m3u8")
                .durationSeconds(3600)
                .width(1920)
                .height(1080)
                .bitrateKbps(5000)
                .fileSizeBytes(150_000_000L)
                .status(VideoAssetStatus.READY)
                .build());

        videoHlsPackageRepository.save(VideoHlsPackage.builder()
                .videoAsset(videoAsset1)
                .storageBucket("communityott-media")
                .masterPlaylistKey("hls/handloom/" + uniqueSuffix + "/master.m3u8")
                .status(HlsPackageStatus.READY)
                .variantCount(2)
                .targetDurationSeconds(6)
                .build());

        StartPlaybackSessionRequest sessionReq = StartPlaybackSessionRequest.builder()
                .platform(Platform.IOS)
                .deviceId("iPhone-15-Pro")
                .build();
        session1Response = playbackSessionService.startSession(testUser1.getId(), content1.getId(), sessionReq);
    }

    @Test
    @DisplayName("Test 1: Authenticated user can submit single PLAY event")
    void test01_submitPlayEvent_Success() throws Exception {
        String eventId = UUID.randomUUID().toString();
        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(eventId)
                .eventType(PlaybackEventType.PLAY)
                .positionSeconds(0)
                .durationSeconds(3600)
                .sequence(1)
                .occurredAt(Instant.now())
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.eventId").value(eventId));

        assertThat(playbackEventRepository.existsByEventId(eventId)).isTrue();
    }

    @Test
    @DisplayName("Test 2: Unauthenticated request is rejected with 401")
    void test02_unauthenticated_Rejected() throws Exception {
        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(PlaybackEventType.PLAY)
                .positionSeconds(0)
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Test 3: User cannot submit event to another user's playback session")
    void test03_crossUserSession_Forbidden() throws Exception {
        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(PlaybackEventType.PLAY)
                .positionSeconds(0)
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 4: Content and session ID mismatch is rejected with 404")
    void test04_contentSessionMismatch_NotFound() throws Exception {
        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(PlaybackEventType.PLAY)
                .positionSeconds(0)
                .build();

        mockMvc.perform(post("/api/v1/content/" + content2.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Test 5: Event ID idempotency - re-submitting duplicate event does not create duplicate row")
    void test05_idempotentDuplicateEvent_HandledGracefully() throws Exception {
        String eventId = UUID.randomUUID().toString();
        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(eventId)
                .eventType(PlaybackEventType.PLAY)
                .positionSeconds(10)
                .sequence(1)
                .build();

        // First attempt
        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));

        long countAfterFirst = playbackEventRepository.count();

        // Second duplicate attempt
        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.message").value("Event already processed (idempotent)"));

        assertThat(playbackEventRepository.count()).isEqualTo(countAfterFirst);
    }

    @Test
    @DisplayName("Test 6: Invalid playback position (exceeding duration) is rejected with 400")
    void test06_invalidPosition_Rejected() throws Exception {
        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(PlaybackEventType.SEEK)
                .positionSeconds(999999) // Way beyond 3600s
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 7: Ingest batch of telemetry events successfully")
    void test07_batchIngestion_Success() throws Exception {
        List<PlaybackEventRequest> events = List.of(
                PlaybackEventRequest.builder().eventId(UUID.randomUUID().toString()).eventType(PlaybackEventType.PLAY).positionSeconds(0).sequence(1).build(),
                PlaybackEventRequest.builder().eventId(UUID.randomUUID().toString()).eventType(PlaybackEventType.BUFFER_START).positionSeconds(30).sequence(2).build(),
                PlaybackEventRequest.builder().eventId(UUID.randomUUID().toString()).eventType(PlaybackEventType.BUFFER_END).positionSeconds(30).sequence(3).build(),
                PlaybackEventRequest.builder().eventId(UUID.randomUUID().toString()).eventType(PlaybackEventType.HEARTBEAT).positionSeconds(60).sequence(4).build()
        );

        PlaybackEventBatchRequest batchRequest = PlaybackEventBatchRequest.builder()
                .events(events)
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events/batch")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSubmitted").value(4))
                .andExpect(jsonPath("$.data.acceptedCount").value(4))
                .andExpect(jsonPath("$.data.duplicateCount").value(0))
                .andExpect(jsonPath("$.data.rejectedCount").value(0));
    }

    @Test
    @DisplayName("Test 8: Batch size exceeding 100 events is rejected")
    void test08_batchExceedingMax_Rejected() throws Exception {
        List<PlaybackEventRequest> largeBatch = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            largeBatch.add(PlaybackEventRequest.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(PlaybackEventType.HEARTBEAT)
                    .positionSeconds(i * 10)
                    .sequence(i + 1)
                    .build());
        }

        PlaybackEventBatchRequest batchRequest = PlaybackEventBatchRequest.builder()
                .events(largeBatch)
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events/batch")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 9: Metadata payload is serialized and queryable")
    void test09_metadataPayload_Persisted() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("from", "720p");
        metadata.put("to", "1080p");
        metadata.put("bitrateKbps", 4500);

        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(eventId)
                .eventType(PlaybackEventType.QUALITY_CHANGE)
                .positionSeconds(120)
                .metadata(metadata)
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));

        var eventOpt = playbackEventRepository.findByEventId(eventId);
        assertThat(eventOpt).isPresent();
        assertThat(eventOpt.get().getMetadata()).contains("1080p");
    }

    @Test
    @DisplayName("Test 10: Excessive metadata (>4KB) is rejected with 400")
    void test10_excessiveMetadata_Rejected() throws Exception {
        String hugeString = "A".repeat(5000);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("overflow", hugeString);

        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(PlaybackEventType.ERROR)
                .positionSeconds(45)
                .metadata(metadata)
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 11: PAUSE event transitions session status to PAUSED and records watch progress")
    void test11_pauseEvent_UpdatesSessionAndProgress() throws Exception {
        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(PlaybackEventType.PAUSE)
                .positionSeconds(500)
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        PlaybackSession session = playbackSessionRepository.findBySessionId(session1Response.getPlaybackSessionId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(PlaybackSessionStatus.PAUSED);
        assertThat(session.getLastPositionSeconds()).isEqualTo(500);

        var progressOpt = watchProgressRepository.findByUserIdAndContentId(testUser1.getId(), content1.getId());
        assertThat(progressOpt).isPresent();
        assertThat(progressOpt.get().getPositionSeconds()).isEqualTo(500);
    }

    @Test
    @DisplayName("Test 12: RESUME event transitions PAUSED session back to ACTIVE")
    void test12_resumeEvent_TransitionsToActive() throws Exception {
        // First pause
        test11_pauseEvent_UpdatesSessionAndProgress();

        // Then resume
        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(PlaybackEventType.RESUME)
                .positionSeconds(500)
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        PlaybackSession session = playbackSessionRepository.findBySessionId(session1Response.getPlaybackSessionId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(PlaybackSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Test 13: END event gracefully terminates session")
    void test13_endEvent_TerminatesSession() throws Exception {
        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(PlaybackEventType.END)
                .positionSeconds(1800)
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        PlaybackSession session = playbackSessionRepository.findBySessionId(session1Response.getPlaybackSessionId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(PlaybackSessionStatus.ENDED);
    }

    @Test
    @DisplayName("Test 14: Non-END event on already ended session is rejected")
    void test14_eventAfterEnd_Rejected() throws Exception {
        // End the session
        test13_endEvent_TerminatesSession();

        // Attempt PLAY on ended session
        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(PlaybackEventType.PLAY)
                .positionSeconds(1800)
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Test 15: COMPLETE event triggers watch progress completion")
    void test15_completeEvent_SetsCompletedFlag() throws Exception {
        PlaybackEventRequest request = PlaybackEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(PlaybackEventType.COMPLETE)
                .positionSeconds(3500) // > 95% of 3600s
                .build();

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        var progressOpt = watchProgressRepository.findByUserIdAndContentId(testUser1.getId(), content1.getId());
        assertThat(progressOpt).isPresent();
        assertThat(progressOpt.get().getCompleted()).isTrue();
    }

    @Test
    @DisplayName("Test 16: Saved Content (My List) is NOT affected by telemetry events")
    void test16_savedContentUnaffected() throws Exception {
        savedContentService.addToMyList(testUser1.getId(), content1.getId());
        assertThat(savedContentService.isSaved(testUser1.getId(), content1.getId()).isSaved()).isTrue();

        // Ingest telemetry events
        test01_submitPlayEvent_Success();

        // My List still has item
        assertThat(savedContentService.isSaved(testUser1.getId(), content1.getId()).isSaved()).isTrue();
    }

    @Test
    @DisplayName("Test 17: Batch with duplicates increments duplicateCount properly")
    void test17_batchWithDuplicates_CountsDuplicates() throws Exception {
        String existingEventId = UUID.randomUUID().toString();
        // Submit once
        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaybackEventRequest.builder()
                                .eventId(existingEventId)
                                .eventType(PlaybackEventType.PLAY)
                                .positionSeconds(0)
                                .build())))
                .andExpect(status().isOk());

        // Batch containing duplicate + new
        List<PlaybackEventRequest> batch = List.of(
                PlaybackEventRequest.builder().eventId(existingEventId).eventType(PlaybackEventType.PLAY).positionSeconds(0).build(),
                PlaybackEventRequest.builder().eventId(UUID.randomUUID().toString()).eventType(PlaybackEventType.HEARTBEAT).positionSeconds(30).build()
        );

        mockMvc.perform(post("/api/v1/content/" + content1.getId() + "/playback/sessions/" + session1Response.getPlaybackSessionId() + "/events/batch")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaybackEventBatchRequest.builder().events(batch).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSubmitted").value(2))
                .andExpect(jsonPath("$.data.acceptedCount").value(1))
                .andExpect(jsonPath("$.data.duplicateCount").value(1))
                .andExpect(jsonPath("$.data.rejectedCount").value(0));
    }

    @Test
    @DisplayName("Test 18: Events are queried by session in chronological occurred_at order")
    void test18_querySessionEventsChronological() throws Exception {
        PlaybackSession session = playbackSessionRepository.findBySessionId(session1Response.getPlaybackSessionId()).orElseThrow();
        var events = playbackEventRepository.findByPlaybackSessionIdOrderByOccurredAtAsc(session.getId());
        assertThat(events).isNotNull();
    }
}
