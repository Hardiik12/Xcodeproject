package com.communityott;

import com.communityott.auth.delivery.DevelopmentEmailOtpDeliveryProvider;
import com.communityott.auth.delivery.DevelopmentSmsOtpDeliveryProvider;
import com.communityott.auth.dto.OtpRequestDto;
import com.communityott.auth.dto.OtpVerifyRequestDto;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.AuthSession;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.repository.AuthSessionRepository;
import com.communityott.common.rbac.RbacService;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthenticationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private DevelopmentEmailOtpDeliveryProvider emailProvider;

    @Autowired
    private DevelopmentSmsOtpDeliveryProvider smsProvider;

    @Autowired
    private RbacService rbacService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        emailProvider.clearTestStore();
        smsProvider.clearTestStore();
        Set<String> keys = redisTemplate.keys("communityott:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String generateUniqueEmail(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @Test
    @Order(1)
    @DisplayName("TEST 1: Request OTP for LOGIN on existing user succeeds (200 OK)")
    void test1_RequestOtpLoginExistingUserSucceeds() throws Exception {
        String email = generateUniqueEmail("login_user");
        User user = userRepository.save(User.builder()
                .email(email)
                .displayName("Login User")
                .status(UserStatus.ACTIVE)
                .build());

        OtpRequestDto request = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .purpose(OtpPurpose.LOGIN)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OTP sent successfully"))
                .andExpect(jsonPath("$.data.requestId").isNotEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("TEST 2: Request OTP for LOGIN on non-existent user returns 404 NOT_FOUND")
    void test2_RequestOtpLoginUnknownUserReturns404() throws Exception {
        String email = generateUniqueEmail("nonexistent");
        OtpRequestDto request = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .purpose(OtpPurpose.LOGIN)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_USER_NOT_FOUND"));
    }

    @Test
    @Order(3)
    @DisplayName("TEST 3: Request OTP for REGISTRATION on new user succeeds (200 OK)")
    void test3_RequestOtpRegistrationNewUserSucceeds() throws Exception {
        String email = generateUniqueEmail("new_reg");
        OtpRequestDto request = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .purpose(OtpPurpose.REGISTRATION)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").isNotEmpty());
    }

    @Test
    @Order(4)
    @DisplayName("TEST 4: Request OTP for REGISTRATION on existing user returns 409 CONFLICT")
    void test4_RequestOtpRegistrationExistingUserReturns409() throws Exception {
        String email = generateUniqueEmail("already_exists");
        userRepository.save(User.builder()
                .email(email)
                .displayName("Existing User")
                .status(UserStatus.ACTIVE)
                .build());

        OtpRequestDto request = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .purpose(OtpPurpose.REGISTRATION)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REGISTRATION_NOT_ALLOWED"));
    }

    @Test
    @Order(5)
    @DisplayName("TEST 5: Request OTP for ACCOUNT_RECOVERY on existing user succeeds")
    void test5_RequestOtpAccountRecoveryExistingUserSucceeds() throws Exception {
        String email = generateUniqueEmail("recover_me");
        userRepository.save(User.builder()
                .email(email)
                .displayName("Recover User")
                .status(UserStatus.ACTIVE)
                .build());

        OtpRequestDto request = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .purpose(OtpPurpose.ACCOUNT_RECOVERY)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(6)
    @DisplayName("TEST 6: Request OTP for ACCOUNT_RECOVERY on non-existent user returns 400 BAD_REQUEST")
    void test6_RequestOtpAccountRecoveryUnknownUserReturns400() throws Exception {
        String email = generateUniqueEmail("nobody");
        OtpRequestDto request = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .purpose(OtpPurpose.ACCOUNT_RECOVERY)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_RECOVERY_NOT_ALLOWED"));
    }

    @Test
    @Order(7)
    @DisplayName("TEST 7: Request OTP for PHONE on new user succeeds via SMS provider")
    void test7_RequestOtpPhoneSucceeds() throws Exception {
        String phone = "+1555" + (1000000 + (int)(Math.random() * 8999999));
        OtpRequestDto request = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.PHONE)
                .identifier(phone)
                .purpose(OtpPurpose.REGISTRATION)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(smsProvider.getLastDeliveredOtp(phone)).isNotNull().matches("^\\d{6}$");
    }

    @Test
    @Order(8)
    @DisplayName("TEST 8: Request OTP with invalid email format returns 400 BAD_REQUEST")
    void test8_RequestOtpInvalidEmailFormatReturns400() throws Exception {
        OtpRequestDto request = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier("invalid-email-address")
                .purpose(OtpPurpose.LOGIN)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OTP_IDENTIFIER_INVALID"));
    }

    @Test
    @Order(9)
    @DisplayName("TEST 9: Request OTP on SUSPENDED user returns 403 FORBIDDEN")
    void test9_RequestOtpSuspendedUserReturns403() throws Exception {
        String email = generateUniqueEmail("suspended");
        userRepository.save(User.builder()
                .email(email)
                .displayName("Suspended User")
                .status(UserStatus.SUSPENDED)
                .build());

        OtpRequestDto request = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .purpose(OtpPurpose.LOGIN)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_SUSPENDED"));
    }

    @Test
    @Order(10)
    @DisplayName("TEST 10: Request OTP on DELETED user returns 403 FORBIDDEN")
    void test10_RequestOtpDeletedUserReturns403() throws Exception {
        String email = generateUniqueEmail("deleted");
        userRepository.save(User.builder()
                .email(email)
                .displayName("Deleted User")
                .status(UserStatus.DELETED)
                .build());

        OtpRequestDto request = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .purpose(OtpPurpose.LOGIN)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_DELETED"));
    }

    @Test
    @Order(11)
    @DisplayName("TEST 11: Verify OTP for LOGIN on ACTIVE user creates AuthSession and returns 200 OK")
    void test11_VerifyOtpLoginSucceedsAndCreatesSession() throws Exception {
        String email = generateUniqueEmail("verified_login");
        User user = userRepository.save(User.builder()
                .email(email)
                .displayName("Verified Login")
                .status(UserStatus.ACTIVE)
                .build());

        // Step 1: Request OTP
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OtpRequestDto.builder()
                                .identifierType(AuthIdentifierType.EMAIL)
                                .identifier(email)
                                .purpose(OtpPurpose.LOGIN)
                                .build())))
                .andExpect(status().isOk());

        String otp = emailProvider.getLastDeliveredOtp(email);
        assertThat(otp).isNotNull();

        // Step 2: Verify OTP
        OtpVerifyRequestDto verifyRequest = OtpVerifyRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .otp(otp)
                .purpose(OtpPurpose.LOGIN)
                .deviceId("iphone-15-pro-uid")
                .deviceName("Hardik's iPhone")
                .platform(Platform.IOS)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.user.id").value(user.getId()))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.session.id").isNotEmpty())
                .andExpect(jsonPath("$.data.session.platform").value("IOS"))
                .andExpect(jsonPath("$.data.session.deviceId").value("iphone-15-pro-uid"));

        List<AuthSession> sessions = authSessionRepository.findByUserId(user.getId());
        assertThat(sessions).isNotEmpty();
        assertThat(sessions.get(0).getPlatform()).isEqualTo(Platform.IOS);
    }

    @Test
    @Order(12)
    @DisplayName("TEST 12: Verify OTP with incorrect code returns 400 BAD_REQUEST")
    void test12_VerifyOtpIncorrectCodeReturns400() throws Exception {
        String email = generateUniqueEmail("wrong_code");
        userRepository.save(User.builder()
                .email(email)
                .displayName("Wrong Code User")
                .status(UserStatus.ACTIVE)
                .build());

        // Request OTP
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OtpRequestDto.builder()
                                .identifierType(AuthIdentifierType.EMAIL)
                                .identifier(email)
                                .purpose(OtpPurpose.LOGIN)
                                .build())))
                .andExpect(status().isOk());

        // Submit wrong OTP
        OtpVerifyRequestDto verifyRequest = OtpVerifyRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .otp("000000")
                .purpose(OtpPurpose.LOGIN)
                .deviceId("wrong-otp-device-uid")
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
    }

    @Test
    @Order(13)
    @DisplayName("TEST 13: Verify OTP for REGISTRATION creates new User, assigns USER role, and creates AuthSession")
    void test13_VerifyOtpRegistrationCreatesUserWithUserRole() throws Exception {
        String email = generateUniqueEmail("auto_register");

        // Step 1: Request OTP
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OtpRequestDto.builder()
                                .identifierType(AuthIdentifierType.EMAIL)
                                .identifier(email)
                                .purpose(OtpPurpose.REGISTRATION)
                                .build())))
                .andExpect(status().isOk());

        String otp = emailProvider.getLastDeliveredOtp(email);
        assertThat(otp).isNotNull();

        // Step 2: Verify OTP
        OtpVerifyRequestDto verifyRequest = OtpVerifyRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .otp(otp)
                .purpose(OtpPurpose.REGISTRATION)
                .deviceId("pixel-8-pro-uid")
                .deviceName("Android Device")
                .platform(Platform.ANDROID)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.session.platform").value("ANDROID"));

        User createdUser = userRepository.findByEmail(email).orElse(null);
        assertThat(createdUser).isNotNull();
        assertThat(createdUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

        Set<String> roles = rbacService.getUserRoles(createdUser.getId());
        assertThat(roles).containsExactly("USER");
        assertThat(roles).doesNotContain("SUPER_ADMIN", "MANAGER", "CONTENT_MANAGER");
    }

    @Test
    @Order(14)
    @DisplayName("TEST 14: Verify OTP for ACCOUNT_RECOVERY succeeds for active user")
    void test14_VerifyOtpAccountRecoverySucceeds() throws Exception {
        String email = generateUniqueEmail("recover_success");
        User user = userRepository.save(User.builder()
                .email(email)
                .displayName("Recover Success")
                .status(UserStatus.ACTIVE)
                .build());

        // Step 1: Request OTP
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OtpRequestDto.builder()
                                .identifierType(AuthIdentifierType.EMAIL)
                                .identifier(email)
                                .purpose(OtpPurpose.ACCOUNT_RECOVERY)
                                .build())))
                .andExpect(status().isOk());

        String otp = emailProvider.getLastDeliveredOtp(email);
        assertThat(otp).isNotNull();

        // Step 2: Verify OTP
        OtpVerifyRequestDto verifyRequest = OtpVerifyRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(email)
                .otp(otp)
                .purpose(OtpPurpose.ACCOUNT_RECOVERY)
                .deviceId("recovery-web-device-uid")
                .platform(Platform.WEB)
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.id").value(user.getId()));
    }
}
