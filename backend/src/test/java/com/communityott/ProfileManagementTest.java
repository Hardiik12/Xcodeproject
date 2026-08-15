package com.communityott;

import com.communityott.auth.security.JwtTokenService;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.dto.CreateProfileRequest;
import com.communityott.user.dto.UpdateProfileRequest;
import com.communityott.user.entity.Profile;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.ProfileRepository;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@org.springframework.transaction.annotation.Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProfileManagementTest {

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
    private ProfileRepository profileRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User user1;
    private User user2;
    private String user1Token;
    private String user2Token;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        user1 = userRepository.save(User.builder()
                .email("user1_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("User One")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(user1, userRole));
        user1Token = jwtTokenService.generateAccessToken(user1);

        user2 = userRepository.save(User.builder()
                .email("user2_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("User Two")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(user2, userRole));
        user2Token = jwtTokenService.generateAccessToken(user2);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        profileRepository.deleteAll();
        if (user1 != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(user1.getId())).toList());
            userRepository.delete(user1);
        }
        if (user2 != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(user2.getId())).toList());
            userRepository.delete(user2);
        }
    }

    @Test
    @Order(1)
    @DisplayName("TEST 1: Profile can be created for authenticated user and first profile is default")
    void test1_CreateProfile_FirstProfileIsDefault() throws Exception {
        CreateProfileRequest request = CreateProfileRequest.builder()
                .displayName("Main Viewing Profile")
                .avatarUrl("https://cdn.communityott.org/avatars/1.png")
                .preferredLanguage("te")
                .build();

        mockMvc.perform(post("/api/v1/profiles")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.displayName").value("Main Viewing Profile"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("te"))
                .andExpect(jsonPath("$.data.userId").value(user1.getId()))
                .andExpect(jsonPath("$.data.default").value(true));
    }

    @Test
    @Order(2)
    @DisplayName("TEST 2: Second profile creation does not override default unless requested")
    void test2_CreateSecondProfile_DoesNotOverrideDefaultUnlessRequested() throws Exception {
        CreateProfileRequest req1 = CreateProfileRequest.builder()
                .displayName("Primary")
                .preferredLanguage("te")
                .build();

        mockMvc.perform(post("/api/v1/profiles")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        CreateProfileRequest req2 = CreateProfileRequest.builder()
                .displayName("Kids")
                .preferredLanguage("en")
                .isDefault(false)
                .build();

        mockMvc.perform(post("/api/v1/profiles")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.displayName").value("Kids"))
                .andExpect(jsonPath("$.data.default").value(false));

        List<Profile> profiles = profileRepository.findByUserIdOrderByCreatedAtAsc(user1.getId());
        assertThat(profiles).hasSize(2);
        assertThat(profiles.get(0).isDefault()).isTrue();
        assertThat(profiles.get(1).isDefault()).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("TEST 3: Profiles belong strictly to authenticated user (isolation)")
    void test3_ProfileListIsolation_BetweenUsers() throws Exception {
        // Create profile for user 1
        profileRepository.save(Profile.builder()
                .user(user1)
                .displayName("User 1 Persona")
                .preferredLanguage("te")
                .isDefault(true)
                .build());

        // Create profile for user 2
        profileRepository.save(Profile.builder()
                .user(user2)
                .displayName("User 2 Persona")
                .preferredLanguage("en")
                .isDefault(true)
                .build());

        // User 1 listing profiles
        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].displayName").value("User 1 Persona"));

        // User 2 listing profiles
        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].displayName").value("User 2 Persona"));
    }

    @Test
    @Order(4)
    @DisplayName("TEST 4: User cannot access or modify another user's profile")
    void test4_UserCannotAccessAnotherUsersProfile() throws Exception {
        Profile user1Profile = profileRepository.save(Profile.builder()
                .user(user1)
                .displayName("User 1 Private")
                .preferredLanguage("te")
                .isDefault(true)
                .build());

        // User 2 attempts to get User 1's profile
        mockMvc.perform(get("/api/v1/profiles/" + user1Profile.getId())
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"));

        // User 2 attempts to update User 1's profile
        UpdateProfileRequest updateReq = UpdateProfileRequest.builder()
                .displayName("Hacked Name")
                .build();

        mockMvc.perform(put("/api/v1/profiles/" + user1Profile.getId())
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"));
    }

    @Test
    @Order(5)
    @DisplayName("TEST 5: Profile update works and updates preferredLanguage and avatar")
    void test5_ProfileUpdate_Works() throws Exception {
        Profile profile = profileRepository.save(Profile.builder()
                .user(user1)
                .displayName("Initial Profile")
                .preferredLanguage("en")
                .isDefault(true)
                .build());

        UpdateProfileRequest updateReq = UpdateProfileRequest.builder()
                .displayName("Updated Profile")
                .avatarUrl("https://cdn.communityott.org/avatars/new.png")
                .preferredLanguage("te")
                .build();

        mockMvc.perform(put("/api/v1/profiles/" + profile.getId())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Profile"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.communityott.org/avatars/new.png"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("te"));
    }

    @Test
    @Order(6)
    @DisplayName("TEST 6: Profile deletion removes profile and reassigns default if necessary")
    void test6_ProfileDeletion_Works() throws Exception {
        Profile p1 = profileRepository.save(Profile.builder()
                .user(user1)
                .displayName("Default Profile")
                .preferredLanguage("en")
                .isDefault(true)
                .build());

        Profile p2 = profileRepository.save(Profile.builder()
                .user(user1)
                .displayName("Secondary Profile")
                .preferredLanguage("te")
                .isDefault(false)
                .build());

        mockMvc.perform(delete("/api/v1/profiles/" + p1.getId())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk());

        List<Profile> remaining = profileRepository.findByUserIdOrderByCreatedAtAsc(user1.getId());
        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().getId()).isEqualTo(p2.getId());
        assertThat(remaining.getFirst().isDefault()).isTrue();
    }
}
