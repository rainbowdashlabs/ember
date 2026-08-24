/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.feature.federation.contract.FederationContractCatalog;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level conventions for route classes that bytecode analysis cannot express:
 * station-ownership checks must go through the RouteSupport helpers (so the 403/404
 * policy stays centralized), UUID path parameters must be read via pathUuid, and no
 * registration may be shadowed by an earlier one - within a class or across the whole
 * binding order.
 */
class RouteSourceConventionsTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /** What the router puts in front of every path the route classes register. */
    private static final String API_PREFIX = "/api/v1";

    /** The one blocked read: exporting the data is the act, and doing it with a GET does not change that. */
    private static final String GDPR_EXPORT = "/api/v1/session/gdpr-export";

    private static final Pattern INLINE_STATION_COMPARISON = Pattern.compile(
            "\\.stationId\\(\\)\\s*!=\\s*session\\.stationId\\(\\)|session\\.stationId\\(\\)\\s*!=\\s*[\\w.]+\\.stationId\\(\\)");

    private static final Pattern INLINE_UUID_PATH_PARSE = Pattern.compile("UUID\\.fromString\\(\\s*ctx\\.pathParam");

    private static final Pattern REMOTE_DIRECT_REGISTRATION = Pattern.compile("prefix\\s*\\+\\s*\"/remote");

    private static final Pattern PREFIX_ASSIGNMENT =
            Pattern.compile("String\\s+(\\w+)\\s*=\\s*prefix\\s*\\+\\s*\"([^\"]*)\"");

    private static final Pattern REGISTRATION =
            Pattern.compile("routes\\.(get|post|put|patch|delete)\\(\\s*(\\w+)\\s*\\+\\s*\"([^\"]*)\"");

    /**
     * A handler: a private method taking nothing but the Javalin context. Every one of them exists
     * to answer a route, so every one of them has to be wired to one.
     */
    private static final Pattern HANDLER_DECLARATION =
            Pattern.compile("private\\s+void\\s+(\\w+)\\(\\s*Context\\s+\\w+\\s*\\)");

    /** A registration that names its handler and the roles it declares, so all three read together. */
    private static final Pattern HANDLER_REGISTRATION = Pattern.compile(
            "routes\\.(get|post|put|patch|delete)\\(\\s*\\w+\\s*\\+\\s*\"([^\"]*)\"\\s*,\\s*this::(\\w+)([^)]*)\\)");

    /** The same for the federation contract binder, which names an endpoint constant instead. */
    private static final Pattern CONTRACT_REGISTRATION =
            Pattern.compile("\\.handle\\(\\s*(\\w+)\\s*,\\s*this::(\\w+)\\)");

    /** A handler signature: the one taking the request context, rather than a namesake. */
    private static final String HANDLER_SIGNATURE = "\\b%s\\s*\\(\\s*Context\\s+\\w+\\s*\\)\\s*\\{";

    private static final Pattern UNSCOPED_DELETE = Pattern.compile("deleteById\\(\\s*\"([a-z_]+)\"");

    private static final Pattern CREATE_TABLE =
            Pattern.compile("CREATE TABLE [a-z_]+\\.([a-z_]+)\\s*\\(([^;]*?)\\n\\);", Pattern.DOTALL);

    /**
     * The unscoped deletes on station-carrying tables that existed when the rule was written. Every
     * one of them is reached through a handler that checks the station; the rule keeps the next one
     * from being added without that check. This list shrinks, never grows.
     */
    private static final Set<String> UNSCOPED_STATION_DELETES = Set.of(
            "attendance_report_preset",
            "attendance_template",
            "board",
            "checklist",
            "equipment_exchange_request",
            "equipment_procurement",
            "event_category",
            "event_template",
            "federation_partner",
            "form",
            "inventory",
            "inventory_container",
            "inventory_container_kind",
            "kb_file",
            "kb_folder",
            "kb_tag",
            "lost_and_found_item",
            "member_group",
            "news",
            "problem_report",
            "procedure",
            "profile_field",
            "quiz_catalog",
            "quiz_category",
            "quiz_test",
            "registration_code",
            "station_event",
            "station_event_break",
            "station_member",
            "station_page",
            "two_factor_policy",
            "user_tag",
            "waiting_list");

    /**
     * What saying which station a row belongs to looks like in this codebase: the session's station,
     * one of the ownership helpers, a {@code *Guards} class, or the federation partner whose station
     * scopes a {@code /remote} lookup.
     */
    private static final Pattern STATION_EVIDENCE = Pattern.compile(
            "stationId|requireOwned|requireSameStation|requireShared|guards\\.|requirePartner|ForPartner|isShared"
                    + "|resolveStation|\\.account\\(\\)|\\.member\\(\\)|accountId|requireManaged");

    private static final Pattern ROUTE_BINDING =
            Pattern.compile("routesBinder\\.addBinding\\(\\)\\.to\\((\\w+)\\.class\\)");

    private static final Path EMBER_MODULE =
            Path.of("src", "main", "java", "dev", "chojo", "ember", "EmberModule.java");

    @Test
    void routesUseOwnershipHelpersInsteadOfInlineStationComparisons() throws IOException {
        assertNoMatches(
                INLINE_STATION_COMPARISON,
                "uses an inline station-id comparison; use RouteSupport.requireOwnedOrNotFound");
    }

    /**
     * A handler nothing points at answers nothing.
     *
     * <p>Java says nothing about it: the method compiles, the class compiles, the tests that never
     * call the endpoint pass, and the feature is simply absent at runtime. It is an easy mistake to
     * make while moving registrations around, and an expensive one to find, because the first
     * report is a 404 from something that plainly exists in the source.
     */
    @Test
    void everyHandlerIsWiredToARoute() throws IOException {
        List<String> orphans = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith("Routes.java"))
                    .toList()) {
                String source = Files.readString(path);
                Matcher declared = HANDLER_DECLARATION.matcher(source);
                while (declared.find()) {
                    String handler = declared.group(1);
                    if (!source.contains("this::" + handler)) {
                        orphans.add("%s: %s".formatted(path.getFileName(), handler));
                    }
                }
            }
        }
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
     * {@code session.stationId()}, calling a {@code RouteSupport.requireOwned*} helper, going
     * through a {@code *Guards} class, or resolving the federation partner whose station scopes a
     * {@code /remote} lookup. A handler that does none of them, directly or through a private
     * helper beside it, is either a hole or an endpoint that genuinely belongs to no station, and
     * the second kind says so with {@link StationFree}.
     */
    @Test
    void everyHandlerTakingAnIdChecksTheStation() throws IOException {
        List<String> unchecked = new ArrayList<>();
        for (String routeClass : boundRouteClasses()) {
            Path path = sourceOf(routeClass);
            String source = Files.readString(path);
            for (var handler : idHandlersIn(source)) {
                if (isStationFree(source, handler.method())) continue;
                if (checksStation(source, handler.method())) continue;
                unchecked.add(
                        "%s: %s %s -> %s".formatted(routeClass, handler.verb(), handler.path(), handler.method()));
            }
        }
        assertTrue(
                unchecked.isEmpty(),
                () -> ("handler(s) taking an id from the address without a station check; add one, or mark the"
                                + " endpoint @StationFree with the reason it belongs to no station:%n%s")
                        .formatted(String.join(System.lineSeparator(), unchecked)));
    }

    /**
     * A table that carries a station is deleted from with the station in the statement.
     *
     * <p>{@code deleteById} takes a table and an id and asks nothing else, which makes the unscoped
     * delete the path of least resistance for a table that has a station to be scoped by, and that
     * has been the shape of every cross-station delete found here. {@code deleteByIdInStation}
     * beside it says the station, and a delete that names the wrong station removes nothing rather
     * than somebody else's row.
     *
     * <p>The list below is the state of the codebase when the rule was written, not a set of
     * blessed exceptions: each of those deletes is reached today through a handler the rule above
     * proves checks the station, so none of them is open, and each is one refactor away from the
     * scoped call. What the rule buys now is that the next one cannot be added. Shrink the list
     * when you touch the feature; never grow it.
     */
    @Test
    void noNewUnscopedDeleteOnAStationTable() throws IOException {
        Set<String> stationTables = tablesWithAStation();
        List<String> unscoped = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher deletes = UNSCOPED_DELETE.matcher(Files.readString(path));
                while (deletes.find()) {
                    String table = deletes.group(1);
                    if (stationTables.contains(table) && !UNSCOPED_STATION_DELETES.contains(table)) {
                        unscoped.add("%s: deleteById(\"%s\")".formatted(path.getFileName(), table));
                    }
                }
            }
        }
        assertTrue(unscoped.isEmpty(), () -> ("delete(s) by id on a table that carries a station; use"
                        + " SqlSupport.deleteByIdInStation so the statement names the station:%n%s")
                .formatted(String.join(System.lineSeparator(), unscoped)));
    }

    /**
     * The tables whose {@code CREATE TABLE} declares a {@code station_id}, read from the migrations
     * rather than listed here, so a table that gains or loses its station is followed automatically.
     */
    private Set<String> tablesWithAStation() throws IOException {
        Set<String> tables = new java.util.HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "resources", "database"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                Matcher creates = CREATE_TABLE.matcher(Files.readString(path));
                while (creates.find()) {
                    if (creates.group(2).matches("(?s).*\\bstation_id\\b.*")) tables.add(creates.group(1));
                }
            }
        }
        return tables;
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
     * still register and enforce, but its endpoints would contribute to no surface hash -
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
     * after a parameter path that also matches it is dead - the parameter route wins and the literal
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
                () -> "Unreachable route registrations - a literal path is matched by an"
                        + " earlier parameter path in the same class, so the literal never runs:%n%s"
                                .formatted(String.join(System.lineSeparator(), violations)));
    }

    /**
     * The same rule across class boundaries. Every route class shares one prefix - {@code ApiServer}
     * registers them all under {@code API_PREFIX} - and the multibinder is consumed in binding order,
     * so the registrations of the whole application form a single ordered list and a literal path in
     * a later-bound class is just as dead as one later in the same file.
     *
     * <p>Before this existed, precedence between classes rested on two hand-written orderings. The
     * one covering the event routes is now a special case of this rule and has been deleted; the
     * remaining one guards a collision that does not exist yet, which no consequence-based check can
     * see.
     */
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
        List<String> reads = new ArrayList<>();
        for (String routeClass : boundRouteClasses()) {
            for (Registration registration : registrationsIn(sourceOf(routeClass))) {
                if (!registration.verb().equals("get")) continue;
                String path = API_PREFIX + registration.path();
                if (blocked.contains(path) && !path.equals(GDPR_EXPORT)) {
                    reads.add("%s: GET %s".formatted(routeClass, path));
                }
            }
        }
        assertTrue(
                reads.isEmpty(),
                () -> "blocked outright in demo mode although the address also answers a read; move it to"
                        + " DEMO_BLOCKED_WRITE_PATHS:%n%s".formatted(String.join(System.lineSeparator(), reads)));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> demoBlockedPaths() throws Exception {
        var field = ApiServer.class.getDeclaredField("DEMO_BLOCKED_PATHS");
        field.setAccessible(true);
        return (Set<String>) field.get(null);
    }

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
                violations.add("%s:%d %s %s is unreachable - %s (%s:%d) is bound earlier and matches it first"
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
                () -> "Unreachable route registrations - a literal path is matched by a parameter path"
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
                    violations.add("%s:%d %s %s is unreachable - %s (line %d) matches it first"
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
    /**
     * A registration that names a handler and takes at least one parameter from the address.
     * Registrations without a parameter address a collection rather than a row, and the permission
     * the route declares is the whole of their gating.
     */
    private List<IdHandler> idHandlersIn(String source) {
        List<IdHandler> handlers = new ArrayList<>();
        Matcher local = HANDLER_REGISTRATION.matcher(source);
        while (local.find()) {
            // An instance-admin route answers for the instance rather than for a station, which is
            // the whole of its scope.
            if (local.group(4).contains("InstancePermission")) continue;
            if (local.group(2).contains("{")) {
                handlers.add(new IdHandler(local.group(1), local.group(2), local.group(3)));
            }
        }
        Matcher contract = CONTRACT_REGISTRATION.matcher(source);
        while (contract.find()) {
            String endpoint = contract.group(1);
            Matcher declared = Pattern.compile(endpoint + "\\s*=\\s*FederationEndpoint[\\s\\S]{0,400}?\"([^\"]*)\"")
                    .matcher(source);
            String endpointPath = declared.find() ? declared.group(1) : "";
            if (endpointPath.contains("{") || endpointPath.isEmpty()) {
                handlers.add(
                        new IdHandler("remote", endpointPath.isEmpty() ? endpoint : endpointPath, contract.group(2)));
            }
        }
        return handlers;
    }

    /**
     * Whether a handler, or a helper it reaches in the same class, says which station the addressed
     * row belongs to.
     *
     * <p>Helpers are followed rather than named, because the codebase reaches its checks through
     * chains of its own: an attendance entry resolves to its session, and the session is what
     * carries the station. Following them is what keeps this rule about the check being made rather
     * than about which words the file happens to contain.
     */
    private boolean checksStation(String source, String handler) {
        // The handler, not a namesake: a route class declares `register(JavalinDefaultRoutingApi,
        // String)` for its registrations, and an event handler called `register` is a different
        // method entirely. A body this cannot find is a gap in the reading, not a hole in the
        // handler.
        String body = bodyOf(source, HANDLER_SIGNATURE.formatted(Pattern.quote(handler)));
        if (body.isEmpty()) return true;
        if (STATION_EVIDENCE.matcher(body).find()) return true;
        Matcher calls = Pattern.compile("\\b(\\w+)\\(").matcher(body);
        var visited = new HashSet<String>();
        visited.add(handler);
        while (calls.find()) {
            if (reachesCheck(source, calls.group(1), visited)) return true;
        }
        return false;
    }

    private boolean reachesCheck(String source, String method, Set<String> visited) {
        if (!visited.add(method)) return false;
        String body = methodBody(source, method);
        if (body.isEmpty()) return false;
        if (STATION_EVIDENCE.matcher(body).find()) return true;
        Matcher calls = Pattern.compile("\\b(\\w+)\\(").matcher(body);
        while (calls.find()) {
            if (reachesCheck(source, calls.group(1), visited)) return true;
        }
        return false;
    }

    /**
     * The body of a method of the given name, brace-balanced from its signature. Empty when the
     * class declares no such method, which is what a call to something defined elsewhere looks like.
     */
    private String methodBody(String source, String method) {
        return bodyOf(source, "\\b" + Pattern.quote(method) + "\\s*\\([^)]*\\)\\s*(?:throws [\\w, ]+)?\\{");
    }

    private String bodyOf(String source, String signaturePattern) {
        Matcher signature = Pattern.compile(signaturePattern).matcher(source);
        if (!signature.find()) return "";
        int depth = 0;
        for (int i = signature.end() - 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(signature.end(), i);
        }
        return "";
    }

    private boolean isStationFree(String source, String handler) {
        Matcher annotated = Pattern.compile(
                        "@StationFree\\([\\s\\S]{0,300}?\\)\\s*(?:@[\\w.]+(?:\\([\\s\\S]*?\\))?\\s*)*"
                                + "private\\s+void\\s+" + Pattern.quote(handler) + "\\s*\\(")
                .matcher(source);
        return annotated.find();
    }

    private record IdHandler(String verb, String path, String method) {}

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
     * compiled {@code CONTRACT} constant rather than re-parsed from source - the binder
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
         * specific - meaning the other one can never be reached.
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
