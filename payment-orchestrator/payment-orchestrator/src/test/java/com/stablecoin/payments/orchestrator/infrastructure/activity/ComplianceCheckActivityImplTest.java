package com.stablecoin.payments.orchestrator.infrastructure.activity;

import com.stablecoin.payments.compliance.client.ComplianceCheckClient;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceCheckActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestActivityEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult.ComplianceStatus.FAILED;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceResult.ComplianceStatus.PASSED;
import static com.stablecoin.payments.orchestrator.fixtures.ComplianceActivityFixtures.aComplianceRequest;
import static com.stablecoin.payments.orchestrator.fixtures.ComplianceActivityFixtures.aComplianceResponse;
import static com.stablecoin.payments.orchestrator.fixtures.ComplianceActivityFixtures.aSanctionsHitResponse;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.CHECK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

@DisplayName("ComplianceCheckActivityImpl")
class ComplianceCheckActivityImplTest {

    private final ComplianceCheckClient complianceCheckClient = mock(ComplianceCheckClient.class);
    private TestActivityEnvironment testActivityEnvironment;
    private ComplianceCheckActivity activity;

    @BeforeEach
    void setUp() {
        testActivityEnvironment = TestActivityEnvironment.newInstance();
        testActivityEnvironment.registerActivitiesImplementations(
                new ComplianceCheckActivityImpl(complianceCheckClient));
        activity = testActivityEnvironment.newActivityStub(ComplianceCheckActivity.class);
    }

    @AfterEach
    void tearDown() {
        testActivityEnvironment.close();
    }

    @Nested
    @DisplayName("compliance check passes")
    class ComplianceCheckPasses {

        @Test
        @DisplayName("should return PASSED when S2 returns PASSED immediately")
        void shouldReturnPassedWhenS2ReturnsPassed() {
            given(complianceCheckClient.initiateCheck(any()))
                    .willReturn(aComplianceResponse("PASSED", "PASSED"));

            var result = activity.checkCompliance(aComplianceRequest());

            var expected = new ComplianceResult(CHECK_ID, PASSED, null);
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("compliance check fails")
    class ComplianceCheckFails {

        @Test
        @DisplayName("should return FAILED when S2 returns FAILED")
        void shouldReturnFailedWhenS2ReturnsFailed() {
            given(complianceCheckClient.initiateCheck(any()))
                    .willReturn(aComplianceResponse("FAILED", "FAILED"));

            var result = activity.checkCompliance(aComplianceRequest());

            var expected = new ComplianceResult(CHECK_ID, FAILED, "FAILED");
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should return FAILED when S2 returns MANUAL_REVIEW")
        void shouldReturnFailedWhenManualReview() {
            given(complianceCheckClient.initiateCheck(any()))
                    .willReturn(aComplianceResponse("MANUAL_REVIEW", "MANUAL_REVIEW"));

            var result = activity.checkCompliance(aComplianceRequest());

            var expected = new ComplianceResult(CHECK_ID, FAILED, "MANUAL_REVIEW");
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("sanctions hit (non-retryable)")
    class SanctionsHit {

        @Test
        @DisplayName("should throw non-retryable ApplicationFailure on SANCTIONS_HIT")
        void shouldThrowNonRetryableOnSanctionsHit() {
            given(complianceCheckClient.initiateCheck(any()))
                    .willReturn(aSanctionsHitResponse());

            assertThatThrownBy(() -> activity.checkCompliance(aComplianceRequest()))
                    .isInstanceOf(ActivityFailure.class)
                    .hasCauseInstanceOf(ApplicationFailure.class)
                    .satisfies(e -> {
                        var af = (ApplicationFailure) e.getCause();
                        assertThat(af.getType()).isEqualTo("SANCTIONS_HIT");
                        assertThat(af.isNonRetryable()).isTrue();
                    });
        }
    }

    @Nested
    @DisplayName("polling")
    class Polling {

        @Test
        @DisplayName("should poll until terminal state")
        void shouldPollUntilTerminalState() {
            var pendingResponse = aComplianceResponse("KYC_IN_PROGRESS", null);
            var passedResponse = aComplianceResponse("PASSED", "PASSED");

            given(complianceCheckClient.initiateCheck(any())).willReturn(pendingResponse);
            given(complianceCheckClient.getCheck(CHECK_ID))
                    .willReturn(aComplianceResponse("SANCTIONS_SCREENING", null))
                    .willReturn(passedResponse);

            var result = activity.checkCompliance(aComplianceRequest());

            var expected = new ComplianceResult(CHECK_ID, PASSED, null);
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(complianceCheckClient).should(times(2)).getCheck(CHECK_ID);
        }
    }
}
