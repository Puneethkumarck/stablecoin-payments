package com.stablecoin.payments.orchestrator.application.config;

import com.stablecoin.payments.orchestrator.domain.workflow.PaymentWorkflowImpl;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceCheckActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.EventPublishingActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockActivity;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.stablecoin.payments.orchestrator.application.config.TemporalConfig.TASK_QUEUE;

/**
 * Registers Temporal workers with workflow types and activity implementations.
 * <p>
 * Disabled during integration/unit tests via {@code app.temporal.worker.enabled=false}.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.temporal.worker.enabled", havingValue = "true", matchIfMissing = true)
public class TemporalWorkerConfig {

    @Bean
    public Worker paymentWorker(WorkerFactory workerFactory,
                                ComplianceCheckActivity complianceCheckActivity,
                                FxLockActivity fxLockActivity,
                                EventPublishingActivity eventPublishingActivity) {
        var worker = workerFactory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                complianceCheckActivity, fxLockActivity, eventPublishingActivity);
        log.info("Temporal worker registered on queue={} with PaymentWorkflow, "
                + "ComplianceCheckActivity, FxLockActivity, EventPublishingActivity", TASK_QUEUE);
        workerFactory.start();
        return worker;
    }
}
