plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
    java
    `java-test-fixtures`
    jacoco
}

jib {
    from {
        image = "eclipse-temurin:25-jre-alpine"
    }
    to {
        image = "stablebridge/payment-orchestrator"
        tags = setOf("latest")
    }
    container {
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}

val integrationTestSourceSet: SourceSet = sourceSets.create("integrationTest") {
    java.srcDir("src/integration-test/java")
    resources.srcDir("src/integration-test/resources")
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

configurations {
    named("integrationTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("integrationTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
}

tasks.register<Test>("integrationTest") {
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.test)
    configure<JacocoTaskExtension> { isEnabled = false }
    failOnNoDiscoveredTests = false
    exclude("**/Abstract*", "**/config/**")
}

val businessTestSourceSet: SourceSet = sourceSets.create("businessTest") {
    java.srcDir("src/business-test/java")
    resources.srcDir("src/business-test/resources")
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output + integrationTestSourceSet.output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output + integrationTestSourceSet.output
}

configurations {
    named("businessTestImplementation") { extendsFrom(configurations.named("integrationTestImplementation").get()) }
    named("businessTestRuntimeOnly") { extendsFrom(configurations.named("integrationTestRuntimeOnly").get()) }
}

tasks.register<Test>("businessTest") {
    testClassesDirs = businessTestSourceSet.output.classesDirs
    classpath = businessTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.named("integrationTest"))
    failOnNoDiscoveredTests = false
    configure<JacocoTaskExtension> { isEnabled = false }
}

val lombokVersion: String by project
val mapstructVersion: String by project
val lombokMapstructBindingVersion: String by project
val resilience4jVersion: String by project
val temporalVersion: String by project
val flywayVersion: String by project
val archunitVersion: String by project
val testcontainersVersion: String by project
val wiremockVersion: String by project
val springdocVersion: String by project

dependencies {
    implementation(project(":payment-orchestrator:payment-orchestrator-api"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // OpenAPI / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Redis — payment state cache
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Kafka via Spring Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Feign
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    // Resilience4j
    implementation("io.github.resilience4j:resilience4j-spring-boot3:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")

    // Temporal SDK
    implementation("io.temporal:temporal-sdk:$temporalVersion")
    implementation("io.temporal:temporal-spring-boot-starter:$temporalVersion")

    // MapStruct (compiler args set below in JavaCompile task)
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:$lombokMapstructBindingVersion")

    // Outbox (namastack)
    implementation("io.namastack:namastack-outbox-starter-jdbc:1.0.0")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    // Test Fixtures
    testFixturesImplementation("org.assertj:assertj-core")
    testFixturesImplementation("org.mockito:mockito-core")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    "integrationTestImplementation"(testFixtures(project))
    "integrationTestImplementation"("org.testcontainers:postgresql:$testcontainersVersion")
    "integrationTestImplementation"("org.testcontainers:kafka:$testcontainersVersion")
    "integrationTestImplementation"("org.testcontainers:junit-jupiter:$testcontainersVersion")
    "integrationTestImplementation"("org.wiremock:wiremock-standalone:$wiremockVersion")
    "integrationTestImplementation"("org.springframework.boot:spring-boot-starter-webmvc-test")
    "integrationTestImplementation"("org.springframework.boot:spring-boot-starter-security-test")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf(
        "-Amapstruct.defaultComponentModel=spring",
        "-Amapstruct.unmappedTargetPolicy=IGNORE"
    ))
}

tasks.withType<Test> {
    jvmArgs("-Dnet.bytebuddy.experimental=true")
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.test {
    configure<JacocoTaskExtension> {
        excludes = listOf("sun.*", "jdk.*", "com.sun.*", "java.*", "javax.*")
    }
    finalizedBy(tasks.jacocoTestReport)
}

val jacocoExclusions = listOf(
    "**/entity/**",
    "**/mapper/**",
    "**/config/**",
    "**/*Application*",
    "**/generated/**",
    "**/*MapperImpl*"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) { exclude(jacocoExclusions) }
    }))
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = "0.50".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) { exclude(jacocoExclusions) }
    }))
}

tasks.named("check") {
    dependsOn(
        tasks.named("integrationTest"),
        tasks.named("businessTest"),
        tasks.jacocoTestCoverageVerification
    )
}
