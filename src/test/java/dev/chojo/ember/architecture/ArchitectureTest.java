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
import jakarta.inject.Singleton;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.stream.Stream;

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

    /**
     * Classes that still start threads of their own and are being moved onto the task scheduler.
     * TODO: shrinks with every area moved onto the task scheduler and goes once it is empty.
     */
    private static final Set<String> THREAD_OWNERS_TO_MIGRATE = Set.of(
            "dev.chojo.ember.Bootstrapper",
            "dev.chojo.ember.auth.BreachCheckWorker",
            "dev.chojo.ember.feature.beacon.service.BeaconMetricsService",
            "dev.chojo.ember.feature.beacon.service.BeaconReportService",
            "dev.chojo.ember.feature.discovery.route.PublicDiscoveryRoutes",
            "dev.chojo.ember.feature.discovery.service.DiscoveryMaintenanceScheduler",
            "dev.chojo.ember.feature.discovery.service.DiscoveryPingScheduler",
            "dev.chojo.ember.feature.discovery.service.DiscoveryPingService",
            "dev.chojo.ember.feature.discovery.service.DiscoveryStationRefreshScheduler",
            "dev.chojo.ember.feature.discovery.service.FederationPartnerSeeder",
            "dev.chojo.ember.feature.federation.service.FederationContractRefreshService",
            "dev.chojo.ember.feature.federation.service.FederationFanout",
            "dev.chojo.ember.feature.federation.service.FederationVersionBroadcaster",
            "dev.chojo.ember.feature.federation.service.FederationWebhookService",
            "dev.chojo.ember.feature.feed.service.FeedMetricsService",
            "dev.chojo.ember.feature.knowledgebase.service.KbPresentationService",
            "dev.chojo.ember.feature.knowledgebase.service.KbTrashPurger",
            "dev.chojo.ember.feature.quiz.route.AiRoutes",
            "dev.chojo.ember.feature.station.service.StationImportService",
            "dev.chojo.ember.feature.station.service.TransferTimeoutWatchdog",
            "dev.chojo.ember.feature.storage.route.StorageRoutes",
            "dev.chojo.ember.feature.storage.service.StorageReconciliationService",
            "dev.chojo.ember.util.service.CloudflareRangesService");

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

    @ArchTest
    static final ArchRule threadsAreLeftToTheScheduler =
            classes().that().resideOutsideOfPackage(LIFECYCLE_PACKAGE).should(leaveThreadsToTheScheduler());

    private static ArchCondition<JavaClass> leaveThreadsToTheScheduler() {
        return new ArchCondition<>("leave starting threads, executors and shutdown hooks to the task scheduler") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (isTestClass(clazz) || THREAD_OWNERS_TO_MIGRATE.contains(topLevelName(clazz))) return;
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

    private static String topLevelName(JavaClass clazz) {
        String name = clazz.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
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
