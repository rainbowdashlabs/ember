/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
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

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural conventions enforced across the backend. Exception lists freeze the
 * pre-existing violations that later cleanup phases resolve — do not extend them
 * for new code; fix the placement instead.
 */
@AnalyzeClasses(packages = "dev.chojo.ember")
public class ArchitectureTest {

    private static final Set<String> REPOSITORY_PLACEMENT_EXCEPTIONS =
            Set.of("dev.chojo.ember.feature.restriction.RestrictionRepository");

    private static final Set<String> SERVICE_PLACEMENT_EXCEPTIONS = Set.of(
            "dev.chojo.ember.util.CloudflareRangesService",
            "dev.chojo.ember.feature.storage.audit.StorageBackendAuditService",
            "dev.chojo.ember.feature.storage.transfer.TransferBackendDescriptorService",
            "dev.chojo.ember.feature.storage.migration.InstanceStorageMigrationService",
            "dev.chojo.ember.feature.storage.migration.StorageMigrationService");

    private static final Set<String> REPOSITORY_DEPENDENCY_EXCEPTIONS = Set.of(
            "dev.chojo.ember.feature.restriction.RestrictionRepository",
            "dev.chojo.ember.feature.board.repository.BoardTicketRepository",
            "dev.chojo.ember.feature.members.repository.StationMemberRepository");

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
            .and(notIn(REPOSITORY_PLACEMENT_EXCEPTIONS))
            .should()
            .resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule servicesResideInServicePackages = classes()
            .that()
            .haveSimpleNameEndingWith("Service")
            .and()
            .areTopLevelClasses()
            .and(notIn(SERVICE_PLACEMENT_EXCEPTIONS))
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
            .and(notIn(REPOSITORY_DEPENDENCY_EXCEPTIONS))
            .should(notDependOnForeignRepositories());

    private static DescribedPredicate<JavaClass> notIn(Set<String> names) {
        return DescribedPredicate.describe("are not frozen exceptions", clazz -> !names.contains(clazz.getFullName()));
    }

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
