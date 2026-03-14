package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.adapter;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.OnboardingWorkflowPort;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.config.TemporalWorkerConfig;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.workflow.MerchantOnboardingWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Temporal-backed adapter that starts the merchant onboarding workflow asynchronously.
 * <p>
 * Workflow ID convention: {@code onboarding-<merchantId>}, task queue: {@code onboarding-workflow}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.fallback-adapters.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class TemporalOnboardingWorkflowAdapter implements OnboardingWorkflowPort {

    private static final Duration WORKFLOW_EXECUTION_TIMEOUT = Duration.ofDays(30);

    private final WorkflowClient workflowClient;

    @Override
    public void startOnboarding(UUID merchantId) {
        var workflowId = "onboarding-" + merchantId;
        var options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.TASK_QUEUE)
                .setWorkflowExecutionTimeout(WORKFLOW_EXECUTION_TIMEOUT)
                .build();

        var workflow = workflowClient.newWorkflowStub(MerchantOnboardingWorkflow.class, options);
        WorkflowClient.start(workflow::runOnboarding, merchantId);
        log.info("Onboarding workflow started workflowId={} merchantId={}", workflowId, merchantId);
    }
}
