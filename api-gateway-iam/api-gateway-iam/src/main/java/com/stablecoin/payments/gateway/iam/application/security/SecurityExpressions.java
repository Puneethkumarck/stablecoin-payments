package com.stablecoin.payments.gateway.iam.application.security;

public final class SecurityExpressions {

    public static final String HAS_MERCHANT_ACCESS =
            "@merchantScopeEnforcer.hasAccess(#merchantId)";

    public static final String HAS_MERCHANT_ACCESS_VIA_REQUEST =
            "@merchantScopeEnforcer.hasAccess(#request.merchantId())";

    public static final String HAS_MERCHANT_ACCESS_VIA_RESPONSE =
            "@merchantScopeEnforcer.hasAccess(returnObject.merchantId())";

    public static final String HAS_MERCHANT_ACCESS_VIA_API_KEY =
            "@merchantScopeEnforcer.hasAccessToApiKey(#keyId)";

    public static final String HAS_TOKEN_OWNERSHIP =
            "@merchantScopeEnforcer.hasAccessToToken(#request.jti())";

    private SecurityExpressions() {
    }
}
