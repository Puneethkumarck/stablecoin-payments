rootProject.name = "stablecoin-payments"

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
