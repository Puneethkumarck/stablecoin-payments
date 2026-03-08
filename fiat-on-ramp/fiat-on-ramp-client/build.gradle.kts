plugins {
    `java-library`
}

dependencies {
    api(project(":fiat-on-ramp:fiat-on-ramp-api"))
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
