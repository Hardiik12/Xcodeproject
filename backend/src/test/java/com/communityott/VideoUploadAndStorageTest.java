package com.communityott;

import com.communityott.common.rbac.SystemPermissions;
import com.communityott.content.entity.AgeRating;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.storage.ChecksumUtility;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VideoUploadAndStorageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private VideoAssetRepository videoAssetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @MockBean
    private MinioClient minioClient;

    private User superAdminUser;
    private User contentManagerUser;
    private User managerUser;
    private User regularUser;
    private Content testContent;

    @BeforeEach
    void setUp() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        Role contentManagerRole = roleRepository.findByName("CONTENT_MANAGER").orElseThrow();
        Role managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        superAdminUser = createUser("video_admin@communityott.com", "+1000000001", superAdminRole);
        contentManagerUser = createUser("video_cm@communityott.com", "+1000000002", contentManagerRole);
        managerUser = createUser("video_mgr@communityott.com", "+1000000003", managerRole);
        regularUser = createUser("video_user@communityott.com", "+1000000004", userRole);

        testContent = Content.builder()
                .title("Sample OTT Documentary")
                .description("Documentary on village craft and culture.")
                .contentType(ContentType.DOCUMENTARY)
                .ageRating(AgeRating.U)
                .releaseDate(LocalDate.of(2026, 3, 15))
                .durationSeconds(2700)
                .status(ContentStatus.DRAFT)
                .createdBy(superAdminUser.getId())
                .updatedBy(superAdminUser.getId())
                .build();
        testContent = contentRepository.save(testContent);
    }

    private User createUser(String email, String phone, Role role) {
        User user = User.builder()
                .email(email)
                .phone(phone)
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);
        UserRole ur = new UserRole(user, role);
        user.getUserRoles().add(ur);
        return userRepository.save(user);
    }

    @Test
    @Order(1)
    void test01_successfulVideoUpload() throws Exception {
        byte[] videoData = "DUMMY_MP4_BINARY_PAYLOAD_FOR_TESTING_PURPOSES".getBytes(StandardCharsets.UTF_8);
        String expectedChecksum = ChecksumUtility.calculateSha256(new ByteArrayInputStream(videoData));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample_documentary.mp4",
                "video/mp4",
                videoData
        );

        mockMvc.perform(multipart("/api/v1/admin/content/" + testContent.getId() + "/videos/upload")
                        .file(file)
                        .header("X-Dev-User-Id", superAdminUser.getId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.contentId", is(testContent.getId().intValue())))
                .andExpect(jsonPath("$.originalFilename", is("sample_documentary.mp4")))
                .andExpect(jsonPath("$.contentType", is("video/mp4")))
                .andExpect(jsonPath("$.fileSizeBytes", is(videoData.length)))
                .andExpect(jsonPath("$.checksumSha256", is(expectedChecksum)))
                .andExpect(jsonPath("$.status", is("UPLOADED")))
                .andExpect(jsonPath("$.storageKey", notNullValue()));

        // Verify database state
        List<VideoAsset> assets = videoAssetRepository.findByContentIdOrderByCreatedAtDesc(testContent.getId());
        assertEquals(1, assets.size());
        VideoAsset asset = assets.get(0);
        assertEquals("sample_documentary.mp4", asset.getOriginalFilename());
        assertEquals(expectedChecksum, asset.getChecksumSha256());
        assertEquals(VideoAssetStatus.UPLOADED, asset.getStatus());

        // Verify content status transitioned from DRAFT to UPLOADING
        Content updatedContent = contentRepository.findById(testContent.getId()).orElseThrow();
        assertEquals(ContentStatus.UPLOADING, updatedContent.getStatus());
    }

    @Test
    @Order(2)
    void test02_contentManagerCanUploadVideo() throws Exception {
        byte[] videoData = "CONTENT_MANAGER_VIDEO_STREAM".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hero_podcast.mkv",
                "video/x-matroska",
                videoData
        );

        mockMvc.perform(multipart("/api/v1/admin/content/" + testContent.getId() + "/videos/upload")
                        .file(file)
                        .header("X-Dev-User-Id", contentManagerUser.getId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename", is("hero_podcast.mkv")))
                .andExpect(jsonPath("$.contentType", is("video/x-matroska")))
                .andExpect(jsonPath("$.status", is("UPLOADED")));
    }

    @Test
    @Order(3)
    void test03_userCannotUploadVideo() throws Exception {
        byte[] videoData = "USER_ATTEMPTING_UPLOAD".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "user_upload.mp4",
                "video/mp4",
                videoData
        );

        mockMvc.perform(multipart("/api/v1/admin/content/" + testContent.getId() + "/videos/upload")
                        .file(file)
                        .header("X-Dev-User-Id", regularUser.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void test04_managerCannotUploadVideoWithoutPermission() throws Exception {
        byte[] videoData = "MANAGER_ATTEMPTING_UPLOAD".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manager_upload.mp4",
                "video/mp4",
                videoData
        );

        mockMvc.perform(multipart("/api/v1/admin/content/" + testContent.getId() + "/videos/upload")
                        .file(file)
                        .header("X-Dev-User-Id", managerUser.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void test05_emptyVideoFileRejected() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.mp4",
                "video/mp4",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/admin/content/" + testContent.getId() + "/videos/upload")
                        .file(emptyFile)
                        .header("X-Dev-User-Id", superAdminUser.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_VIDEO_FORMAT")));
    }

    @Test
    @Order(6)
    void test06_invalidMimeTypeRejected() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "file",
                "picture.png",
                "image/png",
                "fake image payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/admin/content/" + testContent.getId() + "/videos/upload")
                        .file(imageFile)
                        .header("X-Dev-User-Id", superAdminUser.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_VIDEO_FORMAT")));
    }

    @Test
    @Order(7)
    void test07_invalidExtensionRejected() throws Exception {
        MockMultipartFile executableFile = new MockMultipartFile(
                "file",
                "malicious.exe",
                "video/mp4",
                "fake executable payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/admin/content/" + testContent.getId() + "/videos/upload")
                        .file(executableFile)
                        .header("X-Dev-User-Id", superAdminUser.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_VIDEO_FORMAT")));
    }

    @Test
    @Order(8)
    void test08_uploadToNonExistentContentReturns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "valid.mp4",
                "video/mp4",
                "valid payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/admin/content/999999/videos/upload")
                        .file(file)
                        .header("X-Dev-User-Id", superAdminUser.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("CONTENT_NOT_FOUND")));
    }

    @Test
    @Order(9)
    void test09_uploadToArchivedContentRejected() throws Exception {
        testContent.setStatus(ContentStatus.ARCHIVED);
        contentRepository.save(testContent);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "valid.mp4",
                "video/mp4",
                "valid payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/admin/content/" + testContent.getId() + "/videos/upload")
                        .file(file)
                        .header("X-Dev-User-Id", superAdminUser.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_CONTENT_STATE_TRANSITION")));
    }

    @Test
    @Order(10)
    void test10_listVideoAssetsForContent() throws Exception {
        VideoAsset asset1 = VideoAsset.builder()
                .content(testContent)
                .originalFilename("clip1.mp4")
                .fileSizeBytes(1024L)
                .contentType("video/mp4")
                .checksumSha256("checksum1")
                .storageBucket("communityott-media")
                .storageKey("sources/" + testContent.getId() + "/key1.mp4")
                .status(VideoAssetStatus.UPLOADED)
                .build();
        VideoAsset asset2 = VideoAsset.builder()
                .content(testContent)
                .originalFilename("clip2.mp4")
                .fileSizeBytes(2048L)
                .contentType("video/mp4")
                .checksumSha256("checksum2")
                .storageBucket("communityott-media")
                .storageKey("sources/" + testContent.getId() + "/key2.mp4")
                .status(VideoAssetStatus.UPLOADED)
                .build();
        videoAssetRepository.saveAll(List.of(asset1, asset2));

        mockMvc.perform(get("/api/v1/admin/content/" + testContent.getId() + "/videos")
                        .header("X-Dev-User-Id", contentManagerUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].contentId", is(testContent.getId().intValue())));
    }

    @Test
    @Order(11)
    void test11_getVideoAssetById() throws Exception {
        VideoAsset asset = videoAssetRepository.save(VideoAsset.builder()
                .content(testContent)
                .originalFilename("episode.mov")
                .fileSizeBytes(4096L)
                .contentType("video/quicktime")
                .checksumSha256("unique_checksum_mov")
                .storageBucket("communityott-media")
                .storageKey("sources/" + testContent.getId() + "/episode.mov")
                .status(VideoAssetStatus.UPLOADED)
                .durationSeconds(1800)
                .width(1920)
                .height(1080)
                .build());

        mockMvc.perform(get("/api/v1/admin/content/" + testContent.getId() + "/videos/" + asset.getId())
                        .header("X-Dev-User-Id", contentManagerUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(asset.getId().intValue())))
                .andExpect(jsonPath("$.originalFilename", is("episode.mov")))
                .andExpect(jsonPath("$.contentType", is("video/quicktime")))
                .andExpect(jsonPath("$.width", is(1920)))
                .andExpect(jsonPath("$.height", is(1080)));
    }

    @Test
    @Order(12)
    void test12_deleteVideoAsset() throws Exception {
        VideoAsset asset = videoAssetRepository.save(VideoAsset.builder()
                .content(testContent)
                .originalFilename("to_delete.mp4")
                .fileSizeBytes(2048L)
                .contentType("video/mp4")
                .checksumSha256("delete_checksum")
                .storageBucket("communityott-media")
                .storageKey("sources/" + testContent.getId() + "/to_delete.mp4")
                .status(VideoAssetStatus.UPLOADED)
                .build());

        mockMvc.perform(delete("/api/v1/admin/content/" + testContent.getId() + "/videos/" + asset.getId())
                        .header("X-Dev-User-Id", superAdminUser.getId().toString()))
                .andExpect(status().isNoContent());

        VideoAsset updated = videoAssetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(VideoAssetStatus.DELETED, updated.getStatus());
    }

    @Test
    @Order(13)
    void test13_unauthorizedUserCannotDeleteVideoAsset() throws Exception {
        VideoAsset asset = videoAssetRepository.save(VideoAsset.builder()
                .content(testContent)
                .originalFilename("protected.mp4")
                .fileSizeBytes(1024L)
                .contentType("video/mp4")
                .checksumSha256("protected_checksum")
                .storageBucket("communityott-media")
                .storageKey("sources/" + testContent.getId() + "/protected.mp4")
                .status(VideoAssetStatus.UPLOADED)
                .build());

        mockMvc.perform(delete("/api/v1/admin/content/" + testContent.getId() + "/videos/" + asset.getId())
                        .header("X-Dev-User-Id", regularUser.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    void test14_checksumIntegrityVerification() {
        byte[] payload1 = "IDENTICAL_DATA_STREAM".getBytes(StandardCharsets.UTF_8);
        byte[] payload2 = "IDENTICAL_DATA_STREAM".getBytes(StandardCharsets.UTF_8);
        byte[] payload3 = "DIFFERENT_DATA_STREAM".getBytes(StandardCharsets.UTF_8);

        String checksum1 = ChecksumUtility.calculateSha256(new ByteArrayInputStream(payload1));
        String checksum2 = ChecksumUtility.calculateSha256(new ByteArrayInputStream(payload2));
        String checksum3 = ChecksumUtility.calculateSha256(new ByteArrayInputStream(payload3));

        assertNotNull(checksum1);
        assertEquals(64, checksum1.length());
        assertEquals(checksum1, checksum2);
        assertTrue(!checksum1.equals(checksum3));
    }
}
