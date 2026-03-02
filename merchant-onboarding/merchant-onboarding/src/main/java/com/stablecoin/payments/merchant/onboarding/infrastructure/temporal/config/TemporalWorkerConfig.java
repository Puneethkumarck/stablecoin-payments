package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.config;

import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.activity.KafkaEventActivitiesImpl;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.activity.MerchantOnboardingActivitiesImpl;
import com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.workflow.MerchantOnboardingWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.WorkerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Temporal worker that processes onboarding workflows and activities. Only activated when
 * {@code spring.temporal.namespace} is set — skipped in tests where Temporal auto-configuration is excluded.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.temporal.workers.enabled", havingValue = "true", matchIfMissing = true)
public class TemporalWorkerConfig {

  public static final String TASK_QUEUE = "onboarding-workflow";

  private final MerchantOnboardingActivitiesImpl onboardingActivities;
  private final KafkaEventActivitiesImpl kafkaEventActivities;

  @Bean
  public WorkerFactory workerFactory(WorkflowClient workflowClient) {
    var factory = WorkerFactory.newInstance(workflowClient);
    var worker = factory.newWorker(TASK_QUEUE);

    worker.registerWorkflowImplementationTypes(MerchantOnboardingWorkflowImpl.class);
    worker.registerActivitiesImplementations(onboardingActivities, kafkaEventActivities);

    factory.start();
    return factory;
  }
}
