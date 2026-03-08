package com.stablecoin.payments.onramp.api;

import java.util.List;
import java.util.Map;

public record ApiError(
        String code,
        String status,
        String message,
        Map<String, List<String>> errors
) {
    public static ApiError of(String code, String status, String message) {
        return new ApiError(code, status, message, Map.of());
    }

    public static ApiError withErrors(String code, String status, String message,
                                      Map<String, List<String>> errors) {
        return new ApiError(code, status, message, errors);
    }
}
