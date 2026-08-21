package com.communityott.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.security.Principal;

/**
 * Custom authenticated user principal representing an authenticated user identity
 * within the SecurityContext.
 *
 * <p>This principal is decoupled from authentication methods (Dev Header, JWT, OAuth2, etc.).
 * It contains only user metadata required for authorization and context tracking without exposing
 * credentials, tokens, or OTP secrets.</p>
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode(of = "userId")
@ToString
public class CommunityOttPrincipal implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String email;
    private final String displayName;
    private final Long sessionId;

    @Override
    public String getName() {
        return displayName != null ? displayName : (email != null ? email : String.valueOf(userId));
    }
}
