package com.stablecoin.payments.gateway.iam;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("Architecture Rules")
class ArchitectureTest {

    private static com.tngtech.archunit.core.domain.JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.stablecoin.payments.gateway.iam");
    }

    @Test
    @DisplayName("Domain should not depend on Spring")
    void domainShouldNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Domain should not depend on JPA")
    void domainShouldNotDependOnJpa() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Domain should not depend on infrastructure")
    void domainShouldNotDependOnInfrastructure() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Domain should not depend on application layer")
    void domainShouldNotDependOnApplication() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..application..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Ports should be interfaces")
    void portsShouldBeInterfaces() {
        classes()
                .that().resideInAPackage("..domain.port..")
                .and().areNotRecords()
                .should().beInterfaces()
                .check(importedClasses);
    }

    @Test
    @DisplayName("Domain events should be records")
    void domainEventsShouldBeRecords() {
        classes()
                .that().resideInAPackage("..domain.event..")
                .should().beRecords()
                .check(importedClasses);
    }

    @Test
    @DisplayName("Controllers should reside in application.controller package")
    void controllersShouldResideInApplicationController() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideOutsideOfPackage("..application.controller..")
                .allowEmptyShould(true)
                .check(importedClasses);
    }
}
