package com.stablecoin.payments.merchant.iam;

import com.stablecoin.payments.merchant.iam.infrastructure.persistence.repository.MerchantUserJpaRepository;
import com.stablecoin.payments.merchant.iam.infrastructure.persistence.repository.RoleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("business")
@DisplayName("Permission Check Flow")
class PermissionCheckFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleJpaRepository roleJpa;

    @Autowired
    private MerchantUserJpaRepository userJpa;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID merchantId;
    private UUID adminUserId;
    private UUID viewerUserId;

    @BeforeEach
    void seedTeam() {
        merchantId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        viewerUserId = UUID.randomUUID();

        // Flush Redis permission cache
        var keys = redis.keys("perms:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }

        // Seed roles
        var adminRoleId = UUID.randomUUID();
        var viewerRoleId = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO roles (role_id, merchant_id, role_name, description, is_builtin, is_active, created_at, updated_at)
            VALUES (?, ?, 'ADMIN', 'Administrator', true, true, NOW(), NOW()),
                   (?, ?, 'VIEWER', 'Viewer', true, true, NOW(), NOW())
            """, adminRoleId, merchantId, viewerRoleId, merchantId);

        jdbc.update("INSERT INTO role_permissions (role_permission_id, role_id, permission, created_at) VALUES (?, ?, '*:*', NOW())",
                UUID.randomUUID(), adminRoleId);

        for (var perm : new String[]{"payments:read", "transactions:read", "exports:read", "settings:read"}) {
            jdbc.update("INSERT INTO role_permissions (role_permission_id, role_id, permission, created_at) VALUES (?, ?, ?, NOW())",
                    UUID.randomUUID(), viewerRoleId, perm);
        }

        // Seed users
        var adminSql = """
            INSERT INTO merchant_users (user_id, merchant_id, email, email_hash, full_name, status,
                role_id, mfa_enabled, auth_provider, created_at, updated_at, activated_at, version)
            VALUES (?, ?, ?, ?, 'Admin', 'ACTIVE', ?, false, 'LOCAL', NOW(), NOW(), NOW(), 0)
            """;
        jdbc.update(adminSql,
                adminUserId, merchantId, "admin@test.com".getBytes(), "admin-hash-pc", adminRoleId);
        jdbc.update(adminSql.replace("Admin", "Viewer"),
                viewerUserId, merchantId, "viewer@test.com".getBytes(), "viewer-hash-pc", viewerRoleId);
    }

    @Test
    @DisplayName("should allow ADMIN wildcard permission")
    void shouldAllowAdminWildcardPermission() throws Exception {
        // when
        mockMvc.perform(get("/v1/auth/permissions/check")
                        .param("user_id", adminUserId.toString())
                        .param("merchant_id", merchantId.toString())
                        .param("permission", "payments:write"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed", is(true)))
                .andExpect(jsonPath("$.data.role", is("ADMIN")))
                .andExpect(jsonPath("$.data.via", is("*:*")));
    }

    @Test
    @DisplayName("should allow VIEWER explicit permission")
    void shouldAllowViewerExplicitPermission() throws Exception {
        // when
        mockMvc.perform(get("/v1/auth/permissions/check")
                        .param("user_id", viewerUserId.toString())
                        .param("merchant_id", merchantId.toString())
                        .param("permission", "payments:read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed", is(true)))
                .andExpect(jsonPath("$.data.role", is("VIEWER")));
    }

    @Test
    @DisplayName("should deny VIEWER write permission")
    void shouldDenyViewerWritePermission() throws Exception {
        // when
        mockMvc.perform(get("/v1/auth/permissions/check")
                        .param("user_id", viewerUserId.toString())
                        .param("merchant_id", merchantId.toString())
                        .param("permission", "payments:write"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed", is(false)));
    }

    @Test
    @DisplayName("should populate Redis cache on first permission check and hit cache on second")
    void shouldCachePermissionsInRedis() throws Exception {
        // given — cache is empty
        assertThat(redis.hasKey("perms:" + merchantId + ":" + viewerUserId)).isFalse();

        // when — first check (DB miss → load → cache)
        mockMvc.perform(get("/v1/auth/permissions/check")
                        .param("user_id", viewerUserId.toString())
                        .param("merchant_id", merchantId.toString())
                        .param("permission", "transactions:read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed", is(true)));

        // then — Redis now has the entry
        assertThat(redis.hasKey("perms:" + merchantId + ":" + viewerUserId)).isTrue();

        // when — second check (cache hit)
        mockMvc.perform(get("/v1/auth/permissions/check")
                        .param("user_id", viewerUserId.toString())
                        .param("merchant_id", merchantId.toString())
                        .param("permission", "transactions:read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed", is(true)));
    }

    @Test
    @DisplayName("should return 404 for unknown user")
    void shouldReturn404ForUnknownUser() throws Exception {
        // when
        mockMvc.perform(get("/v1/auth/permissions/check")
                        .param("user_id", UUID.randomUUID().toString())
                        .param("merchant_id", merchantId.toString())
                        .param("permission", "payments:read"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should return JWKS with EC public key")
    void shouldReturnJwks() throws Exception {
        // when
        var result = mockMvc.perform(get("/v1/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andReturn();

        // then — valid JWKS structure
        var body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"keys\"");
        assertThat(body).contains("\"EC\"");
        assertThat(body).contains("\"P-256\"");
    }
}
