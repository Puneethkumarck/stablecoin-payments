package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.team.model.Invitation;
import com.stablecoin.payments.merchant.iam.domain.team.model.MerchantUser;

public record InviteResult(MerchantUser user, Invitation invitation) {}
