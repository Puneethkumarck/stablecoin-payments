package com.stablecoin.payments.offramp;

import org.junit.jupiter.api.Tag;

/**
 * Base class for business (E2E) tests.
 * <p>
 * Extends {@link AbstractIntegrationTest} to inherit TestContainers setup and
 * security configuration. Business tests exercise full lifecycle flows through
 * the real Spring context.
 */
@Tag("business")
public abstract class AbstractBusinessTest extends AbstractIntegrationTest {
}
