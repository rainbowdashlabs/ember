/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.spec;

import dev.chojo.ember.api.RegisteredRoutes;
import dev.chojo.ember.api.RegisteredRoutes.Route;
import dev.chojo.ember.api.RouteHarness;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApis;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route annotations are the index the API description is generated from, so each one has to sit on
 * the route it names, and every route has to have one.
 *
 * <p>Both directions are checked against the router the application builds, see {@link RegisteredRoutes}:
 * every registered route carries exactly one {@link OpenApi} on the method that answers it, naming the
 * same path and method, and no annotation names a route that is not registered. A route nobody describes
 * reaches the frontend untyped; an annotation on the wrong handler hands the frontend another route's
 * record.
 *
 * <p>Exempt by rule, never by list:
 * <ul>
 *   <li>Anything outside the API prefix, such as the sitemap: it answers crawlers with XML, not the
 *       application with JSON.</li>
 *   <li>{@code /remote/} routes: they are the server-to-server federation surface, whose contract is the
 *       signed and versioned federation contract, not this description. No browser calls them.</li>
 *   <li>{@code /beacon/} routes: the intake another instance's beacon reports to, signed with that
 *       instance's key. The sender is this same application on another host, it writes the payload
 *       from the same records, and no browser calls it.</li>
 *   <li>{@code /public/webhooks/} routes: the mail providers' delivery callbacks, whose bodies the
 *       providers define and the application only reads.</li>
 * </ul>
 */
class ApiRouteIndexTest {

    private static final String PREFIX = RouteHarness.PREFIX;

    private static final Pattern SPANNING_PARAMETER = Pattern.compile("<([^>]*)>");

    private static final List<Predicate<String>> EXEMPT = List.of(
            path -> !path.startsWith(PREFIX + "/"),
            path -> path.startsWith(PREFIX + "/remote/"),
            path -> path.startsWith(PREFIX + "/beacon/"),
            path -> path.startsWith(PREFIX + "/public/webhooks/"));

    private static List<Route> routes;
    private static List<Description> descriptions;

    @BeforeAll
    static void readTheRouterAndItsAnnotations() {
        RegisteredRoutes application = RegisteredRoutes.application();
        routes = application.routes();
        descriptions = application.boundOrder().stream()
                .distinct()
                .flatMap(ApiRouteIndexTest::descriptionsOf)
                .toList();
    }

    /** Every route of the API carries one annotation, on its own handler, naming its path and method. */
    @Test
    void everyRouteIsDescribedOnItsHandler() {
        List<String> violations = new ArrayList<>();
        for (Route route : routes) {
            if (isExempt(route.path())) continue;
            List<Description> naming = descriptions.stream()
                    .filter(description -> description.names(route))
                    .toList();
            if (naming.isEmpty()) {
                violations.add("undescribed: " + route);
            } else if (naming.size() > 1) {
                violations.add("described %d times: %s in %s".formatted(naming.size(), route, naming));
            } else if (!naming.getFirst().sitsOn(route)) {
                violations.add("described on another handler: %s in %s".formatted(route, naming.getFirst()));
            }
        }
        assertTrue(violations.isEmpty(), () -> "Routes without exactly one @OpenApi on their handler:%n%s"
                .formatted(String.join(System.lineSeparator(), violations)));
    }

    /** No annotation describes a route the application does not register. */
    @Test
    void everyDescriptionNamesARegisteredRoute() {
        Set<String> registered = routes.stream()
                .map(route -> route.method().name() + " " + describedPath(route))
                .collect(Collectors.toSet());
        List<String> stale = descriptions.stream()
                .filter(description -> !registered.contains(description.key()))
                .map(Description::toString)
                .toList();
        assertTrue(stale.isEmpty(), () -> "@OpenApi naming a route that is not registered:%n%s"
                .formatted(String.join(System.lineSeparator(), stale)));
    }

    /**
     * The route's path as a description writes it. A parameter that may span slashes is {@code <name>}
     * to the router and {@code {name}} to OpenAPI, which knows no other kind.
     */
    private static String describedPath(Route route) {
        return SPANNING_PARAMETER.matcher(route.path()).replaceAll("{$1}");
    }

    private static boolean isExempt(String path) {
        return EXEMPT.stream().anyMatch(rule -> rule.test(path));
    }

    private static Stream<Description> descriptionsOf(Class<?> owner) {
        return Stream.concat(Stream.of(owner.getDeclaredMethods()), Stream.of(owner.getDeclaredFields()))
                .flatMap(handler -> descriptionsOn(owner, handler));
    }

    private static Stream<Description> descriptionsOn(Class<?> owner, AccessibleObject handler) {
        String name = ((Member) handler).getName();
        return annotationsOf(handler).stream().flatMap(annotation -> Stream.of(annotation.methods())
                .map(method -> new Description(owner, name, annotation.path(), method)));
    }

    private static List<OpenApi> annotationsOf(AccessibleObject handler) {
        List<OpenApi> annotations = new ArrayList<>(List.of(handler.getAnnotationsByType(OpenApi.class)));
        OpenApis container = handler.getAnnotation(OpenApis.class);
        if (container != null && annotations.isEmpty()) annotations.addAll(List.of(container.value()));
        return annotations;
    }

    /**
     * One method on one path, as an annotation on a route class member describes it.
     *
     * @param owner   the route class
     * @param handler the member carrying the annotation
     * @param path    the path it names
     * @param method  the method it names
     */
    private record Description(Class<?> owner, String handler, String path, HttpMethod method) {

        String key() {
            return method.name() + " " + path;
        }

        boolean names(Route route) {
            return path.equals(describedPath(route))
                    && method.name().equals(route.method().name());
        }

        /**
         * Whether it sits on the method answering the route: the handler itself, or for a handler
         * written inline the method that handler hands the request to.
         */
        boolean sitsOn(Route route) {
            return owner == route.owner()
                    && (handler.equals(route.handler())
                            || route.body().filter(handler::equals).isPresent());
        }

        @Override
        public String toString() {
            return "%s %s on %s.%s".formatted(method, path, owner.getSimpleName(), handler);
        }
    }
}
