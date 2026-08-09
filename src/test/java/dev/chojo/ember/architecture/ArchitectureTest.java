/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.inject.Singleton;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural conventions enforced across the backend. Every rule here holds for the whole
 * codebase without exception — if a new class cannot satisfy one, fix the placement rather
 * than reintroducing a frozen exception list.
 */
@AnalyzeClasses(packages = "dev.chojo.ember")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule repositoriesAreSingletons = classes()
            .that()
            .haveSimpleNameEndingWith("Repository")
            .and()
            .areTopLevelClasses()
            .should()
            .beAnnotatedWith(Singleton.class);

    @ArchTest
    static final ArchRule repositoriesResideInRepositoryPackages = classes()
            .that()
            .haveSimpleNameEndingWith("Repository")
            .and()
            .areTopLevelClasses()
            .should()
            .resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule servicesResideInServicePackages = classes()
            .that()
            .haveSimpleNameEndingWith("Service")
            .and()
            .areTopLevelClasses()
            .should()
            .resideInAPackage("..service..");

    @ArchTest
    static final ArchRule routesDoNotConstructJsonMappers = noClasses()
            .that()
            .resideInAPackage("..route..")
            .should()
            .callConstructor(ObjectMapper.class)
            .orShould()
            .callMethod(JsonMapper.class, "builder");

    @ArchTest
    static final ArchRule repositoriesDoNotDependOnOtherRepositories = classes()
            .that()
            .haveSimpleNameEndingWith("Repository")
            .and()
            .areTopLevelClasses()
            .should(notDependOnForeignRepositories());

    private static ArchCondition<JavaClass> notDependOnForeignRepositories() {
        return new ArchCondition<>("not depend on other repositories") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                clazz.getDirectDependenciesFromSelf().stream()
                        .map(dependency -> dependency.getTargetClass().getBaseComponentType())
                        .filter(target -> target.getSimpleName().endsWith("Repository"))
                        .filter(target -> !target.getFullName().equals(clazz.getFullName()))
                        .distinct()
                        .forEach(target -> events.add(SimpleConditionEvent.violated(
                                clazz,
                                "%s depends on repository %s".formatted(clazz.getFullName(), target.getFullName()))));
            }
        };
    }
}
