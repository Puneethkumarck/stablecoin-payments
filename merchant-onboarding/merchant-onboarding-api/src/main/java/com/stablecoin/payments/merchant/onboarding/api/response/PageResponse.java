package com.stablecoin.payments.merchant.onboarding.api.response;

import java.util.List;

public record PageResponse<T>(
        List<T> data,
        Page page
) {

    public record Page(
            int number,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
