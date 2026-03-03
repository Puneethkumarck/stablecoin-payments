package com.stablecoin.payments.merchant.iam.domain.team.model.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionSetTest {

    @Nested
    class Has {

        @Test
        void exact_match_returns_true() {
            PermissionSet set = PermissionSet.of(List.of(
                    Permission.of("payments", "read"),
                    Permission.of("payments", "write")));

            assertThat(set.has(Permission.of("payments", "read"))).isTrue();
            assertThat(set.has(Permission.of("payments", "write"))).isTrue();
        }

        @Test
        void no_match_returns_false() {
            PermissionSet set = PermissionSet.of(List.of(
                    Permission.of("payments", "read")));

            assertThat(set.has(Permission.of("team", "read"))).isFalse();
        }

        @Test
        void full_wildcard_matches_any_permission() {
            PermissionSet set = PermissionSet.of(List.of(
                    Permission.of("*", "*")));

            assertThat(set.has(Permission.of("payments", "read"))).isTrue();
            assertThat(set.has(Permission.of("team", "manage"))).isTrue();
            assertThat(set.has(Permission.of("api_keys", "write"))).isTrue();
        }

        @Test
        void namespace_wildcard_matches_same_namespace() {
            PermissionSet set = PermissionSet.of(List.of(
                    Permission.of("payments", "*")));

            assertThat(set.has(Permission.of("payments", "read"))).isTrue();
            assertThat(set.has(Permission.of("payments", "cancel"))).isTrue();
            assertThat(set.has(Permission.of("team", "read"))).isFalse();
        }

        @Test
        void empty_set_matches_nothing() {
            PermissionSet set = PermissionSet.empty();

            assertThat(set.has(Permission.of("payments", "read"))).isFalse();
        }
    }

    @Nested
    class HasAll {

        @Test
        void returns_true_when_all_permissions_present() {
            PermissionSet set = PermissionSet.of(List.of(
                    Permission.of("payments", "read"),
                    Permission.of("payments", "write"),
                    Permission.of("team", "read")));

            assertThat(set.hasAll(List.of(
                    Permission.of("payments", "read"),
                    Permission.of("team", "read")))).isTrue();
        }

        @Test
        void returns_false_when_one_permission_missing() {
            PermissionSet set = PermissionSet.of(List.of(
                    Permission.of("payments", "read")));

            assertThat(set.hasAll(List.of(
                    Permission.of("payments", "read"),
                    Permission.of("team", "read")))).isFalse();
        }

        @Test
        void wildcard_satisfies_all() {
            PermissionSet set = PermissionSet.of(List.of(
                    Permission.of("*", "*")));

            assertThat(set.hasAll(List.of(
                    Permission.of("payments", "read"),
                    Permission.of("team", "manage"),
                    Permission.of("roles", "write")))).isTrue();
        }
    }

    @Nested
    class HasAny {

        @Test
        void returns_true_when_at_least_one_matches() {
            PermissionSet set = PermissionSet.of(List.of(
                    Permission.of("payments", "read")));

            assertThat(set.hasAny(List.of(
                    Permission.of("payments", "read"),
                    Permission.of("team", "manage")))).isTrue();
        }

        @Test
        void returns_false_when_none_match() {
            PermissionSet set = PermissionSet.of(List.of(
                    Permission.of("payments", "read")));

            assertThat(set.hasAny(List.of(
                    Permission.of("team", "read"),
                    Permission.of("roles", "manage")))).isFalse();
        }
    }

    @Nested
    class BuiltInRolePermissions {

        @Test
        void admin_has_full_access() {
            PermissionSet adminPerms = PermissionSet.of(BuiltInRole.ADMIN.defaultPermissions());

            assertThat(adminPerms.has(Permission.of("payments", "read"))).isTrue();
            assertThat(adminPerms.has(Permission.of("team", "manage"))).isTrue();
            assertThat(adminPerms.has(Permission.of("anything", "anything"))).isTrue();
        }

        @Test
        void payments_operator_has_payment_permissions() {
            PermissionSet perms = PermissionSet.of(BuiltInRole.PAYMENTS_OPERATOR.defaultPermissions());

            assertThat(perms.has(Permission.of("payments", "read"))).isTrue();
            assertThat(perms.has(Permission.of("payments", "write"))).isTrue();
            assertThat(perms.has(Permission.of("payments", "cancel"))).isTrue();
            assertThat(perms.has(Permission.of("team", "read"))).isTrue();
            assertThat(perms.has(Permission.of("team", "manage"))).isFalse();
            assertThat(perms.has(Permission.of("roles", "manage"))).isFalse();
        }

        @Test
        void viewer_has_read_only_permissions() {
            PermissionSet perms = PermissionSet.of(BuiltInRole.VIEWER.defaultPermissions());

            assertThat(perms.has(Permission.of("payments", "read"))).isTrue();
            assertThat(perms.has(Permission.of("team", "read"))).isTrue();
            assertThat(perms.has(Permission.of("roles", "read"))).isTrue();
            assertThat(perms.has(Permission.of("payments", "write"))).isFalse();
            assertThat(perms.has(Permission.of("team", "manage"))).isFalse();
        }

        @Test
        void developer_has_api_and_webhook_permissions() {
            PermissionSet perms = PermissionSet.of(BuiltInRole.DEVELOPER.defaultPermissions());

            assertThat(perms.has(Permission.of("api_keys", "read"))).isTrue();
            assertThat(perms.has(Permission.of("api_keys", "write"))).isTrue();
            assertThat(perms.has(Permission.of("webhooks", "read"))).isTrue();
            assertThat(perms.has(Permission.of("webhooks", "write"))).isTrue();
            assertThat(perms.has(Permission.of("payments", "read"))).isTrue();
            assertThat(perms.has(Permission.of("payments", "write"))).isFalse();
            assertThat(perms.has(Permission.of("team", "manage"))).isFalse();
        }
    }

    @Nested
    class Properties {

        @Test
        void size_returns_permission_count() {
            PermissionSet set = PermissionSet.of(List.of(
                    Permission.of("a", "b"),
                    Permission.of("c", "d")));

            assertThat(set.size()).isEqualTo(2);
        }

        @Test
        void empty_set_has_zero_size() {
            assertThat(PermissionSet.empty().size()).isZero();
            assertThat(PermissionSet.empty().isEmpty()).isTrue();
        }

        @Test
        void permissions_returns_immutable_copy() {
            PermissionSet set = PermissionSet.of(List.of(
                    Permission.of("a", "b")));
            Set<Permission> permissions = set.permissions();

            assertThat(permissions).hasSize(1);
        }
    }
}
