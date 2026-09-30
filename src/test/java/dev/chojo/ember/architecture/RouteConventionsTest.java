/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.RegisteredRoutes;
import dev.chojo.ember.api.RegisteredRoutes.Route;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.feature.federation.contract.FederationContractCatalog;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conventions every route is held to, checked against the router the application builds and against
 * the compiled route classes rather than against their source text.
 *
 * <p>The router says what is registered, in which order, with which roles, and which method answers
 * each route; see {@link RegisteredRoutes} for how that last part is found. The compiled classes say
 * what a handler calls. Neither depends on how a registration happens to be formatted or which local
 * variable a prefix was kept in, which is what the text-based checks before these could not help
 * depending on.
 */
class RouteConventionsTest {

    /** The one blocked read: exporting the data is the act, and doing it with a GET does not change that. */
    private static final String GDPR_EXPORT = RouteHarness.PREFIX + "/session/gdpr-export";

    /**
     * The members a handler touches that say which station a row belongs to: the session's station,
     * one of the ownership helpers, a station resolved from the address, the partner whose station
     * scopes a {@code /remote} lookup, the account and member the row is scoped to, the cluster a
     * cluster route answers for, or the transfer token that names the station it was issued for.
     */
    private static final Pattern STATION_EVIDENCE = Pattern.compile(
            "stationId|requireOwned|requireVisibleEvent|requireSameStation|requireShared|requirePartner"
                    + "|ForPartner|isShared|resolve\\w*Station|openBlog|accountId|requireManaged|clusterId|validateToken");

    /** A path parameter that names a station, which makes the station itself the row addressed. */
    private static final Pattern STATION_PARAMETER = Pattern.compile("\\{station(Id|Uid)?}", Pattern.CASE_INSENSITIVE);

    /** Reading who is asking, which scopes a row to the caller rather than to a station. */
    private static final Set<String> CALLER_READS = Set.of("account", "member", "clusterMember");

    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{[^}]*}|<[^>]*>");

    private static final Pattern FEDERATION_KEY_MEMBER = Pattern.compile("(?i)federationPrivateKey|privateKeyBase64");

    private static RegisteredRoutes application;
    private static JavaClasses routeClasses;

    @BeforeAll
    static void buildTheRouter() {
        application = RegisteredRoutes.application();
        routeClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(application.boundOrder().stream()
                        .map(Class::getPackageName)
                        .distinct()
                        .toList());
    }

    /** The router holds every route the route classes registered, and nothing else. */
    @Test
    void theRouterHoldsEveryRegisteredRoute() {
        assertTrue(
                application.routes().size() > 1000,
                () -> "Expected the routes of every bound route class, found "
                        + application.routes().size());
        assertEquals(application.routes().size(), application.routerEndpoints().size(), "routes the router holds");
    }

    /**
     * A handler nothing points at answers nothing.
     *
     * <p>Java says nothing about it: the method compiles, the class compiles, the tests that never
     * call the endpoint pass, and the feature is simply absent at runtime. It is an easy mistake to
     * make while moving registrations around, and an expensive one to find, because the first
     * report is a 404 from something that plainly exists in the source. A route class that is never
     * bound is the same mistake made for every handler it has at once.
     */
    @Test
    void everyHandlerIsWiredToARoute() {
        List<String> orphans = new ArrayList<>();
        for (Class<? extends Routes> owner : application.boundOrder()) {
            Set<String> answering = application.routes().stream()
                    .filter(route -> route.owner() == owner)
                    .map(Route::handler)
                    .collect(Collectors.toSet());
            Arrays.stream(owner.getDeclaredMethods())
                    .filter(RouteConventionsTest::isHandlerShaped)
                    .map(Method::getName)
                    .filter(handler -> !answering.contains(handler))
                    .sorted()
                    .forEach(handler -> orphans.add("%s: %s".formatted(owner.getSimpleName(), handler)));
        }
        routeClasses.stream()
                .filter(type -> type.isAssignableTo(Routes.class) && !type.isInterface())
                .filter(type -> application.boundOrder().stream()
                        .noneMatch(bound -> bound.getName().equals(type.getName())))
                .forEach(type -> orphans.add(type.getSimpleName() + " is never bound"));
        assertTrue(orphans.isEmpty(), () -> "handler(s) declared but never registered, so the endpoint answers 404:%n%s"
                .formatted(String.join(System.lineSeparator(), orphans)));
    }

    /**
     * Every handler that takes an id from the address has to say which station it belongs to.
     *
     * <p>The gating model refuses a caller without the permission a route declares, and stops
     * there. It never asks whether the row the caller named is one of theirs, so a handler that
     * simply forgets is caught by nothing: it compiles, its tests pass, and it works perfectly for
     * the station that owns the row. Nine features got that wrong independently, which is one
     * missing control repeated nine times rather than nine mistakes.
     *
     * <p>Saying it can be done in any of the ways the codebase already uses: reading
     * {@code session.stationId()}, calling a {@code RouteSupport.requireOwned*} helper or
     * {@code EventVisibility.requireVisibleEvent}, which checks the station before the audience, going
     * through a {@code *Guards} class, or resolving the federation partner whose station scopes a
     * {@code /remote} lookup. A handler that does none of them, directly or through a method of its
     * own class it calls, is either a hole or an endpoint that genuinely belongs to no station, and
     * the second kind says so with {@link StationFree}. An instance-admin route answers for the
     * instance rather than for a station, which is the whole of its scope.
     *
     * <p>A {@code /remote} route the federation contract binder serves has no body in its route class:
     * the binder resolves the partner from the signature and hands the feature's serving function the
     * serving station's partner row, so the station is fixed before any id is read. Whether the
     * serving function then checks the share is what each feature's parity and share tests prove.
     */
    @Test
    void everyHandlerTakingAnIdChecksTheStation() {
        List<String> unchecked = new ArrayList<>();
        for (Route route : application.routes()) {
            if (!route.takesAnId()) continue;
            if (route.served()) continue;
            if (route.roles().stream().anyMatch(InstancePermission.class::isInstance)) continue;
            if (addressesAStationItself(route)) continue;
            if (isStationFree(route)) continue;
            if (checksStation(route)) continue;
            unchecked.add(route.toString());
        }
        assertTrue(
                unchecked.isEmpty(),
                () -> ("handler(s) taking an id from the address without a station check; add one, or mark the"
                                + " endpoint @StationFree with the reason it belongs to no station:%n%s")
                        .formatted(String.join(System.lineSeparator(), unchecked)));
    }

    /**
     * Javalin answers with the first registered route that matches, so a route registered after
     * another that matches everything it does is dead: the earlier one wins and a literal segment is
     * parsed as its parameter value.
     *
     * <p>Every route class shares one prefix and the router takes them in binding order, so this
     * holds across classes as much as within one. The failure showed up within a class first:
     * splitting the board routes uncovered {@code checklist/reorder} registered after
     * {@code checklist/{itemId}} and {@code tickets/reorder} after {@code tickets/{ticketNumber}}.
     * Both were unreachable and answered 400 on the integer parse. The router itself is asked which
     * route answers, so the rule is exactly as strict as the matching it guards.
     */
    @Test
    void noRouteIsShadowedByAnEarlierOne() {
        List<String> violations = new ArrayList<>();
        for (Route route : application.routes()) {
            String request = PATH_PARAMETER.matcher(route.path()).replaceAll("probe");
            var answering = application.answering(route.method(), request).orElseThrow();
            if (answering == route.endpoint()) continue;
            Route first = application.routes().stream()
                    .filter(candidate -> candidate.endpoint() == answering)
                    .findFirst()
                    .orElseThrow();
            violations.add("%s is unreachable - %s is registered earlier and matches it first".formatted(route, first));
        }
        assertTrue(violations.isEmpty(), () -> "Unreachable routes, each answered by a route registered before it:%n%s"
                .formatted(String.join(System.lineSeparator(), violations)));
    }

    /**
     * The demo guard refuses a whole address, without looking at the method. Every address it
     * refuses therefore has to be one where reading is not a thing anybody does, or the demo hides
     * what it exists to show: the library came back empty, the avatar and the station logo did not
     * load, and none of it looked like a guard, it looked like a broken page.
     *
     * <p>An address that answers a GET belongs in the write-only list beside it instead. The one
     * exception is the data export, which is a GET that does the thing being refused, so it is
     * named here rather than found.
     */
    @Test
    void noDemoBlockedPathAnswersAGet() throws Exception {
        Set<String> blocked = demoBlockedPaths();
        List<String> reads = application.routes().stream()
                .filter(route -> route.method().name().equals("GET"))
                .filter(route -> blocked.contains(route.path()) && !route.path().equals(GDPR_EXPORT))
                .map(Route::toString)
                .toList();
        assertTrue(
                reads.isEmpty(),
                () -> "blocked outright in demo mode although the address also answers a read; move it to"
                        + " DEMO_BLOCKED_WRITE_PATHS:%n%s".formatted(String.join(System.lineSeparator(), reads)));
    }

    /**
     * Every {@code /remote} route is one the federation contract declares. A route registered there
     * any other way is served without the version check the contract binder puts in front of its
     * handlers, and contributes to no surface hash.
     */
    @Test
    void noRemoteRouteBypassesTheContract() {
        Set<String> declared = FederationContractCatalog.ENDPOINTS.stream()
                .map(endpoint -> endpoint.method() + " " + RouteHarness.PREFIX + endpoint.path())
                .collect(Collectors.toSet());
        List<String> bypassing = application.routes().stream()
                .filter(route -> route.path().startsWith(RouteHarness.PREFIX + "/remote/"))
                .filter(route -> !declared.contains(route.method() + " " + route.path()))
                .map(Route::toString)
                .toList();
        assertTrue(
                bypassing.isEmpty(),
                () -> "/remote route(s) registered outside the federation contract binder, bypassing the"
                        + " versioned contract:%n%s".formatted(String.join(System.lineSeparator(), bypassing)));
    }

    /**
     * A route class whose {@code CONTRACT} is missing from the catalog aggregation would
     * still register and enforce, but its endpoints would contribute to no surface hash -
     * payload changes would silently stop rolling versions, which is the exact failure the
     * contract exists to prevent.
     */
    @Test
    void everyDeclaredContractIsAggregatedInTheCatalog() {
        for (Class<? extends Routes> owner : application.boundOrder()) {
            List<FederationEndpoint> contract = contractOf(owner);
            assertTrue(
                    FederationContractCatalog.ENDPOINTS.containsAll(contract),
                    () -> owner.getSimpleName()
                            + " declares a federation contract that is not aggregated in the catalog");
        }
    }

    /**
     * {@code RemoteBoardWebhookRoutes} registers literal paths such as
     * {@code /remote/boards/webhook/mention} that the {@code /remote/boards/{boardKey}/...} routes of
     * the other remote board classes would match just as well, so its binding has to come first.
     * No route collides today, which is why the rule above cannot see it; this keeps the next one
     * from being added in the wrong order.
     */
    @Test
    void remoteBoardWebhookRoutesAreBoundBeforeRemoteBoardRoutes() {
        List<String> order =
                application.boundOrder().stream().map(Class::getSimpleName).toList();
        int webhooks = order.indexOf("RemoteBoardWebhookRoutes");
        int boards = order.indexOf("RemoteBoardRoutes");
        assertTrue(webhooks >= 0 && boards >= 0, "both remote board route classes are bound");
        assertTrue(
                webhooks < boards,
                () -> "RemoteBoardWebhookRoutes must be bound before RemoteBoardRoutes so its literal"
                        + " /remote/boards/webhook/* paths are matched before /remote/boards/{boardKey}/*;"
                        + " found positions %d and %d".formatted(webhooks, boards));
    }

    /**
     * A route never holds a station's federation key.
     *
     * <p>Signing is asked for by station id, and the key stays inside the key store that keeps it
     * encrypted. A route that reads the key, or passes it on, has put key material within reach of
     * a request handler for no reason at all.
     */
    @Test
    void routesNeverHandleFederationKeyMaterial() {
        List<String> violations = new ArrayList<>();
        for (Class<? extends Routes> owner : application.boundOrder()) {
            for (JavaClass type : classAndNested(owner)) {
                type.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> isKeyMaterial(dependency.getTargetClass()))
                        .forEach(dependency -> violations.add(dependency.getDescription()));
                type.getAccessesFromSelf().stream()
                        .filter(access ->
                                FEDERATION_KEY_MEMBER.matcher(access.getName()).find())
                        .forEach(access -> violations.add(access.getDescription()));
            }
        }
        assertTrue(
                violations.isEmpty(),
                () -> "handles a station's federation key; name the station and let the signer sign instead:%n%s"
                        .formatted(String.join(System.lineSeparator(), violations)));
    }

    private static boolean isKeyMaterial(JavaClass type) {
        return type.isAssignableTo(java.security.PrivateKey.class)
                || type.getSimpleName().equals("StationKeyStore");
    }

    /**
     * Whether every value the path takes is a station's address, so that the row the caller named
     * is the station itself and resolving it is the whole of saying which station is meant.
     */
    private static boolean addressesAStationItself(Route route) {
        return PATH_PARAMETER.matcher(route.path()).results().allMatch(parameter -> STATION_PARAMETER
                .matcher(parameter.group())
                .matches());
    }

    private static boolean isHandlerShaped(Method method) {
        return Modifier.isPrivate(method.getModifiers())
                && !Modifier.isStatic(method.getModifiers())
                && !method.isSynthetic()
                && method.getReturnType() == void.class
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == Context.class;
    }

    private static boolean isStationFree(Route route) {
        return Arrays.stream(route.owner().getDeclaredMethods())
                .filter(method -> route.body().filter(method.getName()::equals).isPresent())
                .anyMatch(method -> method.isAnnotationPresent(StationFree.class));
    }

    /**
     * Whether a handler, or a method of its own class it reaches, says which station the addressed
     * row belongs to.
     *
     * <p>Methods of the class are followed rather than named, because the codebase reaches its checks
     * through chains of its own: an attendance entry resolves to its session, and the session is what
     * carries the station. Following them is what keeps this rule about the check being made rather
     * than about which words the handler happens to contain.
     */
    private static boolean checksStation(Route route) {
        JavaClass owner = routeClasses.get(route.owner());
        if (route.body().isEmpty()) return false;
        var pending =
                new ArrayDeque<JavaCodeUnit>(codeUnitsNamed(owner, route.body().get()));
        var visited = new HashSet<JavaCodeUnit>();
        while (!pending.isEmpty()) {
            JavaCodeUnit unit = pending.pop();
            if (!visited.add(unit)) continue;
            for (JavaAccess<?> access : unit.getAccessesFromSelf()) {
                if (isStationEvidence(access)) return true;
                JavaClass target = access.getTargetOwner();
                if (isPartOf(owner, target)) pending.addAll(codeUnitsNamed(target, access.getName()));
            }
        }
        return false;
    }

    private static boolean isStationEvidence(JavaAccess<?> access) {
        String name = access.getName();
        if (STATION_EVIDENCE.matcher(name).find()) return true;
        if (access instanceof JavaMethodCall call
                && CALLER_READS.contains(name)
                && call.getTarget().getRawParameterTypes().isEmpty()) return true;
        if (name.equals("guards")) return true;
        return access.getTargetOwner().getSimpleName().endsWith("Guards");
    }

    private static boolean isPartOf(JavaClass owner, JavaClass type) {
        return type.getName().equals(owner.getName()) || type.getName().startsWith(owner.getName() + "$");
    }

    private static List<JavaCodeUnit> codeUnitsNamed(JavaClass type, String name) {
        return type.getCodeUnits().stream()
                .filter(unit -> unit.getName().equals(name))
                .toList();
    }

    private static List<JavaClass> classAndNested(Class<?> owner) {
        return routeClasses.stream()
                .filter(type ->
                        type.getName().equals(owner.getName()) || type.getName().startsWith(owner.getName() + "$"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> demoBlockedPaths() throws Exception {
        var field = ApiServer.class.getDeclaredField("DEMO_BLOCKED_PATHS");
        field.setAccessible(true);
        return (Set<String>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static List<FederationEndpoint> contractOf(Class<?> routeClass) {
        try {
            return (List<FederationEndpoint>) routeClass.getField("CONTRACT").get(null);
        } catch (NoSuchFieldException e) {
            return List.of();
        } catch (IllegalAccessException e) {
            throw new AssertionError("Unreadable CONTRACT field on " + routeClass, e);
        }
    }
}
