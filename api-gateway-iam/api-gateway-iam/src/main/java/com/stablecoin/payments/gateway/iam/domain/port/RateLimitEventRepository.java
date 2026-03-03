package com.stablecoin.payments.gateway.iam.domain.port;

import com.stablecoin.payments.gateway.iam.domain.model.RateLimitEvent;

public interface RateLimitEventRepository {

    RateLimitEvent save(RateLimitEvent event);
}
