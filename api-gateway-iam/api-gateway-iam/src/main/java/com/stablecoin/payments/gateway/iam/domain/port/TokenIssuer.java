package com.stablecoin.payments.gateway.iam.domain.port;

import java.util.List;
import java.util.UUID;

public interface TokenIssuer {

    String issueToken(UUID merchantId, UUID clientId, List<String> scopes);

    String jwksJson();
}
