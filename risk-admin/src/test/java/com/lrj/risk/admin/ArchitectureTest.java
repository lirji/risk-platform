package com.lrj.risk.admin;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.lrj.risk.admin", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domains_do_not_depend_on_frameworks_or_adapters = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "java.sql..", "..adapter..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule inbound_api_does_not_depend_on_storage_technology = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..", "org.springframework.data.redis..", "java.sql..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_concrete_adapters_or_storage_technology = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter..", "org.springframework.jdbc..", "org.springframework.data.redis..", "java.sql..");
}
