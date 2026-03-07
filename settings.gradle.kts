rootProject.name = "stablebridge-platform"

buildCache {
    local {
        isEnabled = true
    }
}

include("merchant-onboarding:merchant-onboarding-api")
include("merchant-onboarding:merchant-onboarding-client")
include("merchant-onboarding:merchant-onboarding")

include("merchant-iam:merchant-iam-api")
include("merchant-iam:merchant-iam-client")
include("merchant-iam:merchant-iam")

include("api-gateway-iam:api-gateway-iam-api")
include("api-gateway-iam:api-gateway-iam-client")
include("api-gateway-iam:api-gateway-iam")

include("compliance-travel-rule:compliance-travel-rule-api")
include("compliance-travel-rule:compliance-travel-rule-client")
include("compliance-travel-rule:compliance-travel-rule")

include("fx-liquidity-engine:fx-liquidity-engine-api")
include("fx-liquidity-engine:fx-liquidity-engine-client")
include("fx-liquidity-engine:fx-liquidity-engine")
