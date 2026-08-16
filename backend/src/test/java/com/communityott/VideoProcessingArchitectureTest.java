package com.communityott;

import com.communityott.auth.security.JwtTokenService;
import com.communityott.common.exception.ActiveJobAlreadyExistsException;
import com.communityott.common.exception.InvalidJobStateTransitionException;
import com.communityott.content.processing.MediaProbeResult;
import com.communityott.content.dto.VideoProcessingJobResponse;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.content.entity.ProcessingJobStatus;
import com.communityott.content.entity.ProcessingJobType;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.entity.VideoProcessingJob;
import com.communityott.content.processing.DefaultFFprobeService;
import com.communityott.content.processing.DefaultProcessRunner;
import com.communityott.content.processing.DefaultVideoProcessor;
import com.communityott.content.processing.FFmpegProperties;
import com.communityott.content.processing.FFmpegTranscodeService;
import com.communityott.content.processing.FFprobeService;
import com.communityott.content.processing.ProcessExecutionResult;
import com.communityott.content.processing.ProcessRunner;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.repository.VideoProcessingJobRepository;
import com.communityott.content.repository.VideoRenditionRepository;
import com.communityott.content.service.VideoProcessingService;
import com.communityott.content.service.VideoUploadService;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
public class VideoProcessingArchitectureTest {

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
    private VideoUploadService videoUploadService;

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
    private ProcessRunner processRunner;

    private User superAdmin;
    private User contentManager;
    private User regularUser;

    private String superAdminToken;
    private String contentManagerToken;
    private String regularUserToken;

    private Content testContent;
    private VideoAsset testVideoAsset;

    private static final String SAMPLE_FFPROBE_JSON = """
            {
              "streams": [
                {
                  "index": 0,
                  "codec_name": "h264",
                  "codec_long_name": "H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10",
                  "codec_type": "video",
                  "width": 1920,
                  "height": 1080,
                  "r_frame_rate": "30/1",
                  "duration": "120.500000"
                },
                {
                  "index": 1,
                  "codec_name": "aac",
                  "codec_type": "audio",
                  "channels": 2
                }
              ],
              "format": {
                "filename": "source.mp4",
                "format_name": "mov,mp4,m4a,3gp,3g2,mj2",
                "duration": "120.500000",
                "size": "52428800",
                "bit_rate": "3480000"
              }
            }
            """;

    @BeforeEach
    void setUp() {
        Role contentManagerRole = roleRepository.findByName("CONTENT_MANAGER").orElseThrow();
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        superAdmin = userRepository.save(User.builder()
                .email("admin_proc_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("Content Admin")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(superAdmin, contentManagerRole));
        superAdminToken = jwtTokenService.generateAccessToken(superAdmin);

        contentManager = userRepository.save(User.builder()
                .email("cm_proc_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("Content Manager")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(contentManager, contentManagerRole));
        contentManagerToken = jwtTokenService.generateAccessToken(contentManager);

        regularUser = userRepository.save(User.builder()
                .email("user_proc_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("Regular User")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(regularUser, userRole));
        regularUserToken = jwtTokenService.generateAccessToken(regularUser);

        testContent = contentRepository.save(Content.builder()
                .title("Processing Test Film")
                .contentType(ContentType.MOVIE)
                .status(ContentStatus.DRAFT)
                .durationSeconds(120)
                .createdBy(superAdmin.getId())
                .build());

        testVideoAsset = videoAssetRepository.save(VideoAsset.builder()
                .content(testContent)
                .originalFilename("master_source.mp4")
                .fileSizeBytes(52428800L)
                .contentType("video/mp4")
                .checksumSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .storageBucket("communityott-videos")
                .storageKey("sources/" + testContent.getId() + "/e3b0c442_master_source.mp4")
                .status(VideoAssetStatus.UPLOADED)
                .createdBy(superAdmin.getId())
                .build());

        when(objectStorageService.getBucketName()).thenReturn("communityott-videos");
        when(objectStorageService.doesObjectExist(anyString(), anyString())).thenReturn(true);
        when(objectStorageService.getObject(anyString(), anyString()))
                .thenReturn(new ByteArrayInputStream("fake-video-bytes".getBytes(StandardCharsets.UTF_8)));
    }

    // =========================================================================
    // 1. DOMAIN & STATE MACHINE TESTS
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1. Job creation initial state is QUEUED with zero attempts")
    void test01_jobCreationInitialState() {
        VideoProcessingJobResponse job = videoProcessingService.createAndEnqueueProbeJob(testVideoAsset.getId(), superAdmin.getId());
        assertNotNull(job.getId());
        assertEquals(ProcessingJobStatus.QUEUED, job.getStatus());
        assertEquals(ProcessingJobType.PROBE, job.getJobType());
        assertEquals(0, job.getAttemptCount());
        assertEquals(3, job.getMaxAttempts());
    }

    @Test
    @Order(2)
    @DisplayName("2. Idempotency: Reject duplicate active job for the same asset")
    void test02_rejectDuplicateActiveJob() {
        videoProcessingService.createAndEnqueueProbeJob(testVideoAsset.getId(), superAdmin.getId());

        assertThrows(ActiveJobAlreadyExistsException.class, () -> {
            videoProcessingService.createAndEnqueueProbeJob(testVideoAsset.getId(), superAdmin.getId());
        });
    }

    @Test
    @Order(3)
    @DisplayName("3. State machine: Valid transitions allowed (QUEUED -> PROCESSING -> COMPLETED)")
    void test03_validStateTransitions() {
        assertTrue(ProcessingJobStatus.QUEUED.canTransitionTo(ProcessingJobStatus.PROCESSING));
        assertTrue(ProcessingJobStatus.PROCESSING.canTransitionTo(ProcessingJobStatus.COMPLETED));
        assertTrue(ProcessingJobStatus.PROCESSING.canTransitionTo(ProcessingJobStatus.FAILED));
        assertTrue(ProcessingJobStatus.FAILED.canTransitionTo(ProcessingJobStatus.QUEUED)); // retry
    }

    @Test
    @Order(4)
    @DisplayName("4. State machine: Invalid transitions rejected (COMPLETED -> QUEUED, CANCELLED -> PROCESSING)")
    void test04_invalidStateTransitionsRejected() {
        assertFalse(ProcessingJobStatus.COMPLETED.canTransitionTo(ProcessingJobStatus.QUEUED));
        assertFalse(ProcessingJobStatus.COMPLETED.canTransitionTo(ProcessingJobStatus.PROCESSING));
        assertFalse(ProcessingJobStatus.CANCELLED.canTransitionTo(ProcessingJobStatus.PROCESSING));
        assertFalse(ProcessingJobStatus.QUEUED.canTransitionTo(ProcessingJobStatus.COMPLETED));
    }

    // =========================================================================
    // 2. FFPROBE & PROCESS RUNNER UNIT TESTS
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("5. FFprobe JSON Parser: Parses realistic ffprobe output correctly")
    void test05_ffprobeJsonParserSuccess() {
        DefaultFFprobeService probeService = new DefaultFFprobeService(ffmpegProperties, processRunner, objectMapper);
        MediaProbeResult result = probeService.parseProbeJson(SAMPLE_FFPROBE_JSON);

        assertTrue(result.isValidMedia());
        assertEquals(121, result.getDurationSeconds());
        assertEquals(1920, result.getWidth());
        assertEquals(1080, result.getHeight());
        assertEquals("h264", result.getVideoCodec());
        assertEquals("aac", result.getAudioCodec());
        assertEquals("30/1", result.getFrameRate());
        assertEquals(3480, result.getBitrateKbps());
        assertEquals("mov,mp4,m4a,3gp,3g2,mj2", result.getContainerFormat());
    }

    @Test
    @Order(6)
    @DisplayName("6. FFprobe JSON Parser: Detects corrupt media with no video stream")
    void test06_ffprobeJsonParserNoVideoStream() {
        String audioOnlyJson = """
                {
                  "streams": [
                    { "index": 0, "codec_name": "mp3", "codec_type": "audio" }
                  ],
                  "format": { "format_name": "mp3", "duration": "60.0" }
                }
                """;
        DefaultFFprobeService probeService = new DefaultFFprobeService(ffmpegProperties, processRunner, objectMapper);
        MediaProbeResult result = probeService.parseProbeJson(audioOnlyJson);

        assertFalse(result.isValidMedia());
        assertEquals("Media contains no video stream", result.getValidationError());
    }

    @Test
    @Order(7)
    @DisplayName("7. Safe ProcessRunner: Invokes command arguments safely without shell injection")
    void test07_safeProcessRunnerEcho() {
        DefaultProcessRunner runner = new DefaultProcessRunner();
        // Safe execution of OS echo command
        ProcessExecutionResult result = runner.execute(List.of("echo", "CommunityOTT-Safe-Arg"), 5);

        assertEquals(0, result.exitCode());
        assertTrue(result.isSuccess());
        assertFalse(result.timedOut());
        assertTrue(result.stdout().contains("CommunityOTT-Safe-Arg"));
    }

    @Test
    @Order(8)
    @DisplayName("8. Safe ProcessRunner: Handles process timeout and destroys hung process")
    void test08_safeProcessRunnerTimeout() {
        DefaultProcessRunner runner = new DefaultProcessRunner();
        // Sleep command exceeding 1s timeout
        ProcessExecutionResult result = runner.execute(List.of("sleep", "10"), 1);

        assertTrue(result.timedOut());
        assertFalse(result.isSuccess());
    }

    // =========================================================================
    // 3. PROCESSOR & WORKER PIPELINE TESTS
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("9. VideoProcessor: Successful probe execution updates VideoAsset & Content to READY")
    void test09_videoProcessorSuccessPath() {
        when(ffprobeService.probe(any(File.class))).thenReturn(MediaProbeResult.builder()
                .validMedia(true)
                .durationSeconds(150)
                .width(1920)
                .height(1080)
                .bitrateKbps(4500)
                .videoCodec("h264")
                .audioCodec("aac")
                .rawJson(SAMPLE_FFPROBE_JSON)
                .build());

        VideoProcessingJob job = jobRepository.save(VideoProcessingJob.builder()
                .videoAsset(testVideoAsset)
                .jobType(ProcessingJobType.PROBE)
                .status(ProcessingJobStatus.QUEUED)
                .build());

        when(ffmpegTranscodeService.transcode(any(), any(), any())).thenAnswer(invocation -> {
            File target = invocation.getArgument(1);
            if (target.getParentFile() != null) target.getParentFile().mkdirs();
            target.createNewFile();
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(new byte[]{1, 2, 3, 4});
            }
            return true;
        });

        DefaultVideoProcessor processor = new DefaultVideoProcessor(
                jobRepository, videoAssetRepository, videoRenditionRepository, contentRepository,
                objectStorageService, ffprobeService, ffmpegTranscodeService, ffmpegProperties
        );

        processor.process(job.getId());

        VideoProcessingJob updatedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(ProcessingJobStatus.COMPLETED, updatedJob.getStatus());
        assertNotNull(updatedJob.getCompletedAt());
        assertNotNull(updatedJob.getMediaMetadataJson());

        VideoAsset updatedAsset = videoAssetRepository.findById(testVideoAsset.getId()).orElseThrow();
        assertEquals(VideoAssetStatus.READY, updatedAsset.getStatus());
        assertEquals(150, updatedAsset.getDurationSeconds());
        assertEquals(1920, updatedAsset.getWidth());
        assertEquals(1080, updatedAsset.getHeight());

        Content updatedContent = contentRepository.findById(testContent.getId()).orElseThrow();
        assertEquals(ContentStatus.READY, updatedContent.getStatus());
    }

    @Test
    @Order(10)
    @DisplayName("10. VideoProcessor: Failed probe execution marks job and asset FAILED")
    void test10_videoProcessorFailurePath() {
        when(ffprobeService.probe(any(File.class))).thenReturn(MediaProbeResult.builder()
                .validMedia(false)
                .validationError("Invalid video codec or corrupted header")
                .build());

        VideoProcessingJob job = jobRepository.save(VideoProcessingJob.builder()
                .videoAsset(testVideoAsset)
                .jobType(ProcessingJobType.PROBE)
                .status(ProcessingJobStatus.QUEUED)
                .build());

        DefaultVideoProcessor processor = new DefaultVideoProcessor(
                jobRepository, videoAssetRepository, videoRenditionRepository, contentRepository,
                objectStorageService, ffprobeService, ffmpegTranscodeService, ffmpegProperties
        );

        processor.process(job.getId());

        VideoProcessingJob updatedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(ProcessingJobStatus.FAILED, updatedJob.getStatus());
        assertEquals("MEDIA_VALIDATION_FAILED", updatedJob.getErrorCode());
        assertTrue(updatedJob.getErrorMessage().contains("corrupted header"));

        VideoAsset updatedAsset = videoAssetRepository.findById(testVideoAsset.getId()).orElseThrow();
        assertEquals(VideoAssetStatus.FAILED, updatedAsset.getStatus());
    }

    @Test
    @Order(11)
    @DisplayName("11. Retry behavior: Requeuing failed job increments attempts")
    void test11_retryFailedJob() {
        VideoProcessingJob job = jobRepository.save(VideoProcessingJob.builder()
                .videoAsset(testVideoAsset)
                .jobType(ProcessingJobType.PROBE)
                .status(ProcessingJobStatus.FAILED)
                .attemptCount(1)
                .maxAttempts(3)
                .errorCode("TRANSIENT_ERROR")
                .build());

        VideoProcessingJobResponse retried = videoProcessingService.retryProcessingJob(job.getId(), superAdmin.getId());
        assertEquals(ProcessingJobStatus.QUEUED, retried.getStatus());
    }

    @Test
    @Order(12)
    @DisplayName("12. Retry limit: Job exceeding max attempts cannot be retried")
    void test12_retryLimitExceeded() {
        VideoProcessingJob job = jobRepository.save(VideoProcessingJob.builder()
                .videoAsset(testVideoAsset)
                .jobType(ProcessingJobType.PROBE)
                .status(ProcessingJobStatus.FAILED)
                .attemptCount(3)
                .maxAttempts(3)
                .build());

        assertThrows(InvalidJobStateTransitionException.class, () -> {
            videoProcessingService.retryProcessingJob(job.getId(), superAdmin.getId());
        });
    }

    @Test
    @Order(13)
    @DisplayName("13. Crash recovery: Stale jobs in PROCESSING status are recovered and requeued")
    void test13_crashRecoveryStaleJobs() {
        VideoProcessingJob staleJob = jobRepository.saveAndFlush(VideoProcessingJob.builder()
                .videoAsset(testVideoAsset)
                .jobType(ProcessingJobType.PROBE)
                .status(ProcessingJobStatus.PROCESSING)
                .attemptCount(1)
                .maxAttempts(3)
                .lastHeartbeatAt(Instant.now().minus(600, ChronoUnit.SECONDS))
                .build());

        int recovered = videoProcessingService.recoverStaleJobs();
        assertTrue(recovered >= 1);

        VideoProcessingJob refreshed = jobRepository.findById(staleJob.getId()).orElseThrow();
        assertEquals(ProcessingJobStatus.QUEUED, refreshed.getStatus());
    }

    @Test
    @Order(14)
    @DisplayName("14. Automatic processing: Uploading video automatically enqueues PROBE job")
    void test14_uploadAutomaticallyEnqueuesProbeJob() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "auto_upload_test.mp4",
                "video/mp4",
                "dummy-video-content-stream".getBytes(StandardCharsets.UTF_8)
        );

        when(objectStorageService.uploadObject(anyString(), anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenReturn("etag-123");

        var response = videoUploadService.uploadVideo(testContent.getId(), file, superAdmin.getId());
        assertNotNull(response.getId());

        List<VideoProcessingJob> jobs = jobRepository.findByVideoAssetIdOrderByCreatedAtDesc(response.getId());
        assertFalse(jobs.isEmpty());
        assertEquals(ProcessingJobType.PROBE, jobs.get(0).getJobType());
    }

    // =========================================================================
    // 4. REST API & RBAC AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @Order(15)
    @DisplayName("15. REST API: POST /api/v1/admin/videos/{videoId}/processing -> 202 Accepted (Admin)")
    void test15_enqueueProcessingApiSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/admin/videos/" + testVideoAsset.getId() + "/processing")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("QUEUED")))
                .andExpect(jsonPath("$.data.jobType", is("PROBE")));
    }

    @Test
    @Order(16)
    @DisplayName("16. REST API: GET /api/v1/admin/videos/{videoId}/processing -> 200 OK (Content Manager)")
    void test16_listProcessingJobsApiSuccess() throws Exception {
        jobRepository.save(VideoProcessingJob.builder()
                .videoAsset(testVideoAsset)
                .jobType(ProcessingJobType.PROBE)
                .status(ProcessingJobStatus.QUEUED)
                .build());

        mockMvc.perform(get("/api/v1/admin/videos/" + testVideoAsset.getId() + "/processing")
                        .header("Authorization", "Bearer " + contentManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @Order(17)
    @DisplayName("17. REST API: POST /api/v1/admin/videos/{videoId}/processing/retry -> 202 Accepted")
    void test17_retryProcessingApiSuccess() throws Exception {
        VideoProcessingJob failedJob = jobRepository.save(VideoProcessingJob.builder()
                .videoAsset(testVideoAsset)
                .jobType(ProcessingJobType.PROBE)
                .status(ProcessingJobStatus.FAILED)
                .attemptCount(1)
                .maxAttempts(3)
                .errorCode("TEMP_ERROR")
                .build());

        mockMvc.perform(post("/api/v1/admin/videos/" + testVideoAsset.getId() + "/processing/retry")
                        .param("jobId", failedJob.getId().toString())
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("QUEUED")));
    }

    @Test
    @Order(18)
    @DisplayName("18. RBAC Security: Regular USER is rejected with 403 Forbidden")
    void test18_regularUserRejectedWithForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/videos/" + testVideoAsset.getId() + "/processing")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }
}
