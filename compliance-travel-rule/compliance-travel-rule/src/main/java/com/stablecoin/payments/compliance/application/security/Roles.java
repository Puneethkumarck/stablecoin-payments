package com.stablecoin.payments.compliance.application.security;

import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class Roles {

    public static final String ROLE_ADMIN              = "ROLE_ADMIN";
    public static final String ROLE_COMPLIANCE_OFFICER = "ROLE_COMPLIANCE_OFFICER";
    public static final String ROLE_INTERNAL           = "ROLE_INTERNAL";
}
