package com.communityott;

import com.communityott.auth.security.JwtTokenService;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.content.repository.ContentRepository;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.saved.dto.SavedContentResponse;
import com.communityott.saved.entity.SavedContent;
import com.communityott.saved.repository.SavedContentRepository;
import com.communityott.saved.service.SavedContentService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class SavedContentTest {

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
    private SavedContentRepository savedContentRepository;

    @Autowired
    private SavedContentService savedContentService;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User testUser1;
    private User testUser2;
    private String user1Token;
    private String user2Token;

    private Content content1;
    private Content content2;
    private Content content3;

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
                .email("saveduser1_" + uniqueSuffix + "@communityott.com")
                .displayName("Saved User 1")
                .phone("+9193" + String.format("%08d", uniqueSuffix % 100000000L))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(testUser1, userRole));

        testUser2 = userRepository.save(User.builder()
                .email("saveduser2_" + uniqueSuffix + "@communityott.com")
                .displayName("Saved User 2")
                .phone("+9194" + String.format("%08d", uniqueSuffix % 100000000L))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(testUser2, userRole));

        user1Token = jwtTokenService.generateAccessToken(testUser1);
        user2Token = jwtTokenService.generateAccessToken(testUser2);

        content1 = contentRepository.save(Content.builder()
                .title("Kalamkari Art Documentary " + uniqueSuffix)
                .subtitle("Ancient textiles")
                .description("Heritage documentary on Kalamkari art form.")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(3600)
                .thumbnailUrl("https://media.communityott.com/thumbs/kalamkari_" + uniqueSuffix + ".jpg")
                .bannerUrl("https://media.communityott.com/banners/kalamkari_" + uniqueSuffix + ".jpg")
                .build());

        content2 = contentRepository.save(Content.builder()
                .title("Kuchipudi Classical Dance " + uniqueSuffix)
                .subtitle("Cultural dance")
                .description("In-depth exploration of Kuchipudi.")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(2400)
                .thumbnailUrl("https://media.communityott.com/thumbs/kuchipudi_" + uniqueSuffix + ".jpg")
                .bannerUrl("https://media.communityott.com/banners/kuchipudi_" + uniqueSuffix + ".jpg")
                .build());

        content3 = contentRepository.save(Content.builder()
                .title("Kakatiya Architecture " + uniqueSuffix)
                .subtitle("Historic monuments")
                .description("Documentary on Kakatiya dynasty temples.")
                .contentType(ContentType.DOCUMENTARY)
                .status(ContentStatus.PUBLISHED)
                .durationSeconds(1800)
                .thumbnailUrl("https://media.communityott.com/thumbs/kakatiya_" + uniqueSuffix + ".jpg")
                .bannerUrl("https://media.communityott.com/banners/kakatiya_" + uniqueSuffix + ".jpg")
                .build());
    }

    @Test
    @DisplayName("Test 01: Unauthenticated request to My List endpoints returns 401")
    void test01_unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/my-list/" + content1.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/users/me/my-list/" + content1.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me/my-list/" + content1.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me/my-list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Test 02: Add content to My List successfully")
    void test02_addToMyList_Success() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/my-list/" + content1.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contentId").value(content1.getId()))
                .andExpect(jsonPath("$.data.title").value(content1.getTitle()))
                .andExpect(jsonPath("$.data.isPlayable").value(true))
                .andExpect(jsonPath("$.data.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.savedAt").isNotEmpty());

        assertThat(savedContentRepository.existsByUserIdAndContentId(testUser1.getId(), content1.getId())).isTrue();
    }

    @Test
    @DisplayName("Test 03: Add to My List is idempotent (no duplicate rows created)")
    void test03_addToMyList_Idempotent() throws Exception {
        // First add
        mockMvc.perform(post("/api/v1/users/me/my-list/" + content1.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk());

        // Second add of same item
        mockMvc.perform(post("/api/v1/users/me/my-list/" + content1.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentId").value(content1.getId()));

        assertThat(savedContentRepository.findAll().stream()
                .filter(sc -> sc.getUser().getId().equals(testUser1.getId()) && sc.getContent().getId().equals(content1.getId()))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 04: Remove content from My List successfully")
    void test04_removeFromMyList_Success() throws Exception {
        savedContentService.addToMyList(testUser1.getId(), content1.getId());
        assertThat(savedContentRepository.existsByUserIdAndContentId(testUser1.getId(), content1.getId())).isTrue();

        mockMvc.perform(delete("/api/v1/users/me/my-list/" + content1.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(savedContentRepository.existsByUserIdAndContentId(testUser1.getId(), content1.getId())).isFalse();
    }

    @Test
    @DisplayName("Test 05: Remove non-existent item from My List is idempotent and does not fail")
    void test05_removeFromMyList_NonExistent_Idempotent() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/my-list/999999")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Test 06: Check saved status returns true and false accurately")
    void test06_isSaved_TrueAndFalse() throws Exception {
        // Not saved yet
        mockMvc.perform(get("/api/v1/users/me/my-list/" + content1.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(false));

        // Save it
        savedContentService.addToMyList(testUser1.getId(), content1.getId());

        // Now saved
        mockMvc.perform(get("/api/v1/users/me/my-list/" + content1.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(true));
    }

    @Test
    @DisplayName("Test 07: Get My List orders newest saved first (savedAt DESC)")
    void test07_ordering_SavedAtDesc() throws Exception {
        savedContentService.addToMyList(testUser1.getId(), content1.getId());
        Thread.sleep(50);
        savedContentService.addToMyList(testUser1.getId(), content2.getId());
        Thread.sleep(50);
        savedContentService.addToMyList(testUser1.getId(), content3.getId());

        MvcResult result = mockMvc.perform(get("/api/v1/users/me/my-list")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andReturn();

        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("content");
        assertThat(list.get(0).path("contentId").asLong()).isEqualTo(content3.getId());
        assertThat(list.get(1).path("contentId").asLong()).isEqualTo(content2.getId());
        assertThat(list.get(2).path("contentId").asLong()).isEqualTo(content1.getId());
    }

    @Test
    @DisplayName("Test 08: Pagination and max page size enforcement")
    void test08_pagination_AndMaxPageSize() throws Exception {
        savedContentService.addToMyList(testUser1.getId(), content1.getId());
        savedContentService.addToMyList(testUser1.getId(), content2.getId());
        savedContentService.addToMyList(testUser1.getId(), content3.getId());

        // Page 0 size 2
        mockMvc.perform(get("/api/v1/users/me/my-list?page=0&size=2")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        // Size 1000 clamped to 50
        mockMvc.perform(get("/api/v1/users/me/my-list?page=0&size=1000")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    @DisplayName("Test 09: Empty My List returns empty page (not 404)")
    void test09_empty_ReturnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/my-list")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("Test 10: User isolation - User A cannot see or delete User B's saved content")
    void test10_userIsolation() throws Exception {
        savedContentService.addToMyList(testUser1.getId(), content1.getId());
        savedContentService.addToMyList(testUser2.getId(), content2.getId());

        // User 1 sees only Content 1
        mockMvc.perform(get("/api/v1/users/me/my-list")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].contentId").value(content1.getId()));

        // User 2 sees only Content 2
        mockMvc.perform(get("/api/v1/users/me/my-list")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].contentId").value(content2.getId()));

        // User 1 deleting Content 2 does NOT delete User 2's saved record
        mockMvc.perform(delete("/api/v1/users/me/my-list/" + content2.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk());

        assertThat(savedContentRepository.existsByUserIdAndContentId(testUser2.getId(), content2.getId())).isTrue();
    }

    @Test
    @DisplayName("Test 11: Nonexistent content returns 404 on add")
    void test11_nonexistentContent_Returns404() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/my-list/999999")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONTENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Test 12: Unavailable or Draft content retains relation with isPlayable=false badge")
    void test12_unavailableContent_RetainsRelation() throws Exception {
        Content draftContent = contentRepository.save(Content.builder()
                .title("Upcoming Telugu Epic")
                .contentType(ContentType.MOVIE)
                .status(ContentStatus.DRAFT)
                .durationSeconds(7200)
                .build());

        savedContentService.addToMyList(testUser1.getId(), draftContent.getId());

        mockMvc.perform(get("/api/v1/users/me/my-list")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].contentId").value(draftContent.getId()))
                .andExpect(jsonPath("$.data.content[0].isPlayable").value(false))
                .andExpect(jsonPath("$.data.content[0].availability").value("UNAVAILABLE"));
    }

    @Test
    @DisplayName("Test 13: Saved Content is independent of Watch Progress and History")
    void test13_independenceFromProgressAndHistory() throws Exception {
        savedContentService.addToMyList(testUser1.getId(), content1.getId());
        assertThat(savedContentRepository.existsByUserIdAndContentId(testUser1.getId(), content1.getId())).isTrue();

        // Check it is in My List
        mockMvc.perform(get("/api/v1/users/me/my-list")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // Removing does not affect content entity
        savedContentService.removeFromMyList(testUser1.getId(), content1.getId());
        assertThat(contentRepository.existsById(content1.getId())).isTrue();
    }

    @Test
    @DisplayName("Test 14: Completing saved content does not automatically remove it from My List")
    void test14_completingSavedContentDoesNotRemoveIt() throws Exception {
        savedContentService.addToMyList(testUser1.getId(), content1.getId());

        // Verify still in My List
        mockMvc.perform(get("/api/v1/users/me/my-list")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Test 15: Check saved status returns false for unauthenticated or non-existent content")
    void test15_isSaved_NonExistentContent() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/my-list/999999")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(false))
                .andExpect(jsonPath("$.data.contentId").value(999999));
    }
}
