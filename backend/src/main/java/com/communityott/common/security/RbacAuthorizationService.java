package com.communityott.common.security;

import com.communityott.common.rbac.RbacService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Spring Security authorization evaluator component used in method-level security expressions.
 *
 * <p>Usage in Controllers:</p>
 * <pre>
 * {@code @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_UPLOAD')")}
 * </pre>
 *
 * <p>Integrates directly with {@link RbacService} to evaluate fine-grained permission grants
 * for the authenticated {@link CommunityOttPrincipal}.</p>
 */
@Slf4j
@Component("rbacAuthorization")
@RequiredArgsConstructor
public class RbacAuthorizationService {

    private final RbacService rbacService;

    /**
     * Determines whether the authenticated user possesses the specified permission.
     *
     * @param authentication the current Spring Security authentication object
     * @param permissionName the permission string to check (e.g., 'VIDEO_UPLOAD')
     * @return {@code true} if user is authenticated and possesses the permission; {@code false} otherwise
     */
    public boolean hasPermission(Authentication authentication, String permissionName) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.trace("Authorization denied: Authentication is null or unauthenticated");
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CommunityOttPrincipal ottPrincipal)) {
            log.trace("Authorization denied: Principal is not CommunityOttPrincipal");
            return false;
        }

        if (ottPrincipal.getUserId() == null) {
            log.trace("Authorization denied: CommunityOttPrincipal has null userId");
            return false;
        }

        if (permissionName == null || permissionName.isBlank()) {
            log.trace("Authorization denied: Permission name is blank");
            return false;
        }

        boolean hasPerm = rbacService.hasPermission(ottPrincipal.getUserId(), permissionName);
        log.debug("Authorization check: User ID {} for permission '{}' -> {}",
                ottPrincipal.getUserId(), permissionName, hasPerm);

        return hasPerm;
    }

    /**
     * Determines whether the authenticated user possesses any of the specified permissions.
     *
     * @param authentication the current Spring Security authentication object
     * @param permissionNames one or more permission strings to check
     * @return {@code true} if user possesses at least one of the permissions; {@code false} otherwise
     */
    public boolean hasAnyPermission(Authentication authentication, String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0) {
            return false;
        }
        for (String perm : permissionNames) {
            if (hasPermission(authentication, perm)) {
                return true;
            }
        }
        return false;
    }
}
