package com.stablecoin.payments.compliance.infrastructure.provider.onfido;

import java.util.List;

record OnfidoCheckListResponse(List<OnfidoCheck> checks) {

    record OnfidoCheck(
            String id,
            String status,
            String result,
            String applicantId,
            List<String> reportIds
    ) {}
}
