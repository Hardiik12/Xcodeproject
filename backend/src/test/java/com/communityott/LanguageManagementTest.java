package com.communityott;

import com.communityott.content.dto.LanguageCreateRequest;
import com.communityott.content.dto.LanguageUpdateRequest;
import com.communityott.content.entity.Language;
import com.communityott.content.repository.LanguageRepository;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserRoleId;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LanguageManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private User superAdmin;
    private User contentManager;
    private User regularUser;

    @BeforeEach
    void setUp() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        Role contentManagerRole = roleRepository.findByName("CONTENT_MANAGER").orElseThrow();
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        superAdmin = createTestUser("superadmin_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org");
        userRoleRepository.save(new UserRole(superAdmin, superAdminRole));

        contentManager = createTestUser("cm_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org");
        userRoleRepository.save(new UserRole(contentManager, contentManagerRole));

        regularUser = createTestUser("user_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org");
        userRoleRepository.save(new UserRole(regularUser, userRole));
    }

    private User createTestUser(String email) {
        User user = User.builder()
                .email(email)
                .phone("+91" + (int)(Math.random() * 900000000 + 100000000))
                .displayName("Test User")
                .status(UserStatus.ACTIVE)
                .build();
        return userRepository.save(user);
    }

    @Test
    @Order(1)
    @DisplayName("1. Create language with valid permissions succeeds")
    void test01_createLanguageSuccess() throws Exception {
        LanguageCreateRequest request = LanguageCreateRequest.builder()
                .name("Sanskrit")
                .code("sa")
                .active(true)
                .build();

        mockMvc.perform(post("/api/v1/admin/languages")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Sanskrit")))
                .andExpect(jsonPath("$.data.code", is("sa")))
                .andExpect(jsonPath("$.data.active", is(true)));

        assertThat(languageRepository.existsByCode("sa")).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("2. Duplicate language code is rejected with 409 Conflict")
    void test02_duplicateLanguageCodeRejected() throws Exception {
        LanguageCreateRequest request = LanguageCreateRequest.builder()
                .name("Telugu Secondary")
                .code("te") // 'te' already exists from V7 seeds
                .build();

        mockMvc.perform(post("/api/v1/admin/languages")
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("LANGUAGE_DUPLICATE")));
    }

    @Test
    @Order(3)
    @DisplayName("3. Update language details succeeds")
    void test03_updateLanguageSuccess() throws Exception {
        Language lang = languageRepository.save(Language.builder()
                .name("Odia Language")
                .code("or")
                .active(true)
                .build());

        LanguageUpdateRequest request = LanguageUpdateRequest.builder()
                .name("Odia (Standard)")
                .build();

        mockMvc.perform(put("/api/v1/admin/languages/" + lang.getId())
                        .header("X-Dev-User-Id", contentManager.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Odia (Standard)")));
    }

    @Test
    @Order(4)
    @DisplayName("4. Deactivate language sets active to false")
    void test04_deactivateLanguageSuccess() throws Exception {
        Language lang = languageRepository.save(Language.builder()
                .name("Temporary Language")
                .code("temp-lang")
                .active(true)
                .build());

        mockMvc.perform(delete("/api/v1/admin/languages/" + lang.getId())
                        .header("X-Dev-User-Id", contentManager.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.active", is(false)));

        Language updated = languageRepository.findById(lang.getId()).orElseThrow();
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("5. Inactive languages are excluded from public GET /api/v1/languages")
    void test05_inactiveLanguagesExcludedFromPublicListing() throws Exception {
        Language activeLang = languageRepository.save(Language.builder()
                .name("Active Public Language " + UUID.randomUUID().toString().substring(0, 6))
                .code("apl-" + UUID.randomUUID().toString().substring(0, 4))
                .active(true)
                .build());

        Language inactiveLang = languageRepository.save(Language.builder()
                .name("Hidden Inactive Language " + UUID.randomUUID().toString().substring(0, 6))
                .code("hil-" + UUID.randomUUID().toString().substring(0, 4))
                .active(false)
                .build());

        mockMvc.perform(get("/api/v1/languages")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[?(@.id == " + activeLang.getId() + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.id == " + inactiveLang.getId() + ")]").doesNotExist());
    }

    @Test
    @Order(6)
    @DisplayName("6. Regular USER cannot modify languages (403 Forbidden)")
    void test06_regularUserCannotModifyLanguages() throws Exception {
        LanguageCreateRequest request = LanguageCreateRequest.builder()
                .name("Unauthorized Lang")
                .code("unauth")
                .build();

        mockMvc.perform(post("/api/v1/admin/languages")
                        .header("X-Dev-User-Id", regularUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }
}
