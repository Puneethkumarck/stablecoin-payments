package com.stablecoin.payments.merchant.onboarding.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("Architecture rules")
class ArchitectureTest {

    private static final String BASE = "com.stablecoin.payments.merchant.onboarding";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    @DisplayName("domain must not depend on infrastructure")
    void domainMustNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE + ".infrastructure..");
        rule.check(classes);
    }

    @Test
    @DisplayName("domain must not depend on application layer")
    void domainMustNotDependOnApplication() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE + ".application..");
        rule.check(classes);
    }

    @Test
    @DisplayName("domain must not import Spring Framework classes except stereotype annotations")
    void domainMustNotImportSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat(
                        resideInAPackage("org.springframework..")
                                .and(resideOutsideOfPackage("org.springframework.stereotype.."))
                                .and(resideOutsideOfPackage("org.springframework.transaction.."))
                );
        rule.check(classes);
    }

    @Test
    @DisplayName("domain must not import Jakarta Persistence classes")
    void domainMustNotImportJpa() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("jakarta.persistence..");
        rule.check(classes);
    }

    @Test
    @DisplayName("infrastructure must not depend on application controller")
    void infrastructureMustNotDependOnApplicationController() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".infrastructure..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE + ".application.controller..");
        rule.check(classes);
    }
}
