/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import jakarta.inject.Singleton;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural conventions enforced across the backend. Every rule here holds for the whole
 * codebase without exception - if a new class cannot satisfy one, fix the placement rather
 * than reintroducing a frozen exception list.
 */
@AnalyzeClasses(packages = "dev.chojo.ember")
public class ArchitectureTest {
    private static final String LIFECYCLE_PACKAGE = "dev.chojo.ember.lifecycle..";
    private static final String CONSTRUCTOR = "<init>";

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

    /**
     * Notifications are written by the notifier and marked by the digest, both in the notifications
     * feature; everybody else asks the notifier. That keeps one place applying the audience, the
     * preferences and "once while unread" to every row.
     */
    @ArchTest
    static final ArchRule onlyTheNotificationsFeatureWritesNotifications = noClasses()
            .that()
            .resideOutsideOfPackage("dev.chojo.ember.feature.notifications..")
            .should()
            .callMethodWhere(target(owner(assignableTo(NotificationRepository.class)))
                    .and(target(name("insertForStation")
                            .or(name("insertForCluster"))
                            .or(name("markEmailed"))
                            .or(name("deleteByTypeAndLink"))
                            .or(name("deleteAllPointingAt"))
                            .or(name("deleteOldAcknowledged")))));

    @ArchTest
    static final ArchRule routesDoNotConstructJsonMappers = noClasses()
            .that()
            .resideInAPackage("..route..")
            .should()
            .callConstructor(ObjectMapper.class)
            .orShould()
            .callMethod(JsonMapper.class, "builder");

    @ArchTest
    static final ArchRule routesDoNotSaveConfiguration =
            noClasses().that().resideInAPackage("..route..").should().callMethod(Conf.class, "save");

    @ArchTest
    static final ArchRule repositoriesDoNotDependOnOtherRepositories = classes()
            .that()
            .haveSimpleNameEndingWith("Repository")
            .and()
            .areTopLevelClasses()
            .should(notDependOnForeignRepositories());

    @ArchTest
    static final ArchRule threadsAreLeftToTheScheduler =
            classes().that().resideOutsideOfPackage(LIFECYCLE_PACKAGE).should(leaveThreadsToTheScheduler());

    private static ArchCondition<JavaClass> leaveThreadsToTheScheduler() {
        return new ArchCondition<>("leave starting threads, executors and shutdown hooks to the task scheduler") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (isTestClass(clazz)) return;
                Stream.<JavaCall<?>>concat(
                                clazz.getMethodCallsFromSelf().stream(), clazz.getConstructorCallsFromSelf().stream())
                        .filter(ArchitectureTest::startsThreadsOfItsOwn)
                        .forEach(call -> events.add(SimpleConditionEvent.violated(clazz, call.getDescription())));
            }
        };
    }

    private static boolean startsThreadsOfItsOwn(JavaCall<?> call) {
        String name = call.getName();
        return switch (call.getTargetOwner().getName()) {
            case "java.util.concurrent.Executors" -> name.startsWith("new");
            case "java.lang.Thread" ->
                CONSTRUCTOR.equals(name)
                        || "ofPlatform".equals(name)
                        || "ofVirtual".equals(name)
                        || "startVirtualThread".equals(name);
            case "java.util.concurrent.ThreadPoolExecutor",
                    "java.util.concurrent.ScheduledThreadPoolExecutor",
                    "java.util.concurrent.ForkJoinPool",
                    "java.util.Timer" -> CONSTRUCTOR.equals(name);
            case "java.util.concurrent.CompletableFuture" ->
                ("runAsync".equals(name) || "supplyAsync".equals(name))
                        && call.getTarget().getRawParameterTypes().size() == 1;
            case "java.lang.Runtime" -> "addShutdownHook".equals(name);
            default -> false;
        };
    }

    private static boolean isTestClass(JavaClass clazz) {
        return clazz.getSource()
                .map(source -> source.getUri().toString().contains("/test/"))
                .orElse(false);
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
