package com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core;

import java.util.List;

public record PagedResult<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {}
