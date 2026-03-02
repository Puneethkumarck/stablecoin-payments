
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("org.springframework.boot") version "3.4.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.diffplug.spotless") version "7.0.2" apply false
    id("org.sonarqube") version "7.2.2.6593"
    java
}

sonarqube {
    properties {
        property("sonar.projectKey", "Puneethkumarck_stablecoin-payments")
        property("sonar.organization", "ranganathasoftware")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.java.binaries", "**/build/classes")
        property("sonar.exclusions", "**/build/**,**/generated/**,**/*MapperImpl.java")
        property("sonar.coverage.jacoco.xmlReportPaths",
            "**/build/reports/jacoco/test/jacocoTestReport.xml")
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "com.diffplug.spotless")

    val javaVersion: String by project
    val springBootVersion: String by project
    val springCloudVersion: String by project
    val lombokVersion: String by project

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion.toInt())
        }
    }

    repositories {
        mavenCentral()
    }

    configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        }
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.projectlombok" && requested.name == "lombok") {
                useVersion(lombokVersion)
            }
        }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok:$lombokVersion")
        "annotationProcessor"("org.projectlombok:lombok:$lombokVersion")
        "testCompileOnly"("org.projectlombok:lombok:$lombokVersion")
        "testAnnotationProcessor"("org.projectlombok:lombok:$lombokVersion")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            removeUnusedImports()
            importOrder("", "java|javax", "\\#")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
