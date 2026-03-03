plugins {
    `java-library`
}

dependencies {
    api(project(":api-gateway-iam:api-gateway-iam-api"))
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
