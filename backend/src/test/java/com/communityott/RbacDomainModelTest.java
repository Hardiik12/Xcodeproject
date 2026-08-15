package com.communityott;

import com.communityott.permission.entity.Permission;
import com.communityott.role.entity.Role;
import com.communityott.role.entity.RolePermission;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RbacDomainModelTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void testSeededRolesAndPermissionsExistInDatabase() {
        // Query seeded roles
        List<Role> roles = entityManager.createQuery("SELECT r FROM Role r", Role.class).getResultList();
        assertThat(roles).hasSize(4);

        // Query seeded permissions
        List<Permission> permissions = entityManager.createQuery("SELECT p FROM Permission p", Permission.class).getResultList();
        assertThat(permissions).hasSize(35);
    }

    @Test
    void testSuperAdminRoleHasAllPermissions() {
        Role superAdmin = entityManager.createQuery("SELECT r FROM Role r WHERE r.name = :name", Role.class)
                .setParameter("name", "SUPER_ADMIN")
                .getSingleResult();

        assertThat(superAdmin).isNotNull();
        assertThat(superAdmin.isSystemRole()).isTrue();
        
        Set<RolePermission> rolePermissions = superAdmin.getRolePermissions();
        assertThat(rolePermissions).hasSize(35);
    }

    @Test
    void testUserRoleAndPermissionRelationships() {
        // Create user
        User user = User.builder()
                .email("test.user@communityott.org")
                .displayName("Test Community User")
                .status(UserStatus.ACTIVE)
                .build();

        entityManager.persist(user);
        entityManager.flush();

        // Fetch USER role
        Role userRole = entityManager.createQuery("SELECT r FROM Role r WHERE r.name = :name", Role.class)
                .setParameter("name", "USER")
                .getSingleResult();

        // Map User to Role
        UserRole mapping = new UserRole(user, userRole);
        user.getUserRoles().add(mapping);
        entityManager.persist(mapping);
        entityManager.flush();

        // Verify entity navigation User -> UserRole -> Role -> RolePermission -> Permission
        User retrievedUser = entityManager.find(User.class, user.getId());
        assertThat(retrievedUser).isNotNull();
        assertThat(retrievedUser.getUserRoles()).hasSize(1);

        Role fetchedRole = retrievedUser.getUserRoles().iterator().next().getRole();
        assertThat(fetchedRole.getName()).isEqualTo("USER");
        assertThat(fetchedRole.getRolePermissions()).hasSize(2);
    }
}
