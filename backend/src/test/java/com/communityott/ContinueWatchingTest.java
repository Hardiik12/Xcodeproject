package com.communityott;

import com.communityott.auth.entity.Platform;
import com.communityott.auth.security.JwtTokenService;
import com.communityott.content.delivery.DeliveryMode;
import com.communityott.content.delivery.MediaDeliveryProperties;
import com.communityott.content.entity.*;
import com.communityott.content.repository.*;
import com.communityott.playback.config.PlaybackProperties;
import com.communityott.playback.dto.PlaybackProgressRequest;
import com.communityott.playback.dto.StartPlaybackSessionRequest;
import com.communityott.playback.entity.WatchProgress;
import com.communityott.playback.repository.PlaybackSessionRepository;
import com.communityott.playback.repository.WatchProgressRepository;
import com.communityott.playback.service.PlaybackSessionService;
import com.communityott.playback.service.WatchProgressService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class ContinueWatchingTest {

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
    private WatchProgressService watchProgressService;

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

    private Content playableContent1;
    private Content playableContent2;
    private Content playableContent3;
    private VideoAsset readyAsset1;
    private VideoAsset readyAsset2;
    private VideoAsset readyAsset3;

    @BeforeEach
    void setUp() {
        deliveryProperties.setMode(DeliveryMode.LOCAL);
        deliveryProperties.getRateLimit().setEnabled(false);

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

        testUser1 = userRepository.save(User.builder()
                .email("cwuser1_" + uniqueSuffix + "@communityott.com")
                .displayName("CW User 1")
                .phone("+9191" + String.format("%08d", uniqueSuffix % 100000000L))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(testUser1, userRole));

        testUser2 = userRepository.save(User.builder()
                .email("cwuser2_" + uniqueSuffix + "@communityott.com")
                .displayName("CW User 2")
                .phone("+9192" + String.format("%08d", uniqueSuffix % 100000000L))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(testUser2, userRole));

        user1Token = jwtTokenService.generateAccessToken(testUser1);
        user2Token = jwtTokenService.generateAccessToken(testUser2);

        playableContent1 = createReadyContent("Documentary 1: Handloom Weavers " + uniqueSuffix, 3600);
        readyAsset1 = videoAssetRepository.findByContentIdOrderByCreatedAtDesc(playableContent1.getId()).get(0);

        playableContent2 = createReadyContent("Documentary 2: Folk Dances " + uniqueSuffix, 1800);
        readyAsset2 = videoAssetRepository.findByContentIdOrderByCreatedAtDesc(playableContent2.getId()).get(0);

        playableContent3 = createReadyContent("Documentary 3: Temple Architecture " + uniqueSuffix, 2400);
        readyAsset3 = videoAssetRepository.findByContentIdOrderByCreatedAtDesc(playableContent3.getId()).get(0);
    }

    private Content createReadyContent(String title, int duration) {
        Content content = contentRepository.save(Content.builder()
                .title(title)
                .subtitle("Culture series")
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
    @DisplayName("Test 01: Unauthenticated request to continue watching returns 401")
    void test01_unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/continue-watching"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Test 02: Empty continue watching returns empty list and not 404")
    void test02_empty_ReturnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("Test 03: In-progress content appears in continue watching")
    void test03_inProgressContent_Appears() throws Exception {
        // User 1 has 1200s / 3600s watch progress on Content 1
        watchProgressService.recordProgress(testUser1.getId(), playableContent1, readyAsset1, 1200, 3600);

        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].contentId").value(playableContent1.getId()))
                .andExpect(jsonPath("$.data.content[0].title").value(playableContent1.getTitle()))
                .andExpect(jsonPath("$.data.content[0].positionSeconds").value(1200))
                .andExpect(jsonPath("$.data.content[0].durationSeconds").value(3600))
                .andExpect(jsonPath("$.data.content[0].remainingSeconds").value(2400))
                .andExpect(jsonPath("$.data.content[0].completionPercentage").value(33.33333333333333))
                .andExpect(jsonPath("$.data.content[0].completed").value(false));
    }

    @Test
    @DisplayName("Test 04: Zero position content is excluded from continue watching")
    void test04_zeroPosition_Excluded() throws Exception {
        // Content with 0 position
        watchProgressRepository.save(WatchProgress.builder()
                .user(testUser1)
                .content(playableContent1)
                .videoAsset(readyAsset1)
                .positionSeconds(0)
                .durationSeconds(3600)
                .completionPercentage(0.0)
                .completed(false)
                .lastWatchedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("Test 05: Completed content (>= 95% threshold) is excluded from continue watching")
    void test05_completedContent_Excluded() throws Exception {
        // Record 96% progress on Content 1
        watchProgressService.recordProgress(testUser1.getId(), playableContent1, readyAsset1, 3500, 3600);

        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("Test 06: Non-published content (DRAFT or ARCHIVED) is excluded")
    void test06_nonPublishedContent_Excluded() throws Exception {
        Content draftContent = contentRepository.save(Content.builder()
                .title("Draft Documentary")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.DRAFT)
                .durationSeconds(3600)
                .build());

        VideoAsset draftAsset = videoAssetRepository.save(VideoAsset.builder()
                .content(draftContent)
                .originalFilename("draft.mp4")
                .checksumSha256("sha256_draft_" + System.nanoTime())
                .fileSizeBytes(1024L * 1024L)
                .contentType("video/mp4")
                .storageBucket("communityott-media")
                .storageKey("sources/draft_" + System.nanoTime() + ".mp4")
                .durationSeconds(3600)
                .status(VideoAssetStatus.READY)
                .build());

        videoHlsPackageRepository.save(VideoHlsPackage.builder()
                .videoAsset(draftAsset)
                .storageBucket("communityott-media")
                .masterPlaylistKey("hls/draft/" + System.nanoTime() + "/master.m3u8")
                .status(HlsPackageStatus.READY)
                .build());

        watchProgressService.recordProgress(testUser1.getId(), draftContent, draftAsset, 600, 3600);

        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("Test 07: Content without ready HLS package is excluded")
    void test07_unreadyHlsPackage_Excluded() throws Exception {
        Content unreadyContent = contentRepository.save(Content.builder()
                .title("Processing Video Content")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(3600)
                .build());

        VideoAsset unreadyAsset = videoAssetRepository.save(VideoAsset.builder()
                .content(unreadyContent)
                .originalFilename("unready.mp4")
                .checksumSha256("sha256_unready_" + System.nanoTime())
                .fileSizeBytes(1024L * 1024L)
                .contentType("video/mp4")
                .storageBucket("communityott-media")
                .storageKey("sources/unready_" + System.nanoTime() + ".mp4")
                .durationSeconds(3600)
                .status(VideoAssetStatus.READY)
                .build());

        videoHlsPackageRepository.save(VideoHlsPackage.builder()
                .videoAsset(unreadyAsset)
                .storageBucket("communityott-media")
                .masterPlaylistKey("hls/unready/" + System.nanoTime() + "/master.m3u8")
                .status(HlsPackageStatus.PROCESSING) // Not ready!
                .build());

        watchProgressService.recordProgress(testUser1.getId(), unreadyContent, unreadyAsset, 600, 3600);

        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("Test 08: Ordering is newest viewing activity first")
    void test08_ordering_NewestFirst() throws Exception {
        // User 1 watches Content 1 at 300s
        watchProgressService.recordProgress(testUser1.getId(), playableContent1, readyAsset1, 300, 3600);
        Thread.sleep(50);

        // User 1 watches Content 2 at 400s (now Content 2 is newest)
        watchProgressService.recordProgress(testUser1.getId(), playableContent2, readyAsset2, 400, 1800);
        Thread.sleep(50);

        MvcResult r1 = mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list1 = objectMapper.readTree(r1.getResponse().getContentAsString()).path("data").path("content");
        assertThat(list1.get(0).path("contentId").asLong()).isEqualTo(playableContent2.getId());
        assertThat(list1.get(1).path("contentId").asLong()).isEqualTo(playableContent1.getId());

        // User 1 resumes Content 1 and watches to 600s
        Thread.sleep(50);
        watchProgressService.recordProgress(testUser1.getId(), playableContent1, readyAsset1, 600, 3600);

        MvcResult r2 = mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list2 = objectMapper.readTree(r2.getResponse().getContentAsString()).path("data").path("content");
        assertThat(list2.get(0).path("contentId").asLong()).isEqualTo(playableContent1.getId());
        assertThat(list2.get(1).path("contentId").asLong()).isEqualTo(playableContent2.getId());
    }

    @Test
    @DisplayName("Test 09: User A cannot see User B's continue watching items")
    void test09_userIsolation() throws Exception {
        watchProgressService.recordProgress(testUser1.getId(), playableContent1, readyAsset1, 500, 3600);
        watchProgressService.recordProgress(testUser2.getId(), playableContent2, readyAsset2, 400, 1800);

        // User 1 gets only Content 1
        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].contentId").value(playableContent1.getId()));

        // User 2 gets only Content 2
        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].contentId").value(playableContent2.getId()));
    }

    @Test
    @DisplayName("Test 10: Pagination with max page size bounding")
    void test10_pagination_AndMaxPageSize() throws Exception {
        watchProgressService.recordProgress(testUser1.getId(), playableContent1, readyAsset1, 500, 3600);
        watchProgressService.recordProgress(testUser1.getId(), playableContent2, readyAsset2, 400, 1800);
        watchProgressService.recordProgress(testUser1.getId(), playableContent3, readyAsset3, 600, 2400);

        // Page 0 size 2
        mockMvc.perform(get("/api/v1/users/me/continue-watching?page=0&size=2")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        // Size 1000 -> bounded to 50 max
        mockMvc.perform(get("/api/v1/users/me/continue-watching?page=0&size=1000")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    @DisplayName("Test 11: Rewatch behavior - restarting after completion re-enters Continue Watching upon new progress")
    void test11_rewatchBehavior() throws Exception {
        // User completes Content 1 (96% progress) -> excluded from Continue Watching
        watchProgressService.recordProgress(testUser1.getId(), playableContent1, readyAsset1, 3500, 3600);

        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        // User starts rewatching from 120s -> re-enters Continue Watching
        watchProgressService.recordProgress(testUser1.getId(), playableContent1, readyAsset1, 120, 3600);

        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].contentId").value(playableContent1.getId()))
                .andExpect(jsonPath("$.data.content[0].positionSeconds").value(120));
    }

    @Test
    @DisplayName("Test 12: Remaining seconds calculation is never negative")
    void test12_remainingSeconds_nonNegative() throws Exception {
        watchProgressService.recordProgress(testUser1.getId(), playableContent1, readyAsset1, 3000, 3600);

        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].remainingSeconds").value(600));
    }

    @Test
    @DisplayName("Test 13: Content missing READY VideoAsset is excluded")
    void test13_missingReadyVideoAsset_Excluded() throws Exception {
        Content assetlessContent = contentRepository.save(Content.builder()
                .title("Content With Processing Asset")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(3600)
                .build());

        VideoAsset processingAsset = videoAssetRepository.save(VideoAsset.builder()
                .content(assetlessContent)
                .originalFilename("proc.mp4")
                .checksumSha256("sha256_proc_" + System.nanoTime())
                .fileSizeBytes(1024L * 1024L)
                .contentType("video/mp4")
                .storageBucket("communityott-media")
                .storageKey("sources/proc_" + System.nanoTime() + ".mp4")
                .durationSeconds(3600)
                .status(VideoAssetStatus.PROCESSING) // Not ready!
                .build());

        watchProgressService.recordProgress(testUser1.getId(), assetlessContent, processingAsset, 500, 3600);

        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("Test 14: Archived content is excluded from continue watching")
    void test14_archivedContent_Excluded() throws Exception {
        playableContent1.setStatus(ContentStatus.ARCHIVED);
        contentRepository.save(playableContent1);

        watchProgressService.recordProgress(testUser1.getId(), playableContent1, readyAsset1, 600, 3600);

        mockMvc.perform(get("/api/v1/users/me/continue-watching")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }
}
