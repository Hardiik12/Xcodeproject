package com.communityott;

import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import com.communityott.user.service.UserRoleManagementService;
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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class SecurityRbacAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleManagementService userRoleManagementService;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private User superAdminUser;
    private User managerUser;
    private User contentManagerUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        // Create deterministic test users for each role
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        Role managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        Role contentManagerRole = roleRepository.findByName("CONTENT_MANAGER").orElseThrow();
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        superAdminUser = userRepository.save(User.builder()
                .email("test-super-admin@communityott.org")
                .phone("+10000000001")
                .displayName("Test Super Admin")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleManagementService.assignRoleToUser(superAdminUser.getId(), superAdminRole.getId());

        managerUser = userRepository.save(User.builder()
                .email("test-manager@communityott.org")
                .phone("+10000000002")
                .displayName("Test Manager")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleManagementService.assignRoleToUser(managerUser.getId(), managerRole.getId());

        contentManagerUser = userRepository.save(User.builder()
                .email("test-content-manager@communityott.org")
                .phone("+10000000003")
                .displayName("Test Content Manager")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleManagementService.assignRoleToUser(contentManagerUser.getId(), contentManagerRole.getId());

        regularUser = userRepository.save(User.builder()
                .email("test-user@communityott.org")
                .phone("+10000000004")
                .displayName("Test Regular User")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleManagementService.assignRoleToUser(regularUser.getId(), userRole.getId());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (superAdminUser != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(superAdminUser.getId())).toList());
            userRepository.delete(superAdminUser);
        }
        if (managerUser != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(managerUser.getId())).toList());
            userRepository.delete(managerUser);
        }
        if (contentManagerUser != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(contentManagerUser.getId())).toList());
            userRepository.delete(contentManagerUser);
        }
        if (regularUser != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(regularUser.getId())).toList());
            userRepository.delete(regularUser);
        }
    }

    // =========================================================================
    // AUTHENTICATION TESTS (1-3)
    // =========================================================================

    @Test
    @DisplayName("1. Request without authentication header returns HTTP 401 Unauthorized")
    void test1_RequestWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/user-view"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Authentication is required")));
    }

    @Test
    @DisplayName("2. Request with invalid X-Dev-User-Id header returns HTTP 401 Unauthorized")
    void test2_InvalidDevUserIdReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/user-view")
                        .header("X-Dev-User-Id", "999999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("3. Request with valid X-Dev-User-Id header successfully authenticates user")
    void test3_ValidDevUserIdAuthenticatesUser() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/admin")
                        .header("X-Dev-User-Id", superAdminUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.requiredPermission", is("ROLE_CREATE")));
    }

    // =========================================================================
    // AUTHORIZATION TESTS (4-10)
    // =========================================================================

    @Test
    @DisplayName("4. USER role accessing USER_VIEW returns HTTP 403 Forbidden")
    void test4_UserRoleAccessingUserViewReturns403() throws Exception {
        // USER role is seeded with only CONTENT_VIEW and VIDEO_VIEW
        mockMvc.perform(get("/api/v1/rbac/test/user-view")
                        .header("X-Dev-User-Id", regularUser.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")))
                .andExpect(jsonPath("$.error.message", is("You do not have permission to perform this action")));
    }

    @Test
    @DisplayName("5. CONTENT_MANAGER accessing VIDEO_UPLOAD returns HTTP 200 OK")
    void test5_ContentManagerAccessingVideoUploadReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/video-upload")
                        .header("X-Dev-User-Id", contentManagerUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.requiredPermission", is("VIDEO_UPLOAD")));
    }

    @Test
    @DisplayName("6. CONTENT_MANAGER accessing ROLE_CREATE returns HTTP 403 Forbidden")
    void test6_ContentManagerAccessingRoleCreateReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/admin")
                        .header("X-Dev-User-Id", contentManagerUser.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("7. MANAGER accessing ANALYTICS_VIEW returns HTTP 200 OK")
    void test7_ManagerAccessingAnalyticsViewReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/analytics")
                        .header("X-Dev-User-Id", managerUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.requiredPermission", is("ANALYTICS_VIEW")));
    }

    @Test
    @DisplayName("8. MANAGER accessing VIDEO_UPLOAD returns HTTP 403 Forbidden")
    void test8_ManagerAccessingVideoUploadReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/video-upload")
                        .header("X-Dev-User-Id", managerUser.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("9. SUPER_ADMIN accessing ROLE_CREATE returns HTTP 200 OK")
    void test9_SuperAdminAccessingRoleCreateReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/admin")
                        .header("X-Dev-User-Id", superAdminUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.requiredPermission", is("ROLE_CREATE")));
    }

    @Test
    @DisplayName("10. SUPER_ADMIN accessing VIDEO_PUBLISH returns HTTP 200 OK")
    void test10_SuperAdminAccessingVideoPublishReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/video-publish")
                        .header("X-Dev-User-Id", superAdminUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.requiredPermission", is("VIDEO_PUBLISH")));
    }

    // =========================================================================
    // PUBLIC ENDPOINTS TESTS (11-12)
    // =========================================================================

    @Test
    @DisplayName("11. Public custom health endpoint /api/v1/health allows unauthenticated access")
    void test11_HealthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("UP")));
    }

    @Test
    @DisplayName("12. Public Actuator health endpoint /actuator/health allows unauthenticated access")
    void test12_ActuatorHealthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    // =========================================================================
    // SECURITY STRUCTURE & ERROR STRUCTURE TESTS (13-15)
    // =========================================================================

    @Test
    @DisplayName("13. Non-numeric X-Dev-User-Id does not authenticate and returns HTTP 401")
    void test13_NonNumericDevUserIdHeaderFailsToAuthenticate() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/user-view")
                        .header("X-Dev-User-Id", "invalid-id-string"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("14. Authenticated user missing permission returns exact HTTP 403 JSON structure")
    void test14_AuthenticatedUserMissingPermissionReturnsExactForbiddenJson() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/admin")
                        .header("X-Dev-User-Id", regularUser.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")))
                .andExpect(jsonPath("$.error.message", is("You do not have permission to perform this action")));
    }

    @Test
    @DisplayName("15. Unauthenticated request returns exact HTTP 401 JSON structure")
    void test15_UnauthenticatedRequestReturnsExactUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/user-view")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Authentication is required")));
    }
}
