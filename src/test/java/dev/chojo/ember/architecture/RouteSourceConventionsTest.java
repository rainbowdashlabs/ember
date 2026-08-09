/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import dev.chojo.ember.feature.federation.contract.FederationContractCatalog;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level conventions for route classes that bytecode analysis cannot express:
 * station-ownership checks must go through the RouteSupport helpers (so the 403/404
 * policy stays centralized), UUID path parameters must be read via pathUuid, and no
 * registration may be shadowed by an earlier one — within a class or across the whole
 * binding order.
 */
class RouteSourceConventionsTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    private static final Pattern INLINE_STATION_COMPARISON = Pattern.compile(
            "\\.stationId\\(\\)\\s*!=\\s*session\\.stationId\\(\\)|session\\.stationId\\(\\)\\s*!=\\s*[\\w.]+\\.stationId\\(\\)");

    private static final Pattern INLINE_UUID_PATH_PARSE = Pattern.compile("UUID\\.fromString\\(\\s*ctx\\.pathParam");

    private static final Pattern REMOTE_DIRECT_REGISTRATION = Pattern.compile("prefix\\s*\\+\\s*\"/remote");

    private static final Pattern PREFIX_ASSIGNMENT =
            Pattern.compile("String\\s+(\\w+)\\s*=\\s*prefix\\s*\\+\\s*\"([^\"]*)\"");

    private static final Pattern REGISTRATION =
            Pattern.compile("routes\\.(get|post|put|patch|delete)\\(\\s*(\\w+)\\s*\\+\\s*\"([^\"]*)\"");

    private static final Pattern ROUTE_BINDING =
            Pattern.compile("routesBinder\\.addBinding\\(\\)\\.to\\((\\w+)\\.class\\)");

    private static final Path EMBER_MODULE =
            Path.of("src", "main", "java", "dev", "chojo", "ember", "EmberModule.java");

    @Test
    void routesUseOwnershipHelpersInsteadOfInlineStationComparisons() throws IOException {
        assertNoMatches(
                INLINE_STATION_COMPARISON,
                "uses an inline station-id comparison; use RouteSupport.requireOwnedOrNotFound (default) or requireOwned");
    }

    @Test
    void routesUsePathUuidInsteadOfInlineParsing() throws IOException {
        assertNoMatches(INLINE_UUID_PATH_PARSE, "parses a UUID path parameter inline; use RouteSupport.pathUuid");
    }

    /**
     * Every {@code /remote} endpoint must be registered through the federation contract
     * binder. The historical direct-registration pattern concatenated the prefix with a
     * literal remote path, so its reappearance means an endpoint bypasses the versioned
     * contract.
     */
    @Test
    void noRemoteRouteBypassesTheContract() throws IOException {
        assertNoMatches(
                REMOTE_DIRECT_REGISTRATION,
                "registers a /remote route outside the federation contract binder, bypassing the versioned contract");
    }

    /**
     * A route class whose {@code CONTRACT} is missing from the catalog aggregation would
     * still register and enforce, but its endpoints would contribute to no surface hash —
     * payload changes would silently stop rolling versions, which is the exact failure the
     * contract exists to prevent.
     */
    @Test
    void everyDeclaredContractIsAggregatedInTheCatalog() throws IOException {
        for (String routeClass : boundRouteClasses()) {
            List<FederationEndpoint> contract = contractOf(routeClassOf(sourceOf(routeClass)));
            assertTrue(
                    FederationContractCatalog.ENDPOINTS.containsAll(contract),
                    () -> routeClass + " declares a federation contract that is not aggregated in the catalog");
        }
    }

    /**
     * {@code RemoteBoardWebhookRoutes} registers literal paths such as
     * {@code /remote/boards/webhook/mention} that the {@code /remote/boards/{boardKey}/...} routes of
     * the other remote board classes match just as well, so its binding has to come first.
     */
    @Test
    void remoteBoardWebhookRoutesAreBoundBeforeRemoteBoardRoutes() throws IOException {
        List<String> lines = Files.readAllLines(EMBER_MODULE);
        int webhooks = bindingLine(lines, "RemoteBoardWebhookRoutes");
        int boards = bindingLine(lines, "RemoteBoardRoutes");
        assertTrue(
                webhooks < boards,
                () -> "RemoteBoardWebhookRoutes must be bound before RemoteBoardRoutes so its literal"
                        + " /remote/boards/webhook/* paths are matched before /remote/boards/{boardKey}/*;"
                        + " found lines %d and %d".formatted(webhooks, boards));
    }

    /**
     * Javalin answers with the first registered handler that matches, so a literal path registered
     * after a parameter path that also matches it is dead — the parameter route wins and the literal
     * segment is parsed as the parameter value.
     *
     * <p>This is the rule <em>within</em> a class, which is where the failure actually showed up:
     * splitting the board routes uncovered {@code checklist/reorder} registered after
     * {@code checklist/{itemId}} and {@code tickets/reorder} after {@code tickets/{ticketNumber}}.
     * Both were unreachable and answered 400 on the integer parse. The test below applies the same
     * rule <em>across</em> classes.
     */
    @Test
    void noLiteralPathIsShadowedByAnEarlierParameterPath() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith("Routes.java"))
                    .toList()) {
                violations.addAll(shadowedRegistrations(path));
            }
        }
        assertTrue(
                violations.isEmpty(),
                () -> "Unreachable route registrations — a literal path is matched by an"
                        + " earlier parameter path in the same class, so the literal never runs:%n%s"
                                .formatted(String.join(System.lineSeparator(), violations)));
    }

    /**
     * The same rule across class boundaries. Every route class shares one prefix — {@code ApiServer}
     * registers them all under {@code API_PREFIX} — and the multibinder is consumed in binding order,
     * so the registrations of the whole application form a single ordered list and a literal path in
     * a later-bound class is just as dead as one later in the same file.
     *
     * <p>Before this existed, precedence between classes rested on two hand-written orderings. The
     * one covering the event routes is now a special case of this rule and has been deleted; the
     * remaining one guards a collision that does not exist yet, which no consequence-based check can
     * see.
     */
    @Test
    void noLiteralPathIsShadowedByAnEarlierParameterPathInAnotherClass() throws IOException {
        List<Registration> registrations = new ArrayList<>();
        for (String routeClass : boundRouteClasses()) {
            for (Registration registration : registrationsIn(sourceOf(routeClass))) {
                registrations.add(registration.inClass(routeClass));
            }
        }
        assertTrue(
                registrations.size() > 1000,
                () -> "Expected the registrations of every bound route class, found " + registrations.size());
        List<String> violations = new ArrayList<>();
        for (int later = 0; later < registrations.size(); later++) {
            for (int earlier = 0; earlier < later; earlier++) {
                Registration first = registrations.get(earlier);
                Registration second = registrations.get(later);
                if (first.owner.equals(second.owner) || !first.shadows(second)) continue;
                violations.add("%s:%d %s %s is unreachable — %s (%s:%d) is bound earlier and matches it first"
                        .formatted(
                                second.owner,
                                second.line,
                                second.verb,
                                second.path,
                                first.path,
                                first.owner,
                                first.line));
            }
        }
        assertTrue(
                violations.isEmpty(),
                () -> "Unreachable route registrations — a literal path is matched by a parameter path"
                        + " in an earlier-bound route class, so the literal never runs:%n%s"
                                .formatted(String.join(System.lineSeparator(), violations)));
    }

    /**
     * The route classes in the order {@code EmberModule} binds them, which is the order
     * {@code ApiServer} registers them in.
     */
    private List<String> boundRouteClasses() throws IOException {
        List<String> classes = new ArrayList<>();
        for (String line : Files.readAllLines(EMBER_MODULE)) {
            Matcher binding = ROUTE_BINDING.matcher(line);
            if (binding.find()) classes.add(binding.group(1));
        }
        assertTrue(classes.size() > 100, () -> "Expected the full route binding list, found " + classes.size());
        return classes;
    }

    private Path sourceOf(String routeClass) throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            return files.filter(p -> p.getFileName().toString().equals(routeClass + ".java"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No source file found for bound route class " + routeClass));
        }
    }

    private List<String> shadowedRegistrations(Path path) throws IOException {
        List<Registration> registrations = registrationsIn(path);
        List<String> violations = new ArrayList<>();
        for (int later = 0; later < registrations.size(); later++) {
            for (int earlier = 0; earlier < later; earlier++) {
                Registration first = registrations.get(earlier);
                Registration second = registrations.get(later);
                if (first.shadows(second)) {
                    violations.add("%s:%d %s %s is unreachable — %s (line %d) matches it first"
                            .formatted(path, second.line, second.verb, second.path, first.path, first.line));
                }
            }
        }
        return violations;
    }

    /**
     * Reads the registrations of one route class in source order, resolving the local prefix
     * variables that most {@code register} methods build from the {@code prefix} argument.
     *
     * <p>Matched against the file as one string rather than line by line: a registration whose
     * arguments are wrapped onto the next line is the formatter's default once the call grows, and a
     * line-anchored scan sees none of them. Closing that blind spot took the scan from 750 resolved
     * registrations to 1,068, and the first thing the extra 318 turned up was a real unreachable
     * route in the federated board class.
     *
     * <p>Remote federation classes register through the contract binder instead of calling the
     * router directly, so their registrations are read from the {@code FederationEndpoint} constants
     * in the order the {@code handle} calls bind them.
     */
    private List<Registration> registrationsIn(Path path) throws IOException {
        String source = Files.readString(path);
        Map<String, String> prefixes = new HashMap<>();
        prefixes.put("prefix", "");
        List<Registration> registrations = new ArrayList<>();
        Matcher assignments = PREFIX_ASSIGNMENT.matcher(source);
        Matcher matches = REGISTRATION.matcher(source);
        int nextAssignment = assignments.find() ? assignments.start() : -1;
        while (matches.find()) {
            while (nextAssignment >= 0 && nextAssignment < matches.start()) {
                prefixes.put(assignments.group(1), assignments.group(2));
                nextAssignment = assignments.find() ? assignments.start() : -1;
            }
            String base = prefixes.get(matches.group(2));
            if (base == null) continue;
            registrations.add(
                    new Registration(matches.group(1), base + matches.group(3), lineAt(source, matches.start())));
        }
        registrations.addAll(contractRegistrationsIn(path));
        return registrations;
    }

    /**
     * The registrations a class binds through the federation contract binder. Read from the
     * compiled {@code CONTRACT} constant rather than re-parsed from source — the binder
     * enforces at startup that handlers are bound exactly in contract order, so the list
     * <em>is</em> the router order, typed and complete.
     */
    private List<Registration> contractRegistrationsIn(Path path) {
        List<FederationEndpoint> contract = contractOf(routeClassOf(path));
        return contract.stream()
                .map(endpoint -> new Registration(
                        endpoint.method().name().toLowerCase(), endpoint.path(), contract.indexOf(endpoint) + 1))
                .toList();
    }

    private Class<?> routeClassOf(Path path) {
        String qualified =
                MAIN_SOURCES.relativize(path).toString().replace(".java", "").replace(java.io.File.separatorChar, '.');
        try {
            return Class.forName(qualified);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Route source file has no class on the test classpath: " + path, e);
        }
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

    private int lineAt(String source, int offset) {
        return (int) source.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    }

    private record Registration(String verb, String path, int line, String owner) {

        Registration(String verb, String path, int line) {
            this(verb, path, line, "");
        }

        Registration inClass(String routeClass) {
            return new Registration(verb, path, line, routeClass);
        }

        private List<String> segments() {
            return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toList();
        }

        private static boolean isParameter(String segment) {
            return segment.startsWith("{") || segment.startsWith("<");
        }

        /**
         * Whether this registration matches everything the other one does, while being less
         * specific — meaning the other one can never be reached.
         */
        boolean shadows(Registration other) {
            if (!verb.equals(other.verb) || path.equals(other.path)) return false;
            List<String> mine = segments();
            List<String> theirs = other.segments();
            if (mine.size() != theirs.size()) return false;
            boolean lessSpecific = false;
            for (int i = 0; i < mine.size(); i++) {
                String a = mine.get(i);
                String b = theirs.get(i);
                if (isParameter(a)) {
                    if (!isParameter(b)) lessSpecific = true;
                    continue;
                }
                if (!a.equals(b)) return false;
            }
            return lessSpecific;
        }
    }

    private int bindingLine(List<String> lines, String routeClass) {
        String binding = "addBinding().to(%s.class)".formatted(routeClass);
        return Stream.iterate(0, i -> i + 1)
                .limit(lines.size())
                .filter(i -> lines.get(i).contains(binding))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No route binding found for " + routeClass));
    }

    private void assertNoMatches(Pattern pattern, String message) throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            violations = files.filter(path -> path.getFileName().toString().endsWith("Routes.java"))
                    .flatMap(path -> matchesIn(path, pattern))
                    .toList();
        }
        assertTrue(violations.isEmpty(), () -> "%s:%n%s"
                .formatted(message, String.join(System.lineSeparator(), violations)));
    }

    private Stream<String> matchesIn(Path path, Pattern pattern) {
        try {
            List<String> lines = Files.readAllLines(path);
            return Stream.iterate(0, i -> i + 1)
                    .limit(lines.size())
                    .filter(i -> pattern.matcher(lines.get(i)).find())
                    .map(i -> "%s:%d %s".formatted(path, i + 1, lines.get(i).strip()));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
