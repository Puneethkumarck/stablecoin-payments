package com.stablecoin.payments.merchant.iam;

import com.jayway.jsonpath.JsonPath;
import com.stablecoin.payments.merchant.iam.domain.team.EmailHasher;
import com.stablecoin.payments.merchant.iam.domain.team.UserSessionRepository;
import com.stablecoin.payments.merchant.iam.infrastructure.persistence.repository.UserSessionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Business test for session revocation flows.
 * Uses real JWT authentication (no TestSecurityConfig) to validate the full login → logout → refresh chain.
 */
@Tag("business")
@DisplayName("Session Revocation Flow")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
class SessionRevocationFlowTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("s13_merchant_iam")
                    .withUsername("test")
                    .withPassword("test");

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @SuppressWarnings("resource")
    static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.24"))
                    .withExposedPorts(1025, 8025);

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        POSTGRES.start();
        KAFKA.start();
        MAILPIT.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserSessionJpaRepository sessionJpa;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private EmailHasher emailHasher;

    private static final String EMAIL = "admin@acme.com";
    private static final String PASSWORD = "password123";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(PASSWORD);

    private UUID merchantId;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.execute("DELETE FROM user_sessions");
        jdbc.execute("DELETE FROM invitations");
        jdbc.execute("DELETE FROM merchant_users");
        jdbc.execute("DELETE FROM role_permissions");
        jdbc.execute("DELETE FROM roles");
        jdbc.execute("DELETE FROM permission_audit_log");

        merchantId = UUID.randomUUID();
        var adminRoleId = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO roles (role_id, merchant_id, role_name, description, is_builtin, is_active, created_at, updated_at)
            VALUES (?, ?, 'ADMIN', 'Administrator', true, true, NOW(), NOW())
            """, adminRoleId, merchantId);

        jdbc.update("""
            INSERT INTO role_permissions (role_permission_id, role_id, permission, created_at)
            VALUES (?, ?, '*:*', NOW())
            """, UUID.randomUUID(), adminRoleId);

        jdbc.update("""
            INSERT INTO merchant_users (user_id, merchant_id, email, email_hash, full_name, password_hash,
                status, role_id, mfa_enabled, auth_provider, created_at, updated_at, activated_at, version)
            VALUES (?, ?, ?, ?, 'Admin User', ?, 'ACTIVE', ?, false, 'LOCAL', NOW(), NOW(), NOW(), 0)
            """,
                UUID.randomUUID(), merchantId,
                EMAIL.getBytes(), emailHasher.hash(EMAIL),
                PASSWORD_HASH, adminRoleId);
    }

    private LoginTokens login() throws Exception {
        var result = mockMvc.perform(post("/v1/merchants/{id}/auth/login", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        var json = result.getResponse().getContentAsString();
        return new LoginTokens(
                JsonPath.read(json, "$.data.accessToken"),
                JsonPath.read(json, "$.data.refreshToken"));
    }

    @Nested
    @DisplayName("Session persistence on login")
    class SessionPersistence {

        @Test
        @DisplayName("login should persist a user session in the database")
        void shouldPersistSessionOnLogin() throws Exception {
            assertThat(sessionJpa.findAll()).isEmpty();

            login();

            var sessions = sessionJpa.findAll();
            assertThat(sessions).hasSize(1);
            var session = sessions.getFirst();
            assertThat(session.isRevoked()).isFalse();
            assertThat(session.getExpiresAt()).isNotNull();
            assertThat(session.getMerchantId()).isEqualTo(merchantId);
        }
    }

    @Nested
    @DisplayName("Logout invalidates refresh token")
    class LogoutInvalidatesRefresh {

        @Test
        @DisplayName("login → logout → refresh token rejected")
        void shouldRejectRefreshAfterLogout() throws Exception {
            var tokens = login();

            // Logout using access token
            mockMvc.perform(post("/v1/auth/logout")
                            .header("Authorization", "Bearer " + tokens.accessToken()))
                    .andExpect(status().isNoContent());

            // Verify session is revoked in DB
            assertThat(sessionJpa.findAll()).allMatch(s -> s.isRevoked());

            // Refresh should be rejected — session is revoked
            mockMvc.perform(post("/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken": "%s"}
                                    """.formatted(tokens.refreshToken())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Merchant suspension cuts off refresh")
    class MerchantSuspensionCutsOffRefresh {

        @Test
        @DisplayName("merchant suspended → refresh token rejected")
        void shouldRejectRefreshAfterMerchantSuspension() throws Exception {
            var tokens = login();

            // Simulate merchant suspension — revokes all sessions for the merchant
            sessionRepository.revokeAllByMerchantId(merchantId, "merchant_suspended");

            // Verify sessions revoked
            assertThat(sessionJpa.findAll()).allMatch(s -> s.isRevoked());

            // Refresh should be rejected
            mockMvc.perform(post("/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken": "%s"}
                                    """.formatted(tokens.refreshToken())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Valid session allows refresh")
    class ValidSessionAllowsRefresh {

        @Test
        @DisplayName("login → refresh token succeeds while session is valid")
        void shouldAllowRefreshWithValidSession() throws Exception {
            var tokens = login();

            mockMvc.perform(post("/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken": "%s"}
                                    """.formatted(tokens.refreshToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        }
    }

    private record LoginTokens(String accessToken, String refreshToken) {}
}
