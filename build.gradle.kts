
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
    id("com.diffplug.spotless") apply false
    id("com.google.cloud.tools.jib") apply false
    id("org.sonarqube") version "7.5.0.8588"
    id("org.owasp.dependencycheck") version "12.2.0"
    java
}

sonarqube {
    properties {
        property("sonar.projectKey", "Puneethkumarck_stablebridge-platform")
        property("sonar.organization", "ranganathasoftware")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.exclusions", "**/build/**,**/generated/**,**/*MapperImpl.java")
        property("sonar.coverage.jacoco.xmlReportPaths",
            "**/build/reports/jacoco/test/jacocoTestReport.xml")
    }
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    suppressionFile = "${rootDir}/config/owasp-suppressions.xml"
    nvd {
        System.getenv("NVD_API_KEY")?.let { apiKey = it }
    }
    analyzers {
        nodeEnabled = false
        nodeAuditEnabled = false
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

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
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
