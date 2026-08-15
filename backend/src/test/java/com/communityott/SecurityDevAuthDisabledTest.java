package com.communityott;

import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.service.UserRoleManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "communityott.security.dev-auth-enabled=false")
@Transactional
class SecurityDevAuthDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleManagementService userRoleManagementService;

    private User superAdminUser;

    @BeforeEach
    void setUp() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        superAdminUser = userRepository.save(User.builder()
                .email("test-disabled-devauth@communityott.org")
                .phone("+10000000099")
                .displayName("Disabled DevAuth User")
                .status(UserStatus.ACTIVE)
                .build());
        userRoleManagementService.assignRoleToUser(superAdminUser.getId(), superAdminRole.getId());
    }

    @Test
    @DisplayName("When dev-auth-enabled=false, X-Dev-User-Id header is ignored and request returns HTTP 401")
    void testDevAuthDisabledIgnoresDevHeader() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/admin")
                        .header("X-Dev-User-Id", superAdminUser.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Authentication is required")));
    }
}
