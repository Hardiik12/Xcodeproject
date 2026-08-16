package com.communityott;

import com.communityott.auth.security.JwtTokenService;
import com.communityott.content.dto.VideoProcessingJobResponse;
import com.communityott.content.dto.VideoRenditionResponse;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.content.entity.ProcessingJobStatus;
import com.communityott.content.entity.ProcessingJobType;
import com.communityott.content.entity.RenditionStatus;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.entity.VideoProcessingJob;
import com.communityott.content.entity.VideoRendition;
import com.communityott.content.entity.VideoResolution;
import com.communityott.content.processing.DefaultFFmpegTranscodeService;
import com.communityott.content.processing.DefaultVideoProcessor;
import com.communityott.content.processing.FFmpegHlsPackagingService;
import com.communityott.content.processing.FFmpegProperties;
import com.communityott.content.processing.FFmpegTranscodeService;
import com.communityott.content.processing.FFprobeService;
import com.communityott.content.processing.MediaProbeResult;
import com.communityott.content.processing.ProcessExecutionResult;
import com.communityott.content.processing.ProcessRunner;
import com.communityott.content.processing.TranscodeProfile;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.repository.VideoProcessingJobRepository;
import com.communityott.content.repository.VideoRenditionRepository;
import com.communityott.content.service.VideoProcessingService;
import com.communityott.content.storage.ObjectStorageService;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
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
public class VideoTranscodingPipelineTest {

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
    private VideoProcessingJobRepository jobRepository;

    @Autowired
    private VideoRenditionRepository videoRenditionRepository;

    @Autowired
    private VideoProcessingService videoProcessingService;

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
    private DefaultVideoProcessor defaultVideoProcessor;

    private User contentManager;
    private User regularUser;
    private Content testContent;
    private VideoAsset testVideoAsset;

    @BeforeEach
    void setUp() {
        Role contentManagerRole = roleRepository.findByName("CONTENT_MANAGER")
                .orElseThrow(() -> new IllegalStateException("CONTENT_MANAGER role not found"));
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("USER role not found"));

        contentManager = userRepository.save(User.builder()
                .email("transcoder.manager." + UUID.randomUUID() + "@communityott.org")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(contentManager, contentManagerRole));

        regularUser = userRepository.save(User.builder()
                .email("regular.user." + UUID.randomUUID() + "@communityott.org")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(regularUser, userRole));

        testContent = contentRepository.save(Content.builder()
                .title("Transcoding Master Test")
                .contentType(ContentType.MOVIE)
                .status(ContentStatus.UPLOADING)
                .build());

        testVideoAsset = videoAssetRepository.save(VideoAsset.builder()
                .content(testContent)
                .originalFilename("master_1080p_source.mp4")
                .fileSizeBytes(150_000_000L)
                .contentType("video/mp4")
                .checksumSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .storageBucket("communityott-videos")
                .storageKey("source/2026/08/master_1080p_source.mp4")
                .status(VideoAssetStatus.UPLOADED)
                .build());

        when(objectStorageService.getObject(anyString(), anyString()))
                .thenReturn(new ByteArrayInputStream("mock-video-binary-content".getBytes(StandardCharsets.UTF_8)));

        when(objectStorageService.uploadObject(anyString(), anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenReturn("mock-etag");
    }

    private String getBearerToken(User user) {
        return "Bearer " + jwtTokenService.generateAccessToken(user);
    }

    @Test
    @Order(1)
    @DisplayName("1. Resolution Ladder: Prevents upscaling and provides appropriate renditions")
    void test01_resolutionLadderSelection() {
        // 1080p source -> 5 renditions
        List<VideoResolution> ladder1080 = VideoResolution.getLadderForSource(1080);
        assertEquals(5, ladder1080.size());
        assertEquals(VideoResolution.RES_1080P, ladder1080.get(0));
        assertEquals(VideoResolution.RES_144P, ladder1080.get(4));

        // 720p source -> 4 renditions (no 1080p)
        List<VideoResolution> ladder720 = VideoResolution.getLadderForSource(720);
        assertEquals(4, ladder720.size());
        assertFalse(ladder720.contains(VideoResolution.RES_1080P));
        assertEquals(VideoResolution.RES_720P, ladder720.get(0));

        // 480p source -> 3 renditions (480p, 360p, 144p)
        List<VideoResolution> ladder480 = VideoResolution.getLadderForSource(480);
        assertEquals(3, ladder480.size());
        assertEquals(VideoResolution.RES_480P, ladder480.get(0));

        // 144p source -> 1 rendition (144p)
        List<VideoResolution> ladder144 = VideoResolution.getLadderForSource(144);
        assertEquals(1, ladder144.size());
        assertEquals(VideoResolution.RES_144P, ladder144.get(0));

        // Unknown low height e.g. 100p -> fallback includes at least 144p
        List<VideoResolution> ladder100 = VideoResolution.getLadderForSource(100);
        assertEquals(1, ladder100.size());
        assertEquals(VideoResolution.RES_144P, ladder100.get(0));
    }

    @Test
    @Order(2)
    @DisplayName("2. DefaultFFmpegTranscodeService: Builds safe FFmpeg CLI command with scale filter and faststart")
    void test02_ffmpegCommandGeneration() {
        ProcessRunner mockRunner = Mockito.mock(ProcessRunner.class);
        when(mockRunner.execute(anyList(), anyInt()))
                .thenReturn(new ProcessExecutionResult(0, "ffmpeg output", "", false, 1500));

        DefaultFFmpegTranscodeService transcodeService = new DefaultFFmpegTranscodeService(mockRunner, ffmpegProperties);

        File source = new File(System.getProperty("java.io.tmpdir"), "test_source.mp4");
        File target = new File(System.getProperty("java.io.tmpdir"), "test_target_720p.mp4");
        try {
            source.createNewFile();
            try (FileOutputStream fos = new FileOutputStream(source)) {
                fos.write(new byte[]{1, 2, 3});
            }

            TranscodeProfile profile = TranscodeProfile.fromResolution(VideoResolution.RES_720P);

            // Create target file before runner returns
            target.createNewFile();
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(new byte[]{4, 5, 6});
            }

            boolean success = transcodeService.transcode(source, target, profile);
            assertTrue(success);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
            verify(mockRunner).execute(commandCaptor.capture(), eq(ffmpegProperties.getTimeoutSeconds()));

            List<String> command = commandCaptor.getValue();
            assertTrue(command.contains("-i"));
            assertTrue(command.contains("-vf"));
            assertTrue(command.contains("scale=-2:720"));
            assertTrue(command.contains("-c:v"));
            assertTrue(command.contains("libx264"));
            assertTrue(command.contains("-c:a"));
            assertTrue(command.contains("aac"));
            assertTrue(command.contains("+faststart"));

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            source.delete();
            target.delete();
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. End-to-End Transcoding Pipeline: Generates multi-resolution ladder, uploads to MinIO, and records VideoRenditions")
    void test03_e2eTranscodingPipeline() {
        when(ffprobeService.probe(any(File.class))).thenReturn(MediaProbeResult.builder()
                .validMedia(true)
                .durationSeconds(180)
                .width(1920)
                .height(1080)
                .bitrateKbps(5000)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate("24/1")
                .rawJson("{\"streams\":[]}")
                .build());

        when(ffmpegTranscodeService.transcode(any(File.class), any(File.class), any(TranscodeProfile.class)))
                .thenAnswer(invocation -> {
                    File target = invocation.getArgument(1);
                    if (target.getParentFile() != null) target.getParentFile().mkdirs();
                    target.createNewFile();
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        fos.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
                    }
                    return true;
                });

        when(ffmpegHlsPackagingService.packageToHls(any(), any(), any(), anyInt())).thenAnswer(invocation -> {
            File targetDir = invocation.getArgument(1);
            if (!targetDir.exists()) targetDir.mkdirs();
            File playlist = new File(targetDir, "index.m3u8");
            File init = new File(targetDir, "init.mp4");
            File seg0 = new File(targetDir, "segment_00000.m4s");
            java.nio.file.Files.writeString(playlist.toPath(), "#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:2\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:2.000,\nsegment_00000.m4s\n#EXT-X-ENDLIST\n");
            java.nio.file.Files.write(init.toPath(), new byte[]{1, 2, 3});
            java.nio.file.Files.write(seg0.toPath(), new byte[]{4, 5, 6});
            return com.communityott.content.processing.HlsPackagingResult.builder()
                    .resolution("1080p")
                    .playlistFile(playlist)
                    .initSegmentFile(init)
                    .mediaSegmentFiles(List.of(seg0))
                    .segmentCount(1)
                    .targetDurationSeconds(2)
                    .bandwidthBps(5000000L)
                    .averageBandwidthBps(4500000L)
                    .codecs("avc1.640028,mp4a.40.2")
                    .width(1920)
                    .height(1080)
                    .frameRate(24.0)
                    .build();
        });

        VideoProcessingJob job = jobRepository.save(VideoProcessingJob.builder()
                .videoAsset(testVideoAsset)
                .jobType(ProcessingJobType.PROBE)
                .status(ProcessingJobStatus.QUEUED)
                .build());

        defaultVideoProcessor.process(job.getId());

        // Verify Job completed
        VideoProcessingJob completedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(ProcessingJobStatus.COMPLETED, completedJob.getStatus());

        // Verify VideoAsset and Content status
        VideoAsset asset = videoAssetRepository.findById(testVideoAsset.getId()).orElseThrow();
        assertEquals(VideoAssetStatus.READY, asset.getStatus());
        assertEquals(180, asset.getDurationSeconds());

        Content content = contentRepository.findById(testContent.getId()).orElseThrow();
        assertEquals(ContentStatus.READY, content.getStatus());

        // Verify VideoRenditions in database
        List<VideoRendition> renditions = videoRenditionRepository.findByVideoAssetIdOrderByHeightDesc(asset.getId());
        assertEquals(5, renditions.size(), "1080p source should generate 5 renditions (1080p, 720p, 480p, 360p, 144p)");

        assertEquals("1080p", renditions.get(0).getResolution());
        assertEquals(1080, renditions.get(0).getHeight());
        assertEquals("720p", renditions.get(1).getResolution());
        assertEquals("480p", renditions.get(2).getResolution());
        assertEquals("360p", renditions.get(3).getResolution());
        assertEquals("144p", renditions.get(4).getResolution());

        for (VideoRendition r : renditions) {
            assertEquals(RenditionStatus.READY, r.getStatus());
            assertEquals("h264", r.getVideoCodec());
            assertEquals("aac", r.getAudioCodec());
            assertNotNull(r.getChecksumSha256());
            assertTrue(r.getStorageKey().startsWith("renditions/asset_" + asset.getId() + "/"));
        }

        // Verify MinIO uploads
        verify(objectStorageService, atLeastOnce()).uploadObject(eq("communityott-videos"), anyString(), any(InputStream.class), anyLong(), eq("video/mp4"));
    }

    @Test
    @Order(4)
    @DisplayName("4. REST API: POST /api/v1/admin/videos/{videoId}/transcode enqueues transcoding job")
    void test04_enqueueTranscodingEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/admin/videos/" + testVideoAsset.getId() + "/transcode")
                        .header("Authorization", getBearerToken(contentManager)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.jobType", is("TRANSCODE")))
                .andExpect(jsonPath("$.data.status", is("QUEUED")));
    }

    @Test
    @Order(5)
    @DisplayName("5. REST API: GET /api/v1/admin/videos/{videoId}/renditions returns all generated renditions")
    void test05_listRenditionsEndpoint() throws Exception {
        // Seed renditions
        videoRenditionRepository.save(VideoRendition.builder()
                .videoAsset(testVideoAsset)
                .resolution("1080p")
                .width(1920)
                .height(1080)
                .bitrateKbps(4800)
                .audioBitrateKbps(192)
                .fileSizeBytes(50_000_000L)
                .storageBucket("communityott-videos")
                .storageKey("renditions/asset_" + testVideoAsset.getId() + "/1080p.mp4")
                .checksumSha256("sha1080")
                .status(RenditionStatus.READY)
                .build());

        videoRenditionRepository.save(VideoRendition.builder()
                .videoAsset(testVideoAsset)
                .resolution("720p")
                .width(1280)
                .height(720)
                .bitrateKbps(2600)
                .audioBitrateKbps(128)
                .fileSizeBytes(25_000_000L)
                .storageBucket("communityott-videos")
                .storageKey("renditions/asset_" + testVideoAsset.getId() + "/720p.mp4")
                .checksumSha256("sha720")
                .status(RenditionStatus.READY)
                .build());

        mockMvc.perform(get("/api/v1/admin/videos/" + testVideoAsset.getId() + "/renditions")
                        .header("Authorization", getBearerToken(contentManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].resolution", is("1080p")))
                .andExpect(jsonPath("$.data[1].resolution", is("720p")));
    }

    @Test
    @Order(6)
    @DisplayName("6. Security: Regular user without VIDEO_PROCESS receives 403 Forbidden")
    void test06_securityAuthorizationCheck() throws Exception {
        mockMvc.perform(post("/api/v1/admin/videos/" + testVideoAsset.getId() + "/transcode")
                        .header("Authorization", getBearerToken(regularUser)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/videos/" + testVideoAsset.getId() + "/renditions")
                        .header("Authorization", getBearerToken(regularUser)))
                .andExpect(status().isForbidden());
    }
}
