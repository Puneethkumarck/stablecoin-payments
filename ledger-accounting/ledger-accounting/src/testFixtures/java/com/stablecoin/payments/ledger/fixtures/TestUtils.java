package com.stablecoin.payments.ledger.fixtures;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;

public final class TestUtils {

    private TestUtils() {}

    public static <T> T eqIgnoringTimestamps(T expected) {
        return eqIgnoring(expected);
    }

    public static <T> T eqIgnoring(T expected, String... fieldsToIgnore) {
        return argThat(it -> isEqualIgnoring(it, expected, fieldsToIgnore));
    }

    private static <T> boolean isEqualIgnoring(T original, T expected, String... fieldsToIgnore) {
        try {
            assertThat(original)
                    .usingRecursiveComparison()
                    .ignoringFieldsOfTypes(ZonedDateTime.class, LocalDateTime.class, LocalDate.class, Instant.class)
                    .ignoringFields(fieldsToIgnore)
                    .isEqualTo(expected);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
