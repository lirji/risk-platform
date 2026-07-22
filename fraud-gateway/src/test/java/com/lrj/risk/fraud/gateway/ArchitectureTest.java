package com.lrj.risk.fraud.gateway;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.lrj.risk.fraud.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule decision_domain_is_framework_free = noClasses()
            .that().resideInAPackage("..decision.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "java.sql..", "org.apache.kafka..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule inbound_ports_do_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..application.port.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");
}
