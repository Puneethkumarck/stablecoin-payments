package com.stablecoin.payments.compliance.infrastructure.provider.worldcheck;

import java.util.List;

record WorldCheckScreeningResponse(
        String caseId,
        String caseSystemId,
        String status,
        List<MatchResult> results
) {
    record MatchResult(
            String referenceId,
            String matchStrength,
            String matchedTerm,
            String matchedNameType,
            String submittedTerm,
            List<String> matchedLists,
            List<String> categories
    ) {}
}
