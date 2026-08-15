package com.communityott;

import com.communityott.common.rbac.RbacService;
import com.communityott.common.rbac.SystemPermissions;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RbacServiceTest {

    @Autowired
    private RbacService rbacService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private Role superAdminRole;
    private Role managerRole;
    private Role contentManagerRole;
    private Role userRole;

    @BeforeEach
    void setUp() {
        superAdminRole = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        contentManagerRole = roleRepository.findByName("CONTENT_MANAGER").orElseThrow();
        userRole = roleRepository.findByName("USER").orElseThrow();
    }

    private User createTestUserWithRole(Role... roles) {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .email("rbac.test." + uniqueSuffix + "@communityott.org")
                .phone("+1555" + (int)(Math.random() * 10000000))
                .displayName("RBAC Test User " + uniqueSuffix)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.saveAndFlush(user);

        for (Role role : roles) {
            UserRole userRoleMapping = new UserRole(savedUser, role);
            userRoleRepository.save(userRoleMapping);
        }
        userRoleRepository.flush();
        return savedUser;
    }

    @Test
    @DisplayName("TEST 1: A user with USER role has CONTENT_VIEW permission")
    void test1_UserHasContentView() {
        User user = createTestUserWithRole(userRole);
        boolean hasPermission = rbacService.hasPermission(user.getId(), SystemPermissions.CONTENT_VIEW);
        assertThat(hasPermission).isTrue();
    }

    @Test
    @DisplayName("TEST 2: A user with USER role does NOT have VIDEO_UPLOAD permission")
    void test2_UserDoesNotHaveVideoUpload() {
        User user = createTestUserWithRole(userRole);
        boolean hasPermission = rbacService.hasPermission(user.getId(), SystemPermissions.VIDEO_UPLOAD);
        assertThat(hasPermission).isFalse();
    }

    @Test
    @DisplayName("TEST 3: A CONTENT_MANAGER has VIDEO_UPLOAD permission")
    void test3_ContentManagerHasVideoUpload() {
        User user = createTestUserWithRole(contentManagerRole);
        boolean hasPermission = rbacService.hasPermission(user.getId(), SystemPermissions.VIDEO_UPLOAD);
        assertThat(hasPermission).isTrue();
    }

    @Test
    @DisplayName("TEST 4: A CONTENT_MANAGER does NOT have USER_DELETE permission")
    void test4_ContentManagerDoesNotHaveUserDelete() {
        User user = createTestUserWithRole(contentManagerRole);
        boolean hasPermission = rbacService.hasPermission(user.getId(), SystemPermissions.USER_DELETE);
        assertThat(hasPermission).isFalse();
    }

    @Test
    @DisplayName("TEST 5: A MANAGER has ANALYTICS_VIEW permission")
    void test5_ManagerHasAnalyticsView() {
        User user = createTestUserWithRole(managerRole);
        boolean hasPermission = rbacService.hasPermission(user.getId(), SystemPermissions.ANALYTICS_VIEW);
        assertThat(hasPermission).isTrue();
    }

    @Test
    @DisplayName("TEST 6: A MANAGER has ANALYTICS_EXPORT permission")
    void test6_ManagerHasAnalyticsExport() {
        User user = createTestUserWithRole(managerRole);
        boolean hasPermission = rbacService.hasPermission(user.getId(), SystemPermissions.ANALYTICS_EXPORT);
        assertThat(hasPermission).isTrue();
    }

    @Test
    @DisplayName("TEST 7: SUPER_ADMIN has every seeded permission (35 permissions)")
    void test7_SuperAdminHasEveryPermission() {
        User user = createTestUserWithRole(superAdminRole);
        Set<String> permissions = rbacService.getUserPermissions(user.getId());
        
        assertThat(permissions).hasSize(35);
        assertThat(rbacService.hasPermission(user.getId(), SystemPermissions.USER_DELETE)).isTrue();
        assertThat(rbacService.hasPermission(user.getId(), SystemPermissions.SYSTEM_SETTINGS_UPDATE)).isTrue();
    }

    @Test
    @DisplayName("TEST 8: A user with multiple roles receives union of permissions from all assigned roles")
    void test8_MultiRolePermissionUnion() {
        User multiRoleUser = createTestUserWithRole(userRole, contentManagerRole);
        Set<String> permissions = rbacService.getUserPermissions(multiRoleUser.getId());
        Set<String> roles = rbacService.getUserRoles(multiRoleUser.getId());

        assertThat(roles).containsExactlyInAnyOrder("USER", "CONTENT_MANAGER");

        // CONTENT_MANAGER has 9 permissions, USER has 2 permissions (both contained in CONTENT_MANAGER)
        assertThat(permissions).hasSize(9);
        assertThat(permissions).contains(
                SystemPermissions.CONTENT_VIEW,
                SystemPermissions.VIDEO_VIEW,
                SystemPermissions.VIDEO_UPLOAD,
                SystemPermissions.VIDEO_EDIT,
                SystemPermissions.VIDEO_PROCESS,
                SystemPermissions.VIDEO_RETRY
        );

        assertThat(rbacService.hasPermission(multiRoleUser.getId(), SystemPermissions.VIDEO_UPLOAD)).isTrue();
        assertThat(rbacService.hasPermission(multiRoleUser.getId(), SystemPermissions.USER_DELETE)).isFalse();
    }

    @Test
    @DisplayName("TEST 9: A nonexistent permission returns false")
    void test9_NonexistentPermissionReturnsFalse() {
        User user = createTestUserWithRole(superAdminRole);
        boolean hasPermission = rbacService.hasPermission(user.getId(), "NON_EXISTENT_PERMISSION_XYZ");
        assertThat(hasPermission).isFalse();
    }

    @Test
    @DisplayName("TEST 10: A user with no roles has no permissions")
    void test10_UserWithoutRolesHasNoPermissions() {
        User userNoRoles = createTestUserWithRole(); // No roles assigned
        
        Set<String> permissions = rbacService.getUserPermissions(userNoRoles.getId());
        Set<String> roles = rbacService.getUserRoles(userNoRoles.getId());

        assertThat(roles).isEmpty();
        assertThat(permissions).isEmpty();
        assertThat(rbacService.hasPermission(userNoRoles.getId(), SystemPermissions.CONTENT_VIEW)).isFalse();
    }
}
