plugins {
    `java-library`
}

dependencies {
    api(project(":merchant-iam:merchant-iam-api"))
    api("org.springframework.cloud:spring-cloud-starter-openfeign")
}
