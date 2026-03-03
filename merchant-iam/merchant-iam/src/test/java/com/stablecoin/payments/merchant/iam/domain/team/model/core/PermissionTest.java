package com.stablecoin.payments.merchant.iam.domain.team.model.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionTest {

    @Nested
    class Parse {

        @Test
        void parses_valid_permission_string() {
            Permission permission = Permission.parse("payments:write");

            assertThat(permission.namespace()).isEqualTo("payments");
            assertThat(permission.action()).isEqualTo("write");
        }

        @Test
        void parses_wildcard_permission() {
            Permission permission = Permission.parse("*:*");

            assertThat(permission.namespace()).isEqualTo("*");
            assertThat(permission.action()).isEqualTo("*");
        }

        @Test
        void parses_namespace_wildcard() {
            Permission permission = Permission.parse("payments:*");

            assertThat(permission.namespace()).isEqualTo("payments");
            assertThat(permission.action()).isEqualTo("*");
        }

        @Test
        void rejects_invalid_format_without_colon() {
            assertThatThrownBy(() -> Permission.parse("paymentswrite"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid permission format");
        }

        @Test
        void rejects_null_string() {
            assertThatThrownBy(() -> Permission.parse(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Implies {

        @Test
        void full_wildcard_implies_any_permission() {
            Permission wildcard = Permission.of("*", "*");
            Permission specific = Permission.of("payments", "write");

            assertThat(wildcard.implies(specific)).isTrue();
        }

        @Test
        void namespace_wildcard_implies_same_namespace() {
            Permission namespaceWildcard = Permission.of("payments", "*");

            assertThat(namespaceWildcard.implies(Permission.of("payments", "read"))).isTrue();
            assertThat(namespaceWildcard.implies(Permission.of("payments", "write"))).isTrue();
            assertThat(namespaceWildcard.implies(Permission.of("payments", "cancel"))).isTrue();
        }

        @Test
        void namespace_wildcard_does_not_imply_different_namespace() {
            Permission namespaceWildcard = Permission.of("payments", "*");

            assertThat(namespaceWildcard.implies(Permission.of("team", "read"))).isFalse();
        }

        @Test
        void exact_match_implies_same_permission() {
            Permission exact = Permission.of("payments", "read");

            assertThat(exact.implies(Permission.of("payments", "read"))).isTrue();
        }

        @Test
        void exact_match_does_not_imply_different_action() {
            Permission exact = Permission.of("payments", "read");

            assertThat(exact.implies(Permission.of("payments", "write"))).isFalse();
        }

        @Test
        void exact_match_does_not_imply_different_namespace() {
            Permission exact = Permission.of("payments", "read");

            assertThat(exact.implies(Permission.of("transactions", "read"))).isFalse();
        }

        @Test
        void specific_permission_does_not_imply_wildcard() {
            Permission specific = Permission.of("payments", "read");
            Permission wildcard = Permission.of("*", "*");

            assertThat(specific.implies(wildcard)).isFalse();
        }
    }

    @Nested
    class ToString {

        @Test
        void formats_as_namespace_colon_action() {
            assertThat(Permission.of("payments", "write").toString())
                    .isEqualTo("payments:write");
        }
    }

    @Nested
    class Validation {

        @Test
        void rejects_null_namespace() {
            assertThatThrownBy(() -> Permission.of(null, "read"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejects_null_action() {
            assertThatThrownBy(() -> Permission.of("payments", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejects_blank_namespace() {
            assertThatThrownBy(() -> Permission.of(" ", "read"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejects_blank_action() {
            assertThatThrownBy(() -> Permission.of("payments", " "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
