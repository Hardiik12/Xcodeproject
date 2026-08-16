package com.communityott;

import com.communityott.auth.security.JwtTokenService;
import com.communityott.common.exception.VideoProcessingException;
import com.communityott.content.dto.VideoHlsPackageResponse;
import com.communityott.content.dto.VideoProcessingJobResponse;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.content.entity.HlsPackageStatus;
import com.communityott.content.entity.HlsVariantStatus;
import com.communityott.content.entity.ProcessingJobStatus;
import com.communityott.content.entity.ProcessingJobType;
import com.communityott.content.entity.RenditionStatus;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.entity.VideoHlsPackage;
import com.communityott.content.entity.VideoHlsVariant;
import com.communityott.content.entity.VideoProcessingJob;
import com.communityott.content.entity.VideoRendition;
import com.communityott.content.processing.DefaultFFmpegHlsPackagingService;
import com.communityott.content.processing.DefaultVideoProcessor;
import com.communityott.content.processing.FFmpegHlsPackagingService;
import com.communityott.content.processing.FFmpegProperties;
import com.communityott.content.processing.FFmpegTranscodeService;
import com.communityott.content.processing.FFprobeService;
import com.communityott.content.processing.HlsManifestGenerator;
import com.communityott.content.processing.HlsPackageValidator;
import com.communityott.content.processing.HlsPackagingResult;
import com.communityott.content.processing.ProcessExecutionResult;
import com.communityott.content.processing.ProcessRunner;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.repository.VideoHlsPackageRepository;
import com.communityott.content.repository.VideoHlsVariantRepository;
import com.communityott.content.repository.VideoProcessingJobRepository;
import com.communityott.content.repository.VideoRenditionRepository;
import com.communityott.content.service.VideoProcessingService;
import com.communityott.content.storage.ObjectStorageService;
import com.communityott.content.storage.StorageKeyGenerator;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VideoHlsPackagingTest {

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
    private VideoProcessingJobRepository jobRepository;

    @Autowired
    private VideoRenditionRepository videoRenditionRepository;

    @Autowired
    private VideoHlsPackageRepository videoHlsPackageRepository;

    @Autowired
    private VideoHlsVariantRepository videoHlsVariantRepository;

    @Autowired
    private VideoProcessingService videoProcessingService;

    @Autowired
    private HlsManifestGenerator hlsManifestGenerator;

    @Autowired
    private HlsPackageValidator hlsPackageValidator;

    @Autowired
    private StorageKeyGenerator storageKeyGenerator;

    @Autowired
    private FFmpegProperties ffmpegProperties;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private MinioClient minioClient;

    @MockBean
    private ObjectStorageService objectStorageService;

    @MockBean
    private FFprobeService ffprobeService;

    @MockBean
    private FFmpegTranscodeService ffmpegTranscodeService;

    @MockBean
    private FFmpegHlsPackagingService ffmpegHlsPackagingService;

    @MockBean
    private ProcessRunner processRunner;

    @Autowired
    private DefaultVideoProcessor videoProcessor;

    private User adminUser;
    private User regularUser;
    private String adminToken;
    private String regularUserToken;

    private Content testContent;
    private VideoAsset testVideoAsset;
    private VideoRendition rendition1080p;
    private VideoRendition rendition720p;

    @BeforeEach
    void setUp() {
        Role contentManagerRole = roleRepository.findByName("CONTENT_MANAGER").orElseThrow();
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        adminUser = userRepository.save(User.builder()
                .email("hls_admin_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("HLS Admin")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(adminUser, contentManagerRole));
        adminToken = jwtTokenService.generateAccessToken(adminUser);

        regularUser = userRepository.save(User.builder()
                .email("hls_user_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("HLS Viewer")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(regularUser, userRole));
        regularUserToken = jwtTokenService.generateAccessToken(regularUser);

        testContent = contentRepository.save(Content.builder()
                .title("Adaptive HLS Test Stream")
                .contentType(ContentType.MOVIE)
                .status(ContentStatus.DRAFT)
                .durationSeconds(120)
                .createdBy(adminUser.getId())
                .build());

        testVideoAsset = videoAssetRepository.save(VideoAsset.builder()
                .content(testContent)
                .originalFilename("hls_master.mp4")
                .fileSizeBytes(104857600L)
                .contentType("video/mp4")
                .checksumSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .storageBucket("communityott-videos")
                .storageKey("sources/" + testContent.getId() + "/e3b0c442_hls_master.mp4")
                .status(VideoAssetStatus.UPLOADED)
                .createdBy(adminUser.getId())
                .build());

        rendition1080p = videoRenditionRepository.save(VideoRendition.builder()
                .videoAsset(testVideoAsset)
                .resolution("1080p")
                .width(1920)
                .height(1080)
                .bitrateKbps(5000)
                .audioBitrateKbps(192)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate(24.0)
                .fileSizeBytes(50000000L)
                .storageBucket("communityott-videos")
                .storageKey("renditions/asset_" + testVideoAsset.getId() + "/1080p.mp4")
                .checksumSha256("1080p_checksum")
                .status(RenditionStatus.READY)
                .durationSeconds(120)
                .build());

        rendition720p = videoRenditionRepository.save(VideoRendition.builder()
                .videoAsset(testVideoAsset)
                .resolution("720p")
                .width(1280)
                .height(720)
                .bitrateKbps(2800)
                .audioBitrateKbps(128)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate(24.0)
                .fileSizeBytes(28000000L)
                .storageBucket("communityott-videos")
                .storageKey("renditions/asset_" + testVideoAsset.getId() + "/720p.mp4")
                .checksumSha256("720p_checksum")
                .status(RenditionStatus.READY)
                .durationSeconds(120)
                .build());

        when(objectStorageService.getBucketName()).thenReturn("communityott-videos");
        when(objectStorageService.doesObjectExist(anyString(), anyString())).thenReturn(true);
        when(objectStorageService.getObject(anyString(), anyString()))
                .thenReturn(new ByteArrayInputStream("fake-mp4-rendition-bytes".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @Order(1)
    @DisplayName("1. HLS Job Creation: Enqueues PACKAGE_HLS job in QUEUED state")
    void test01_hlsJobCreationInitialState() {
        VideoProcessingJobResponse job = videoProcessingService.createAndEnqueueHlsPackageJob(testVideoAsset.getId(), adminUser.getId());
        assertNotNull(job.getId());
        assertEquals(ProcessingJobType.PACKAGE_HLS, job.getJobType());
        assertEquals(ProcessingJobStatus.QUEUED, job.getStatus());
        assertEquals(0, job.getAttemptCount());
    }

    @Test
    @Order(2)
    @DisplayName("2. Master Playlist Generator: Generates standard HLS VOD master manifest with correct attributes")
    void test02_masterPlaylistGeneration() {
        VideoHlsVariant v1080 = VideoHlsVariant.builder()
                .resolution("1080p")
                .width(1920)
                .height(1080)
                .bandwidthBps(5970800L)
                .averageBandwidthBps(5192000L)
                .codecs("avc1.640028,mp4a.40.2")
                .frameRate(24.0)
                .build();

        VideoHlsVariant v720 = VideoHlsVariant.builder()
                .resolution("720p")
                .width(1280)
                .height(720)
                .bandwidthBps(3367200L)
                .averageBandwidthBps(2928000L)
                .codecs("avc1.4d401f,mp4a.40.2")
                .frameRate(24.0)
                .build();

        String masterManifest = hlsManifestGenerator.generateMasterPlaylist(List.of(v1080, v720));

        assertNotNull(masterManifest);
        assertTrue(masterManifest.startsWith("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-INDEPENDENT-SEGMENTS"));
        assertTrue(masterManifest.contains("#EXT-X-STREAM-INF:BANDWIDTH=5970800,AVERAGE-BANDWIDTH=5192000,RESOLUTION=1920x1080,CODECS=\"avc1.640028,mp4a.40.2\",FRAME-RATE=24.000\n1080p/index.m3u8"));
        assertTrue(masterManifest.contains("#EXT-X-STREAM-INF:BANDWIDTH=3367200,AVERAGE-BANDWIDTH=2928000,RESOLUTION=1280x720,CODECS=\"avc1.4d401f,mp4a.40.2\",FRAME-RATE=24.000\n720p/index.m3u8"));
        assertFalse(masterManifest.contains(".."));
    }

    @Test
    @Order(3)
    @DisplayName("3. Master Playlist Generator: Rejects empty variants list")
    void test03_masterPlaylistRejectsEmptyVariants() {
        assertThrows(VideoProcessingException.class, () -> hlsManifestGenerator.generateMasterPlaylist(List.of()));
    }

    @Test
    @Order(4)
    @DisplayName("4. Master Playlist Security: Validates and prevents path traversal or absolute URIs")
    void test04_masterPlaylistSecurityViolation() {
        VideoHlsVariant maliciousVariant = VideoHlsVariant.builder()
                .resolution("../etc/passwd")
                .width(1920)
                .height(1080)
                .bandwidthBps(5000000L)
                .build();

        assertThrows(VideoProcessingException.class, () -> hlsManifestGenerator.generateMasterPlaylist(List.of(maliciousVariant)));
    }

    @Test
    @Order(5)
    @DisplayName("5. HLS Package Validator: Successfully validates valid fMP4 variant package")
    void test05_hlsPackageValidatorSuccess(@TempDir File tempDir) throws Exception {
        File playlist = new File(tempDir, "index.m3u8");
        File init = new File(tempDir, "init.mp4");
        File seg0 = new File(tempDir, "segment_00000.m4s");
        File seg1 = new File(tempDir, "segment_00001.m4s");

        String validVODPlaylist = """
                #EXTM3U
                #EXT-X-VERSION:7
                #EXT-X-TARGETDURATION:2
                #EXT-X-MEDIA-SEQUENCE:0
                #EXT-X-MAP:URI="init.mp4"
                #EXTINF:2.000,
                segment_00000.m4s
                #EXTINF:2.000,
                segment_00001.m4s
                #EXT-X-ENDLIST
                """;

        Files.writeString(playlist.toPath(), validVODPlaylist);
        Files.write(init.toPath(), new byte[]{1, 2, 3});
        Files.write(seg0.toPath(), new byte[]{4, 5, 6});
        Files.write(seg1.toPath(), new byte[]{7, 8, 9});

        hlsPackageValidator.validateVariantPackage(tempDir, playlist, init, List.of(seg0, seg1));
    }

    @Test
    @Order(6)
    @DisplayName("6. HLS Package Validator: Fails validation on missing initialization segment")
    void test06_hlsPackageValidatorMissingInit(@TempDir File tempDir) throws Exception {
        File playlist = new File(tempDir, "index.m3u8");
        File nonExistentInit = new File(tempDir, "init.mp4");
        File seg0 = new File(tempDir, "segment_00000.m4s");

        Files.writeString(playlist.toPath(), "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-ENDLIST");
        Files.write(seg0.toPath(), new byte[]{1, 2, 3});

        assertThrows(VideoProcessingException.class, () ->
                hlsPackageValidator.validateVariantPackage(tempDir, playlist, nonExistentInit, List.of(seg0))
        );
    }

    @Test
    @Order(7)
    @DisplayName("7. HLS Package Validator: Fails validation if #EXT-X-ENDLIST is missing (VOD required)")
    void test07_hlsPackageValidatorMissingEndlist(@TempDir File tempDir) throws Exception {
        File playlist = new File(tempDir, "index.m3u8");
        File init = new File(tempDir, "init.mp4");
        File seg0 = new File(tempDir, "segment_00000.m4s");

        String livePlaylistMissingEndlist = """
                #EXTM3U
                #EXT-X-VERSION:7
                #EXT-X-TARGETDURATION:2
                #EXT-X-MAP:URI="init.mp4"
                #EXTINF:2.000,
                segment_00000.m4s
                """;

        Files.writeString(playlist.toPath(), livePlaylistMissingEndlist);
        Files.write(init.toPath(), new byte[]{1, 2, 3});
        Files.write(seg0.toPath(), new byte[]{4, 5, 6});

        assertThrows(VideoProcessingException.class, () ->
                hlsPackageValidator.validateVariantPackage(tempDir, playlist, init, List.of(seg0))
        );
    }

    @Test
    @Order(8)
    @DisplayName("8. FFmpeg HLS Packaging Service: Stream copy command and fMP4 packaging execution")
    void test08_ffmpegHlsPackagingServiceCommand(@TempDir File tempDir) throws Exception {
        File sourceMp4 = new File(tempDir, "rendition_720p.mp4");
        Files.write(sourceMp4.toPath(), new byte[]{1, 2, 3, 4});

        File variantOutputDir = new File(tempDir, "720p");
        variantOutputDir.mkdirs();

        when(processRunner.execute(anyList(), anyInt())).thenAnswer(invocation -> {
            File playlist = new File(variantOutputDir, "index.m3u8");
            File init = new File(variantOutputDir, "init.mp4");
            File seg0 = new File(variantOutputDir, "segment_00000.m4s");
            Files.writeString(playlist.toPath(), "#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:2\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:2.000,\nsegment_00000.m4s\n#EXT-X-ENDLIST\n");
            Files.write(init.toPath(), new byte[]{1, 2, 3});
            Files.write(seg0.toPath(), new byte[]{4, 5, 6});
            return new ProcessExecutionResult(0, "ffmpeg output", "", false, 150L);
        });

        DefaultFFmpegHlsPackagingService packagingService = new DefaultFFmpegHlsPackagingService(processRunner, ffmpegProperties);
        HlsPackagingResult result = packagingService.packageToHls(sourceMp4, variantOutputDir, rendition720p, 2);

        assertNotNull(result);
        assertEquals("720p", result.getResolution());
        assertEquals(1, result.getSegmentCount());
        assertEquals(2, result.getTargetDurationSeconds());
        assertEquals(1280, result.getWidth());
        assertEquals(720, result.getHeight());
        assertTrue(result.getPlaylistFile().exists());
        assertTrue(result.getInitSegmentFile().exists());
    }

    @Test
    @Order(9)
    @DisplayName("9. E2E HLS Packaging Pipeline: Full worker execution with MinIO upload & JPA entity persistence")
    void test09_e2eHlsPackagingPipeline() throws Exception {
        when(ffmpegHlsPackagingService.packageToHls(any(), any(), any(), anyInt())).thenAnswer(invocation -> {
            File targetDir = invocation.getArgument(1);
            if (!targetDir.exists()) targetDir.mkdirs();
            VideoRendition rendition = invocation.getArgument(2);

            File playlist = new File(targetDir, "index.m3u8");
            File init = new File(targetDir, "init.mp4");
            File seg0 = new File(targetDir, "segment_00000.m4s");
            Files.writeString(playlist.toPath(), "#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:2\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:2.000,\nsegment_00000.m4s\n#EXT-X-ENDLIST\n");
            Files.write(init.toPath(), new byte[]{1, 2, 3});
            Files.write(seg0.toPath(), new byte[]{4, 5, 6});

            return HlsPackagingResult.builder()
                    .resolution(rendition.getResolution())
                    .playlistFile(playlist)
                    .initSegmentFile(init)
                    .mediaSegmentFiles(List.of(seg0))
                    .segmentCount(1)
                    .targetDurationSeconds(2)
                    .bandwidthBps(((long) rendition.getBitrateKbps() + rendition.getAudioBitrateKbps()) * 1150L)
                    .averageBandwidthBps(((long) rendition.getBitrateKbps() + rendition.getAudioBitrateKbps()) * 1000L)
                    .codecs("avc1.640028,mp4a.40.2")
                    .width(rendition.getWidth())
                    .height(rendition.getHeight())
                    .frameRate(24.0)
                    .build();
        });

        VideoProcessingJob job = jobRepository.save(VideoProcessingJob.builder()
                .videoAsset(testVideoAsset)
                .jobType(ProcessingJobType.PACKAGE_HLS)
                .status(ProcessingJobStatus.QUEUED)
                .build());

        videoProcessor.process(job.getId());

        VideoProcessingJob updatedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(ProcessingJobStatus.COMPLETED, updatedJob.getStatus());

        VideoHlsPackage hlsPackage = videoHlsPackageRepository.findByVideoAssetId(testVideoAsset.getId()).orElseThrow();
        assertEquals(HlsPackageStatus.READY, hlsPackage.getStatus());
        assertEquals("hls/" + testContent.getId() + "/" + testVideoAsset.getId() + "/master.m3u8", hlsPackage.getMasterPlaylistKey());
        assertEquals(2, hlsPackage.getVariantCount());

        List<VideoHlsVariant> variants = videoHlsVariantRepository.findByHlsPackageIdOrderByHeightDesc(hlsPackage.getId());
        assertEquals(2, variants.size());

        VideoAsset updatedAsset = videoAssetRepository.findById(testVideoAsset.getId()).orElseThrow();
        assertEquals(VideoAssetStatus.READY, updatedAsset.getStatus());
    }

    @Test
    @Order(10)
    @DisplayName("10. StorageKeyGenerator: Generates deterministic HLS keys matching CDN conventions")
    void test10_storageKeyGeneratorHlsKeys() {
        String masterKey = storageKeyGenerator.generateHlsMasterKey(101L, 202L);
        assertEquals("hls/101/202/master.m3u8", masterKey);

        String variantKey = storageKeyGenerator.generateHlsVariantPlaylistKey(101L, 202L, "1080p");
        assertEquals("hls/101/202/1080p/index.m3u8", variantKey);

        String initKey = storageKeyGenerator.generateHlsInitSegmentKey(101L, 202L, "720p");
        assertEquals("hls/101/202/720p/init.mp4", initKey);

        String segmentKey = storageKeyGenerator.generateHlsMediaSegmentKey(101L, 202L, "480p", "segment_00001.m4s");
        assertEquals("hls/101/202/480p/segment_00001.m4s", segmentKey);
    }

    @Test
    @Order(11)
    @DisplayName("11. REST API: POST /api/v1/admin/videos/{videoId}/hls/package -> 202 Accepted (Admin)")
    void test11_enqueueHlsPackagingEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/admin/videos/" + testVideoAsset.getId() + "/hls/package")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("QUEUED")))
                .andExpect(jsonPath("$.data.jobType", is("PACKAGE_HLS")));
    }

    @Test
    @Order(12)
    @DisplayName("12. REST API: GET /api/v1/admin/videos/{videoId}/hls -> 200 OK (Admin)")
    void test12_getHlsPackageEndpoint() throws Exception {
        VideoHlsPackage pkg = videoHlsPackageRepository.save(VideoHlsPackage.builder()
                .videoAsset(testVideoAsset)
                .masterPlaylistKey("hls/" + testContent.getId() + "/" + testVideoAsset.getId() + "/master.m3u8")
                .storageBucket("communityott-videos")
                .status(HlsPackageStatus.READY)
                .variantCount(2)
                .targetDurationSeconds(2)
                .build());

        videoHlsVariantRepository.save(VideoHlsVariant.builder()
                .hlsPackage(pkg)
                .videoRendition(rendition1080p)
                .resolution("1080p")
                .width(1920)
                .height(1080)
                .playlistKey("hls/" + testContent.getId() + "/" + testVideoAsset.getId() + "/1080p/index.m3u8")
                .initSegmentKey("hls/" + testContent.getId() + "/" + testVideoAsset.getId() + "/1080p/init.mp4")
                .segmentCount(60)
                .targetDurationSeconds(2)
                .bandwidthBps(5970800L)
                .averageBandwidthBps(5192000L)
                .codecs("avc1.640028,mp4a.40.2")
                .frameRate(24.0)
                .status(HlsVariantStatus.READY)
                .build());

        mockMvc.perform(get("/api/v1/admin/videos/" + testVideoAsset.getId() + "/hls")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.masterPlaylistKey", is("hls/" + testContent.getId() + "/" + testVideoAsset.getId() + "/master.m3u8")))
                .andExpect(jsonPath("$.data.variantCount", is(2)))
                .andExpect(jsonPath("$.data.variants", hasSize(1)))
                .andExpect(jsonPath("$.data.variants[0].resolution", is("1080p")));
    }

    @Test
    @Order(13)
    @DisplayName("13. RBAC Security: Regular user without VIDEO_PROCESS permission is rejected with 403 Forbidden")
    void test13_regularUserRejectedWithForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/videos/" + testVideoAsset.getId() + "/hls/package")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }
}
