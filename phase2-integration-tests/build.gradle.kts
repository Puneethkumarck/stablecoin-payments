plugins {
    java
}

val temporalVersion: String by project
val wiremockVersion: String by project

dependencies {
    // API modules — DTOs for request/response deserialization
    testImplementation(project(":payment-orchestrator:payment-orchestrator-api"))
    testImplementation(project(":compliance-travel-rule:compliance-travel-rule-api"))
    testImplementation(project(":fx-liquidity-engine:fx-liquidity-engine-api"))

    // Temporal SDK — query workflow results
    testImplementation("io.temporal:temporal-sdk:$temporalVersion")

    // Jackson 3 — JSON parsing (JSR-310 built into core in Jackson 3)
    testImplementation("tools.jackson.core:jackson-databind")

    // Kafka — event verification
    testImplementation("org.apache.kafka:kafka-clients")

    // Test framework
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.awaitility:awaitility")

    // SLF4J for logging in tests
    testImplementation("org.slf4j:slf4j-api")
    testRuntimeOnly("ch.qos.logback:logback-classic")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("load")
    }
}

tasks.register<Test>("loadTest") {
    useJUnitPlatform {
        includeTags("load")
    }
    maxHeapSize = "1g"
}
