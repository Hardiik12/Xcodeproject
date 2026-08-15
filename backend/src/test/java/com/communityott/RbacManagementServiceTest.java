package com.communityott;

import com.communityott.common.exception.LastSuperAdminProtectionException;
import com.communityott.common.exception.RoleAlreadyExistsException;
import com.communityott.common.exception.SystemRoleModificationException;
import com.communityott.permission.dto.PermissionResponse;
import com.communityott.permission.entity.Permission;
import com.communityott.permission.repository.PermissionRepository;
import com.communityott.permission.service.PermissionManagementService;
import com.communityott.role.dto.RoleCreateRequest;
import com.communityott.role.dto.RoleResponse;
import com.communityott.role.dto.RoleUpdateRequest;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RolePermissionRepository;
import com.communityott.role.repository.RoleRepository;
import com.communityott.role.service.RoleManagementService;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import com.communityott.user.service.UserRoleManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class RbacManagementServiceTest {

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
    private PermissionManagementService permissionManagementService;

    @Autowired
    private UserRoleManagementService userRoleManagementService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

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

    private User createTestUser() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .email("mgmt.test." + uniqueSuffix + "@communityott.org")
                .phone("+1555" + (int)(Math.random() * 10000000))
                .displayName("MGMT Test User " + uniqueSuffix)
                .status(UserStatus.ACTIVE)
                .build();
        return userRepository.saveAndFlush(user);
    }

    // ===================================================================
    // ROLE CREATION TESTS
    // ===================================================================

    @Test
    @DisplayName("TEST 1: Create custom role successfully")
    void test1_CreateCustomRoleSuccess() {
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("analyst")
                .description("Can view and export analytics")
                .build();

        RoleResponse response = roleManagementService.createRole(request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("ANALYST");
        assertThat(response.getDescription()).isEqualTo("Can view and export analytics");
        assertThat(response.isSystemRole()).isFalse();
    }

    @Test
    @DisplayName("TEST 2: Duplicate role name fails")
    void test2_DuplicateRoleNameFails() {
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("SUPER_ADMIN")
                .description("Duplicate super admin")
                .build();

        assertThatThrownBy(() -> roleManagementService.createRole(request))
                .isInstanceOf(RoleAlreadyExistsException.class);
    }

    @Test
    @DisplayName("TEST 3: New custom role has is_system_role=false")
    void test3_NewCustomRoleHasIsSystemRoleFalse() {
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("AUDITOR")
                .description("Audit compliance role")
                .build();

        RoleResponse response = roleManagementService.createRole(request);
        assertThat(response.isSystemRole()).isFalse();
    }

    // ===================================================================
    // SYSTEM ROLES PROTECTION TESTS
    // ===================================================================

    @Test
    @DisplayName("TEST 4: SUPER_ADMIN cannot be deleted")
    void test4_SuperAdminCannotBeDeleted() {
        assertThatThrownBy(() -> roleManagementService.deleteRole(superAdminRole.getId()))
                .isInstanceOf(SystemRoleModificationException.class)
                .hasMessageContaining("System role cannot be deleted");
    }

    @Test
    @DisplayName("TEST 5: SUPER_ADMIN cannot be renamed")
    void test5_SuperAdminCannotBeRenamed() {
        RoleUpdateRequest updateRequest = RoleUpdateRequest.builder()
                .name("ULTIMATE_ADMIN")
                .build();

        assertThatThrownBy(() -> roleManagementService.updateRole(superAdminRole.getId(), updateRequest))
                .isInstanceOf(SystemRoleModificationException.class)
                .hasMessageContaining("System roles cannot be renamed");
    }

    @Test
    @DisplayName("TEST 6: Other system roles cannot be deleted")
    void test6_OtherSystemRolesCannotBeDeleted() {
        assertThatThrownBy(() -> roleManagementService.deleteRole(managerRole.getId()))
                .isInstanceOf(SystemRoleModificationException.class);

        assertThatThrownBy(() -> roleManagementService.deleteRole(contentManagerRole.getId()))
                .isInstanceOf(SystemRoleModificationException.class);

        assertThatThrownBy(() -> roleManagementService.deleteRole(userRole.getId()))
                .isInstanceOf(SystemRoleModificationException.class);
    }

    // ===================================================================
    // PERMISSION ASSIGNMENT TESTS
    // ===================================================================

    @Test
    @DisplayName("TEST 7: Assign permission to custom role")
    void test7_AssignPermissionToCustomRole() {
        RoleResponse customRole = roleManagementService.createRole(
                RoleCreateRequest.builder().name("MODERATOR").description("Content Moderator").build());

        Permission permission = permissionRepository.findByName("CONTENT_VIEW").orElseThrow();

        RoleResponse updatedRole = roleManagementService.assignPermissionToRole(customRole.getId(), permission.getId());

        assertThat(updatedRole.getPermissions()).extracting(PermissionResponse::getName)
                .contains("CONTENT_VIEW");
    }

    @Test
    @DisplayName("TEST 8: Duplicate permission assignment is idempotent")
    void test8_DuplicatePermissionAssignmentIsIdempotent() {
        RoleResponse customRole = roleManagementService.createRole(
                RoleCreateRequest.builder().name("REVIEWER").build());

        Permission permission = permissionRepository.findByName("CONTENT_SUBMIT").orElseThrow();

        roleManagementService.assignPermissionToRole(customRole.getId(), permission.getId());
        RoleResponse response = roleManagementService.assignPermissionToRole(customRole.getId(), permission.getId());

        long count = response.getPermissions().stream()
                .filter(p -> "CONTENT_SUBMIT".equals(p.getName()))
                .count();

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("TEST 9: Remove permission from custom role")
    void test9_RemovePermissionFromCustomRole() {
        RoleResponse customRole = roleManagementService.createRole(
                RoleCreateRequest.builder().name("TEMP_ROLE").build());

        Permission permission = permissionRepository.findByName("ANALYTICS_VIEW").orElseThrow();

        roleManagementService.assignPermissionToRole(customRole.getId(), permission.getId());
        RoleResponse roleAfterRemove = roleManagementService.removePermissionFromRole(customRole.getId(), permission.getId());

        assertThat(roleAfterRemove.getPermissions()).extracting(PermissionResponse::getName)
                .doesNotContain("ANALYTICS_VIEW");
    }

    @Test
    @DisplayName("TEST 10: Permission is not deleted when relationship is removed")
    void test10_PermissionIsNotDeletedWhenRelationshipRemoved() {
        RoleResponse customRole = roleManagementService.createRole(
                RoleCreateRequest.builder().name("TEST_ROLE").build());

        Permission permission = permissionRepository.findByName("AUDIT_VIEW").orElseThrow();

        roleManagementService.assignPermissionToRole(customRole.getId(), permission.getId());
        roleManagementService.removePermissionFromRole(customRole.getId(), permission.getId());

        assertThat(permissionRepository.existsById(permission.getId())).isTrue();
    }

    // ===================================================================
    // USER ROLE ASSIGNMENT TESTS
    // ===================================================================

    @Test
    @DisplayName("TEST 11: Assign role to user")
    void test11_AssignRoleToUser() {
        User user = createTestUser();
        userRoleManagementService.assignRoleToUser(user.getId(), userRole.getId());

        Set<String> roles = userRoleManagementService.getUserRoles(user.getId());
        assertThat(roles).contains("USER");
    }

    @Test
    @DisplayName("TEST 12: Duplicate role assignment is idempotent")
    void test12_DuplicateRoleAssignmentIsIdempotent() {
        User user = createTestUser();
        userRoleManagementService.assignRoleToUser(user.getId(), userRole.getId());
        userRoleManagementService.assignRoleToUser(user.getId(), userRole.getId());

        Set<String> roles = userRoleManagementService.getUserRoles(user.getId());
        assertThat(roles).hasSize(1);
    }

    @Test
    @DisplayName("TEST 13: Remove role from user")
    void test13_RemoveRoleFromUser() {
        User user = createTestUser();
        userRoleManagementService.assignRoleToUser(user.getId(), userRole.getId());
        userRoleManagementService.removeRoleFromUser(user.getId(), userRole.getId());

        Set<String> roles = userRoleManagementService.getUserRoles(user.getId());
        assertThat(roles).isEmpty();
    }

    // ===================================================================
    // MULTI-ROLE TESTS
    // ===================================================================

    @Test
    @DisplayName("TEST 14: User with two roles receives union of permissions")
    void test14_MultiRolePermissionUnion() {
        User user = createTestUser();
        userRoleManagementService.assignRoleToUser(user.getId(), userRole.getId());
        userRoleManagementService.assignRoleToUser(user.getId(), contentManagerRole.getId());

        Set<String> effectivePermissions = userRoleManagementService.getUserEffectivePermissions(user.getId());

        assertThat(effectivePermissions).contains(
                "CONTENT_VIEW",
                "VIDEO_VIEW",
                "VIDEO_UPLOAD",
                "VIDEO_EDIT",
                "VIDEO_PROCESS"
        );
    }

    // ===================================================================
    // SUPER ADMIN SAFETY TESTS
    // ===================================================================

    @Test
    @DisplayName("TEST 15: Cannot remove SUPER_ADMIN from the only remaining SUPER_ADMIN user")
    void test15_CannotRemoveLastSuperAdminUser() {
        userRoleRepository.deleteByRoleId(superAdminRole.getId());

        User adminUser = createTestUser();
        userRoleManagementService.assignRoleToUser(adminUser.getId(), superAdminRole.getId());

        assertThatThrownBy(() -> userRoleManagementService.removeRoleFromUser(adminUser.getId(), superAdminRole.getId()))
                .isInstanceOf(LastSuperAdminProtectionException.class);
    }

    @Test
    @DisplayName("TEST 16: Can remove SUPER_ADMIN from a user if another SUPER_ADMIN exists")
    void test16_CanRemoveSuperAdminIfAnotherSuperAdminExists() {
        User admin1 = createTestUser();
        User admin2 = createTestUser();

        userRoleManagementService.assignRoleToUser(admin1.getId(), superAdminRole.getId());
        userRoleManagementService.assignRoleToUser(admin2.getId(), superAdminRole.getId());

        // Count is 2, so removing admin2 should succeed
        userRoleManagementService.removeRoleFromUser(admin2.getId(), superAdminRole.getId());

        Set<String> admin2Roles = userRoleManagementService.getUserRoles(admin2.getId());
        assertThat(admin2Roles).doesNotContain("SUPER_ADMIN");
    }

    @Test
    @DisplayName("TEST 17: SUPER_ADMIN cannot lose its required seeded permissions")
    void test17_SuperAdminCannotLoseSeededPermissions() {
        Permission permission = permissionRepository.findByName("CONTENT_VIEW").orElseThrow();

        assertThatThrownBy(() -> roleManagementService.removePermissionFromRole(superAdminRole.getId(), permission.getId()))
                .isInstanceOf(SystemRoleModificationException.class)
                .hasMessageContaining("Permissions cannot be removed from SUPER_ADMIN");
    }

    // ===================================================================
    // DELETE ROLE TESTS
    // ===================================================================

    @Test
    @DisplayName("TEST 18: Deleting custom role removes role relationships")
    void test18_DeletingCustomRoleRemovesRelationships() {
        RoleResponse customRole = roleManagementService.createRole(
                RoleCreateRequest.builder().name("TEMP_DELETE_ROLE").build());

        Permission permission = permissionRepository.findByName("CONTENT_VIEW").orElseThrow();
        roleManagementService.assignPermissionToRole(customRole.getId(), permission.getId());

        User user = createTestUser();
        userRoleManagementService.assignRoleToUser(user.getId(), customRole.getId());

        // Delete role
        roleManagementService.deleteRole(customRole.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(roleRepository.existsById(customRole.getId())).isFalse();
        assertThat(rolePermissionRepository.findByRoleId(customRole.getId())).isEmpty();
        assertThat(userRoleRepository.findByRoleId(customRole.getId())).isEmpty();
    }

    @Test
    @DisplayName("TEST 19: Deleting custom role does not delete permissions")
    void test19_DeletingCustomRoleDoesNotDeletePermissions() {
        RoleResponse customRole = roleManagementService.createRole(
                RoleCreateRequest.builder().name("TEMP_PERM_ROLE").build());

        Permission permission = permissionRepository.findByName("ANALYTICS_VIEW").orElseThrow();
        roleManagementService.assignPermissionToRole(customRole.getId(), permission.getId());

        roleManagementService.deleteRole(customRole.getId());

        assertThat(permissionRepository.existsById(permission.getId())).isTrue();
    }

    @Test
    @DisplayName("TEST 20: Deleting custom role does not delete users")
    void test20_DeletingCustomRoleDoesNotDeleteUsers() {
        RoleResponse customRole = roleManagementService.createRole(
                RoleCreateRequest.builder().name("TEMP_USER_ROLE").build());

        User user = createTestUser();
        userRoleManagementService.assignRoleToUser(user.getId(), customRole.getId());

        roleManagementService.deleteRole(customRole.getId());

        assertThat(userRepository.existsById(user.getId())).isTrue();
    }
}
