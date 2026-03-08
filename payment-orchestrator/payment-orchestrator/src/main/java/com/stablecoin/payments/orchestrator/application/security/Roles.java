package com.stablecoin.payments.orchestrator.application.security;

import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class Roles {

    public static final String ROLE_ADMIN    = "ROLE_ADMIN";
    public static final String ROLE_MERCHANT = "ROLE_MERCHANT";
    public static final String ROLE_INTERNAL = "ROLE_INTERNAL";
}
