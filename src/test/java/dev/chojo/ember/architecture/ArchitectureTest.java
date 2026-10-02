/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.feature.board.entity.BoardFieldType;
import dev.chojo.ember.feature.comment.repository.CommentRepository;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Singleton;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.ENUMS;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

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

    /**
     * Comments of every kind live in one table behind one repository, and only the comment feature
     * reaches it. Every other feature goes through the comment service, so the check that an answer
     * stays on its parent's target, the soft delete, the notices and the mentions hold for every
     * comment, whatever it was written on. Tests wire the repository into the service and are left out.
     */
    @ArchTest
    static final ArchRule onlyTheCommentFeatureReachesCommentStorage = noClasses()
            .that()
            .resideOutsideOfPackage("dev.chojo.ember.feature.comment..")
            .and(DescribedPredicate.not(tests()))
            .should()
            .dependOnClassesThat()
            .areAssignableTo(CommentRepository.class);

    /**
     * A domain event handler belongs to the feature whose behaviour it carries, in that feature's
     * {@code handler} package beside the services it calls. The bus and the events stay in the core
     * {@code event} package, which holds no handler of its own. Handlers written inside tests are
     * left out.
     */
    @ArchTest
    static final ArchRule eventHandlersLiveInTheirFeatures = classes()
            .that()
            .implement(DomainEventHandler.class)
            .and(DescribedPredicate.not(tests()))
            .should()
            .resideInAPackage("dev.chojo.ember.feature.*.handler");

    /**
     * {@link FieldType} is the one list of field types. A feature that spelled its own list would map
     * each of its constants onto it, so no enum outside the field kernel declares a method answering
     * a {@code FieldType}. The two that do are the names shared appointments and shared boards still
     * send to partner stations, which nothing but the wire reads.
     */
    @ArchTest
    static final ArchRule noFeatureSpellsItsOwnFieldTypes = noMethods()
            .that()
            .areDeclaredInClassesThat(ENUMS.and(resideOutsideOfPackage("dev.chojo.ember.feature.question.."))
                    .and(DescribedPredicate.not(belongToAnyOf(EventFieldType.class, BoardFieldType.class))))
            .should()
            .haveRawReturnType(FieldType.class);

    @ArchTest
    static final ArchRule routesDoNotConstructJsonMappers = noClasses()
            .that()
            .resideInAPackage("..route..")
            .should()
            .callConstructor(ObjectMapper.class)
            .orShould()
            .callMethod(JsonMapper.class, "builder");

    /**
     * A route reads the request, asks a service and writes the answer. Lookups, access decisions and
     * writes live in services, where they are covered, so a route reaches no repository, not even
     * for one of its nested read models. The tests beside the routes build their data through
     * repositories and are not routes.
     */
    @ArchTest
    static final ArchRule routesDoNotDependOnRepositories =
            noClasses().that(areRoutes()).should().dependOnClassesThat().resideInAPackage("..repository..");

    /**
     * A shared core is told whose data it touches by an {@link Owner}, and that owner comes from the
     * session ({@code StationSession.owner()}, {@code UserSession.association(...)},
     * {@code UserSession.instance()}). A route that built one itself would build it from what the
     * request says, and a caller could then name another station's or association's data.
     */
    @ArchTest
    static final ArchRule routesTakeTheirOwnerFromTheSession =
            noClasses().that(areRoutes()).should().callConstructorWhere(target(owner(assignableTo(Owner.class))));

    /**
     * An association's storage versions and placements are kept by the storage feature and changed by
     * the association's own storage service. Nobody else reaches them, so every write to them passes
     * the checks those two hold.
     */
    @ArchTest
    static final ArchRule onlyStorageAndTheAssociationReachAssociationStorage = noClasses()
            .that()
            .resideOutsideOfPackages("dev.chojo.ember.feature.storage..", "dev.chojo.ember.feature.cluster..")
            .and(DescribedPredicate.not(tests()))
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(ClusterStorageConfigRepository.class, ClusterStationStorageRepository.class);

    @ArchTest
    static final ArchRule routesDoNotSaveConfiguration =
            noClasses().that(areRoutes()).should().callMethod(Conf.class, "save");

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

    private static DescribedPredicate<JavaClass> areRoutes() {
        return resideInAPackage("..route..")
                .or(assignableTo(Routes.class))
                .and(DescribedPredicate.not(tests()))
                .as("routes");
    }

    private static DescribedPredicate<JavaClass> tests() {
        return DescribedPredicate.describe("tests", ArchitectureTest::isTestClass);
    }

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
