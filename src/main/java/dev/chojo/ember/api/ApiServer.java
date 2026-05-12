/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.entity.StationMember;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import dev.chojo.ember.repository.StationRepository;
import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.OpenApiPluginConfiguration;
import io.javalin.openapi.plugin.swagger.SwaggerConfiguration;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import io.javalin.security.RouteRole;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.javalin.http.ContentType.JSON;
import static java.util.Objects.requireNonNullElse;

@Singleton
public class ApiServer {
    private static final Logger log = LoggerFactory.getLogger(ApiServer.class);
    private static final String API_PREFIX = "/api/v1";
    public static final String ATTR_SESSION = "session";

    private static final Set<String> DEMO_BLOCKED_PATHS =
            Set.of("/api/v1/auth/change-password", "/api/v1/auth/set-password");

    private final Set<Routes> routes;
    private final Api apiConfig;
    private final Demo demoConfig;
    private final AccessManager accessManager;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final StationRepository stationRepository;

    @Inject
    public ApiServer(
            Set<Routes> routes,
            Api apiConfig,
            Demo demoConfig,
            AccessManager accessManager,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            StationRepository stationRepository) {
        this.routes = routes;
        this.apiConfig = apiConfig;
        this.demoConfig = demoConfig;
        this.accessManager = accessManager;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.stationRepository = stationRepository;
    }

    public void start() {
        var app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
            config.jsonMapper(jacksonMapper());

            var publicDir = System.getenv().getOrDefault("EMBER_PUBLIC_DIR", "public");
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = publicDir;
                staticFiles.location = io.javalin.http.staticfiles.Location.EXTERNAL;
            });

            config.registerPlugin(new OpenApiPlugin(this::configureOpenApi));
            config.registerPlugin(new SwaggerPlugin(this::configureSwagger));

            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));

            config.routes.before(ctx -> {
                if (ctx.method() == HandlerType.OPTIONS) return;
                log.trace(
                        "Received request on route: {} {}\nHeaders:\n{}\nBody:\n{}",
                        ctx.method() + " " + ctx.url(),
                        requireNonNullElse(ctx.queryString(), ""),
                        ctx.headerMap().entrySet().stream()
                                .map(h -> "   " + h.getKey() + ": " + h.getValue())
                                .collect(Collectors.joining("\n")),
                        ctx.contentType() == null
                                        || ctx.contentType().contains("text")
                                        || ctx.contentType().equals(JSON)
                                ? ctx.body().substring(0, Math.min(ctx.body().length(), 180))
                                : "Bytes");
            });

            config.routes.after(ctx -> {
                if (ctx.method() == HandlerType.OPTIONS) return;
                log.trace(
                        "Answered request on route: {} {}\nStatus: {}\nHeaders:\n{}\nBody:\n{}",
                        ctx.method() + " " + ctx.url(),
                        requireNonNullElse(ctx.queryString(), ""),
                        ctx.status(),
                        ctx.res().getHeaderNames().stream()
                                .map(h -> "   " + h + ": " + ctx.res().getHeader(h))
                                .collect(Collectors.joining("\n")),
                        JSON.equals(ctx.res().getContentType())
                                ? requireNonNullElse(ctx.result(), "")
                                        .substring(
                                                0,
                                                Math.min(
                                                        requireNonNullElse(ctx.result(), "")
                                                                .length(),
                                                        360))
                                : "Bytes");
            });

            if (demoConfig.enabled()) {
                config.routes.before(this::handleDemoGuard);
            }

            config.routes.beforeMatched(this::handleAccess);

            setupExceptionHandlers(config.routes);

            // Public demo endpoints
            config.routes.get(
                    API_PREFIX + "/demo/status",
                    ctx -> ctx.json(java.util.Map.of("demo", demoConfig.enabled(), "dev", demoConfig.dev())));

            if (demoConfig.enabled() || demoConfig.dev()) {
                config.routes.get(API_PREFIX + "/demo/accounts", this::handleDemoAccounts);
            }

            for (Routes route : routes) {
                route.register(config.routes, API_PREFIX);
            }
        });
        app.start(apiConfig.host(), apiConfig.port());
        log.info("API server started on {}:{}", apiConfig.host(), apiConfig.port());
    }

    private void handleDemoGuard(@NotNull Context ctx) {
        String path = ctx.path();
        var method = ctx.method();

        // Block password changes
        if (DEMO_BLOCKED_PATHS.contains(path)) {
            throw new io.javalin.http.BadRequestResponse("This action is disabled in demo mode");
        }

        // Block station create/delete (but allow GET and PUT for manage)
        if (path.startsWith("/api/v1/admin/stations") && (method == HandlerType.POST || method == HandlerType.DELETE)) {
            throw new io.javalin.http.BadRequestResponse("Station management is disabled in demo mode");
        }

        // Block role changes on members and groups
        if (method == HandlerType.PUT
                && (path.matches("/api/v1/station-members/\\d+/roles") || path.matches("/api/v1/groups/\\d+/roles"))) {
            throw new io.javalin.http.BadRequestResponse("Role changes are disabled in demo mode");
        }
    }

    private void handleDemoAccounts(@NotNull Context ctx) {
        var allStations = stationRepository.findAll();
        if (allStations.isEmpty()) {
            ctx.json(java.util.List.of());
            return;
        }
        int stationId = allStations.getFirst().id();
        var members = stationMemberRepository.findByStation(stationId);
        var accounts = new java.util.ArrayList<DemoAccount>();
        for (StationMember member : members) {
            accountRepository.findById(member.accountId()).ifPresent(account -> {
                var roles = stationMemberRepository.findRoles(member.id());
                var roleNames = roles.stream().map(r -> r.role().name()).toList();
                accounts.add(new DemoAccount(account.email(), account.firstName(), account.lastName(), roleNames));
            });
        }
        ctx.json(accounts);
    }

    public record DemoAccount(String email, String firstName, String lastName, java.util.List<String> roles) {}

    private void handleAccess(@NotNull Context ctx) {
        Set<RouteRole> routeRoles = ctx.routeRoles();

        // Routes with no roles defined are public
        if (routeRoles.isEmpty()) {
            return;
        }

        // Extract session token from Authorization header
        String authHeader = ctx.header("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        if (token == null || token.isBlank()) {
            throw new UnauthorizedResponse("Missing or invalid Authorization header");
        }

        // Parse optional station ID
        Integer stationId = null;
        String stationIdHeader = ctx.header("X-Station-Id");
        if (stationIdHeader != null && !stationIdHeader.isBlank()) {
            try {
                stationId = Integer.parseInt(stationIdHeader);
            } catch (NumberFormatException e) {
                throw new UnauthorizedResponse("Invalid X-Station-Id header");
            }
        }

        // Resolve user session with account info and roles
        Optional<UserSession> sessionOpt = accessManager.resolveUserSession(token, stationId);
        if (sessionOpt.isEmpty()) {
            throw new UnauthorizedResponse("Invalid or expired session");
        }

        UserSession session = sessionOpt.get();
        ctx.attribute(ATTR_SESSION, session);

        // Record user agent and update last-used timestamp
        String userAgent = ctx.userAgent();
        accountRepository.touchSession(token, userAgent);

        // If route only requires LOGIN, authenticated is enough
        if (routeRoles.size() == 1 && routeRoles.contains(Roles.LOGIN)) {
            return;
        }

        // Station-scoped role check
        Set<Roles> userRoles = session.roles();

        // Check if user has any of the required roles (roles are already expanded to include children)
        for (RouteRole required : routeRoles) {
            if (userRoles.contains(required)) {
                return;
            }
        }

        ctx.header("X-Required-Roles", routeRoles.toString());
        ctx.header("X-User-Roles", userRoles.toString());
        throw new ForbiddenResponse("Insufficient permissions. Required: " + routeRoles + ", Current: " + userRoles);
    }

    private Jackson3Mapper jacksonMapper() {
        ObjectMapper mapper = JsonMapper.builder()
                .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX"))
                .build();
        return new Jackson3Mapper(mapper);
    }

    private void configureOpenApi(OpenApiPluginConfiguration config) {
        config.withDocumentationPath("/docs")
                .withDefinitionConfiguration((version, definition) -> definition.info(info -> {
                    info.title("Ember API");
                    info.version("1.0");
                    info.description("Documentation for the Ember API");
                }));
    }

    private void configureSwagger(SwaggerConfiguration config) {
        config.withDocumentationPath("/docs").withUiPath("/swagger-ui");
    }

    private void setupExceptionHandlers(RoutesConfig routes) {
        routes.exception(ApiException.class, (err, ctx) -> ctx.json(
                        new ErrorResponseWrapper(err.getClass().getSimpleName(), err.getMessage()))
                .status(err.status()));

        routes.exception(HttpResponseException.class, (err, ctx) -> ctx.json(new ErrorResponseWrapper(
                        HttpStatus.forStatus(err.getStatus()).getMessage(), err.getMessage()))
                .status(err.getStatus()));

        routes.exception(IllegalArgumentException.class, (err, ctx) -> ctx.json(
                        new ErrorResponseWrapper("Invalid Input", err.getMessage()))
                .status(HttpStatus.BAD_REQUEST));

        routes.exception(Exception.class, (err, ctx) -> {
            log.error("Unhandled exception on route {}", ctx.path(), err);
            ctx.json(new ErrorResponseWrapper("Internal Server Error")).status(HttpStatus.INTERNAL_SERVER_ERROR);
        });
    }
}
