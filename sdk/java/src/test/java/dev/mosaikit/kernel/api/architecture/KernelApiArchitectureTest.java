// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The kernel API is the contract with plugins. It must stay free of frameworks, so that plugins
 * never depend on the kernel's implementation choices (see ADR-0004).
 */
@AnalyzeClasses(packages = "dev.mosaikit.kernel.api", importOptions = ImportOption.DoNotIncludeTests.class)
class KernelApiArchitectureTest {

    @ArchTest
    static final ArchRule apiDependsOnlyOnTheJdk = classes()
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("dev.mosaikit.kernel.api..", "java..")
            .because("the plugin contract must not expose framework types such as Jackson, Vert.x or Hibernate");
}
