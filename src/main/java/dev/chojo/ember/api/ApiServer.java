/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.service.ApiRequestLogger;
import dev.chojo.ember.feature.system.service.DemoService;
import dev.chojo.ember.util.DevErrorWriter;
import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.staticfiles.Location;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.OpenApiPluginConfiguration;
import io.javalin.openapi.plugin.swagger.SwaggerConfiguration;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import io.javalin.plugin.bundled.CorsPluginConfig;
import io.javalin.security.RouteRole;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.javalin.http.ContentType.JSON;
import static java.util.Objects.requireNonNullElse;

/**
 * Configures and starts the Javalin HTTP server.
 * Sets up CORS, OpenAPI/Swagger, authentication/authorization, exception handling,
 * cache-control headers, demo mode guards, and registers all feature route groups.
 */
@Singleton
public class ApiServer {
    public static final String ATTR_SESSION = "session";
    private static final Logger log = LoggerFactory.getLogger(ApiServer.class);
    private static final String API_PREFIX = "/api/v1";
    private static final Set<String> DEMO_BLOCKED_PATHS =
            Set.of("/api/v1/auth/change-password", "/api/v1/auth/set-password");

    private final Set<Routes> routes;
    private final Api apiConfig;
    private final Demo demoConfig;
    private final AccessManager accessManager;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final StationRepository stationRepository;
    private final ProfileFieldService profileFieldService;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;
    private final ApiRequestLogger apiRequestLogger;
    private final DemoService demoService;

    @Inject
    public ApiServer(
            Set<Routes> routes,
            Api apiConfig,
            Demo demoConfig,
            AccessManager accessManager,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            StationRepository stationRepository,
            ProfileFieldService profileFieldService,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository,
            ApiRequestLogger apiRequestLogger,
            DemoService demoService) {
        this.routes = routes;
        this.apiConfig = apiConfig;
        this.demoConfig = demoConfig;
        this.accessManager = accessManager;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.stationRepository = stationRepository;
        this.profileFieldService = profileFieldService;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
        this.apiRequestLogger = apiRequestLogger;
        this.demoService = demoService;
        this.apiRequestLogger.start();
    }

    /**
     * Creates the Javalin application, registers all middleware, routes, and plugins, then starts the server.
     */
    public void start() {
        if (demoConfig.dev()) {
            DevErrorWriter.clearOnStartup();
        }
        var app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
            config.jsonMapper(jacksonMapper());

            var publicDir = Path.of(System.getenv().getOrDefault("EMBER_PUBLIC_DIR", "public"));
            if (Files.isDirectory(publicDir)) {
                config.staticFiles.add(staticFiles -> {
                    staticFiles.directory = publicDir.toString();
                    staticFiles.location = Location.EXTERNAL;
                });
                config.spaRoot.addFile("/", publicDir.resolve("index.html").toString(), Location.EXTERNAL);
            } else {
                config.spaRoot.addFile("/", "/static/index.html", Location.CLASSPATH);
            }

            config.registerPlugin(new OpenApiPlugin(this::configureOpenApi));
            config.registerPlugin(new SwaggerPlugin(this::configureSwagger));

            config.bundledPlugins.enableCors(cors -> cors.addRule(CorsPluginConfig.CorsRule::anyHost));

            config.routes.before(ctx -> {
                if (ctx.method() == HandlerType.OPTIONS) return;
                String bodyLog;
                if (ctx.path().contains("/ai/")
                        || ctx.path().contains("/auth/")
                        || ctx.path().contains("/admin/config/")) {
                    bodyLog = "[REDACTED - contains sensitive data]";
                } else if (ctx.contentType() == null
                        || ctx.contentType().contains("text")
                        || ctx.contentType().equals(JSON)) {
                    bodyLog = ctx.body().substring(0, Math.min(ctx.body().length(), 180));
                } else {
                    bodyLog = "Bytes";
                }
                log.trace(
                        "Received request on route: {} {}\nHeaders:\n{}\nBody:\n{}",
                        ctx.method() + " " + ctx.url(),
                        requireNonNullElse(ctx.queryString(), ""),
                        ctx.headerMap().entrySet().stream()
                                .map(h -> "   " + h.getKey() + ": " + h.getValue())
                                .collect(Collectors.joining("\n")),
                        bodyLog);
            });

            config.routes.after(ctx -> {
                if (ctx.method() == HandlerType.OPTIONS) return;
                String responseBody;
                if (ctx.path().contains("/auth/")
                        || ctx.path().contains("/ai/")
                        || ctx.path().contains("/admin/config/")) {
                    responseBody = "[REDACTED]";
                } else if (JSON.equals(ctx.res().getContentType())) {
                    String result = requireNonNullElse(ctx.result(), "");
                    responseBody = result.substring(0, Math.min(result.length(), 360));
                } else {
                    responseBody = "Bytes";
                }
                log.trace(
                        "Answered request on route: {} {}\nStatus: {}\nHeaders:\n{}\nBody:\n{}",
                        ctx.method() + " " + ctx.url(),
                        requireNonNullElse(ctx.queryString(), ""),
                        ctx.status(),
                        ctx.res().getHeaderNames().stream()
                                .map(h -> "   " + h + ": " + ctx.res().getHeader(h))
                                .collect(Collectors.joining("\n")),
                        responseBody);
            });

            // Cache-control headers
            config.routes.after(this::applyCacheHeaders);

            // Federation response headers
            config.routes.after(this::applyFederationHeaders);

            // API request timing
            config.routes.before(ctx -> ctx.attribute("_requestStart", System.currentTimeMillis()));
            config.routes.after(ctx -> {
                Long start = ctx.attribute("_requestStart");
                if (start != null && ctx.path().startsWith(API_PREFIX)) {
                    long duration = System.currentTimeMillis() - start;
                    apiRequestLogger.record(ctx.method().name(), ctx.path(), ctx.statusCode(), duration);
                }
            });

            if (demoConfig.enabled()) {
                config.routes.before(this::handleDemoGuard);
            }

            config.routes.beforeMatched(this::handleAccess);

            setupExceptionHandlers(config.routes);

            // Public endpoints
            config.routes.get(
                    API_PREFIX + "/public/config",
                    ctx -> ctx.json(Map.of(
                            "demoUrl",
                            apiConfig.demoUrl() != null ? apiConfig.demoUrl() : "",
                            "demo",
                            demoConfig.enabled() || demoConfig.dev(),
                            "version",
                            loadAppVersion())));

            // Public demo endpoints
            config.routes.get(
                    API_PREFIX + "/demo/status",
                    ctx -> ctx.json(Map.of("demo", demoConfig.enabled(), "dev", demoConfig.dev())));

            if (demoConfig.enabled() || demoConfig.dev()) {
                config.routes.get(API_PREFIX + "/demo/accounts", this::handleDemoAccounts);
            }

            if (demoConfig.dev()) {
                config.routes.post(API_PREFIX + "/dev/errors", this::handleDevErrorReport);
            }

            for (Routes route : routes) {
                route.register(config.routes, API_PREFIX);
            }
        });
        app.start(apiConfig.host(), apiConfig.port());
        log.info("API server started on {}:{}", apiConfig.host(), apiConfig.port());
    }

    /**
     * Before-handler that blocks destructive operations in demo mode,
     * such as password changes, station creation/deletion, and role modifications.
     */
    private void handleDemoGuard(@NotNull Context ctx) {
        String path = ctx.path();
        var method = ctx.method();

        // Block password changes
        if (DEMO_BLOCKED_PATHS.contains(path)) {
            throw new BadRequestResponse("This action is disabled in demo mode");
        }

        // Block station create/delete (but allow GET and PUT for manage)
        if (path.startsWith("/api/v1/admin/stations") && (method == HandlerType.POST || method == HandlerType.DELETE)) {
            throw new BadRequestResponse("Station management is disabled in demo mode");
        }

        // Block role changes on members and groups
        if (method == HandlerType.PUT
                && (path.matches("/api/v1/station-members/\\d+/roles") || path.matches("/api/v1/groups/\\d+/roles"))) {
            throw new BadRequestResponse("Role changes are disabled in demo mode");
        }
    }

    /**
     * Serves the list of demo accounts with their roles, groups, and tags for the demo login page.
     */
    private void handleDevErrorReport(@NotNull Context ctx) {
        record ErrorReport(String source, String message, String stack, String context) {}
        var report = ctx.bodyAsClass(ErrorReport.class);
        DevErrorWriter.writeFrontend(
                report.source() != null ? report.source() : "unknown",
                report.message() != null ? report.message() : "",
                report.stack() != null ? report.stack() : "",
                report.context() != null ? report.context() : "");
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void handleDemoAccounts(@NotNull Context ctx) {
        var allStations = stationRepository.findAll();
        if (allStations.isEmpty()) {
            ctx.json(List.of());
            return;
        }
        var stationGroups = new ArrayList<Map<String, Object>>();
        for (var station : allStations) {
            var members = stationMemberRepository.findByStation(station.id());
            var accounts = new ArrayList<DemoAccount>();
            for (StationMember member : members) {
                if (member.accountId() == null) continue;
                accountRepository.findById(member.accountId()).ifPresent(account -> {
                    var roles = stationMemberRepository.findRoles(member.id());
                    var roleNames = roles.stream().map(r -> r.role().name()).toList();
                    var groupNames = memberGroupRepository.findGroupsForMember(member.id()).stream()
                            .map(MemberGroup::name)
                            .toList();
                    var tagNames = userTagRepository.findTagsForMember(member.id()).stream()
                            .map(UserTag::name)
                            .toList();
                    boolean complete = profileFieldService.isProfileComplete(member.id(), station.id(), roleNames);
                    accounts.add(new DemoAccount(
                            account.email(),
                            account.firstName(),
                            account.lastName(),
                            roleNames,
                            groupNames,
                            tagNames,
                            complete));
                });
            }
            if (!accounts.isEmpty()) {
                stationGroups.add(Map.of(
                        "stationId", station.uid().toString(),
                        "stationName", station.name(),
                        "accounts", accounts));
            }
        }
        // Always return flat list from the first station (primary)
        // Additional stations are appended with stationName for display
        if (stationGroups.isEmpty()) {
            ctx.json(List.of());
        } else {
            ctx.json(stationGroups);
        }
    }

    /**
     * Before-matched handler that enforces authentication and role-based authorization.
     * Resolves the session from the Authorization header, stores it as a context attribute,
     * and checks that the user has at least one of the required route roles.
     */
    private void handleAccess(@NotNull Context ctx) {
        Set<RouteRole> routeRoles = ctx.routeRoles();

        // Routes with no roles defined are public — still populate session if token or federation headers present
        if (routeRoles.isEmpty()) {
            // Try federation signature auth for /remote/ endpoints
            if (ctx.header("X-Federation-Station-Id") != null) {
                accessManager
                        .resolveFederationSession(ctx)
                        .ifPresent(s -> ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION, s));
            }
            // Try bearer token auth (best effort)
            String publicAuthHeader = ctx.header("Authorization");
            if (publicAuthHeader != null && publicAuthHeader.startsWith("Bearer ")) {
                String publicToken = publicAuthHeader.substring(7);
                if (!publicToken.isBlank()) {
                    Station publicStation = null;
                    String publicStationId = ctx.header("X-Station-Id");
                    if (publicStationId != null && !publicStationId.isBlank()) {
                        try {
                            publicStation = stationRepository
                                    .findByUid(UUID.fromString(publicStationId))
                                    .orElse(null);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    accessManager
                            .resolveUserSession(publicToken, publicStation)
                            .ifPresent(s -> ctx.attribute(ATTR_SESSION, s));
                }
            }
            return;
        }

        // Extract session token from Authorization header or query param (for iframe/download URLs)
        String authHeader = ctx.header("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        if ((token == null || token.isBlank()) && ctx.queryParam("token") != null) {
            token = ctx.queryParam("token");
        }

        if (token == null || token.isBlank()) {
            throw new UnauthorizedResponse("Missing or invalid Authorization header");
        }

        // Parse optional station UID from header or query param
        Station station = null;
        String stationIdHeader = ctx.header("X-Station-Id");
        if ((stationIdHeader == null || stationIdHeader.isBlank()) && ctx.queryParam("stationId") != null) {
            stationIdHeader = ctx.queryParam("stationId");
        }
        if (stationIdHeader != null && !stationIdHeader.isBlank()) {
            try {
                var uid = UUID.fromString(stationIdHeader);
                station = stationRepository.findByUid(uid).orElse(null);
                if (station == null) {
                    throw new UnauthorizedResponse("Unknown station");
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid X-Station-Id header value", e);
                throw new UnauthorizedResponse("Invalid X-Station-Id header");
            }
        }

        // Resolve user session with account info and roles
        Optional<UserSession> sessionOpt = accessManager.resolveUserSession(token, station);
        if (sessionOpt.isEmpty()) {
            throw new UnauthorizedResponse("Invalid or expired session");
        }

        UserSession session = sessionOpt.get();
        ctx.attribute(ATTR_SESSION, session);

        // Record user agent, location, and update last-used timestamp
        String userAgent = ctx.userAgent();
        String location = ctx.header("CF-IPCountry");
        accountRepository.touchSession(token, userAgent, location);

        // Track activity for demo idle reset
        if (demoConfig.enabled()) {
            demoService.recordActivity();
        }

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

    /**
     * Creates the Jackson 3 JSON mapper configured with ISO date formatting.
     */
    private Jackson3Mapper jacksonMapper() {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new StationIdModule(stationRepository))
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

    /**
     * Registers exception handlers that convert exceptions into standardized JSON error responses.
     */
    private void setupExceptionHandlers(RoutesConfig routes) {
        boolean devErrors = demoConfig.dev();

        routes.exception(ApiException.class, (err, ctx) -> {
            int code = err.status().getCode();
            if (code >= 500) {
                log.error("API error {} on {} {}: {}", code, ctx.method(), ctx.path(), err.getMessage(), err);
                if (devErrors) DevErrorWriter.write(err, ctx.method() + " " + ctx.path());
            } else if (code == 404) {
                log.warn("API 404 on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
                if (devErrors) DevErrorWriter.write(err, ctx.method() + " " + ctx.path());
            } else if (code >= 400 && code != 401) {
                log.warn("API error {} on {} {}: {}", code, ctx.method(), ctx.path(), err.getMessage());
            }
            ctx.json(new ErrorResponseWrapper(err.getClass().getSimpleName(), err.getMessage()))
                    .status(err.status());
        });

        routes.exception(HttpResponseException.class, (err, ctx) -> {
            int code = err.getStatus();
            if (code >= 500) {
                log.error("HTTP {} on {} {}: {}", code, ctx.method(), ctx.path(), err.getMessage(), err);
                if (devErrors) DevErrorWriter.write(err, ctx.method() + " " + ctx.path());
            } else if (code == 404) {
                log.warn("HTTP 404 on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
                if (devErrors) DevErrorWriter.write(err, ctx.method() + " " + ctx.path());
            } else if (code >= 400 && code != 401) {
                log.warn("HTTP {} on {} {}: {}", code, ctx.method(), ctx.path(), err.getMessage());
            }
            ctx.json(new ErrorResponseWrapper(HttpStatus.forStatus(code).getMessage(), err.getMessage()))
                    .status(code);
        });

        routes.exception(IllegalArgumentException.class, (err, ctx) -> {
            log.warn("Invalid input on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            ctx.json(new ErrorResponseWrapper("Invalid Input", err.getMessage()))
                    .status(HttpStatus.BAD_REQUEST);
        });

        routes.exception(Exception.class, (err, ctx) -> {
            log.error("Unhandled exception on route {} {}", ctx.method(), ctx.path(), err);
            if (devErrors) DevErrorWriter.write(err, ctx.method() + " " + ctx.path());
            ctx.json(new ErrorResponseWrapper("Internal Server Error")).status(HttpStatus.INTERNAL_SERVER_ERROR);
        });
    }

    /**
     * After-handler that sets appropriate Cache-Control and ETag headers based on the request path.
     */
    private void applyCacheHeaders(@NotNull Context ctx) {
        if (ctx.method() != HandlerType.GET) return;

        String path = ctx.path();

        // Binary resources (images, avatars, logos) — private short cache
        if (path.contains("/avatar") || path.contains("/logo") || path.contains("/image")) {
            ctx.header("Cache-Control", "private, max-age=300");
            return;
        }

        // Public legal documents — cache with version-based ETag
        if (path.startsWith(API_PREFIX + "/public/")) {
            ctx.header("Cache-Control", "public, max-age=3600");
            addETag(ctx);
            return;
        }

        // Demo status — rarely changes
        if (path.startsWith(API_PREFIX + "/demo/")) {
            ctx.header("Cache-Control", "public, max-age=60");
            return;
        }

        // All other API GET responses — private, use ETag for conditional requests
        if (path.startsWith(API_PREFIX + "/")) {
            ctx.header("Cache-Control", "private, no-cache");
            addETag(ctx);
        }
    }

    /**
     * After-handler that sets federation station identity headers on responses from
     * {@code /federated/} and {@code /remote/} endpoints.
     * For remote endpoints (server-to-server), the headers identify this station.
     * For federated endpoints, route handlers set these headers themselves per entity.
     */
    private void applyFederationHeaders(@NotNull Context ctx) {
        String path = ctx.path();
        if (!path.startsWith(API_PREFIX + "/remote/")) return;

        // For /remote/ responses, identify this station (the one serving the data)
        FederationSession fedSession = ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION);
        if (fedSession != null) {
            stationRepository.findById(fedSession.stationId()).ifPresent(station -> FederationHeaders.setStationHeaders(ctx, station));
        }
    }

    /**
     * Computes an ETag from the response body hash and handles conditional 304 Not Modified responses.
     */
    private void addETag(@NotNull Context ctx) {
        String body = ctx.result();
        if (body == null || body.isEmpty()) return;

        String etag = "\"" + Integer.toHexString(body.hashCode()) + "\"";
        ctx.header("ETag", etag);

        String ifNoneMatch = ctx.header("If-None-Match");
        if (etag.equals(ifNoneMatch)) {
            ctx.status(HttpStatus.NOT_MODIFIED);
            ctx.result("");
        }
    }

    private String loadAppVersion() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("version")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (Exception e) {
            log.warn("Failed to read version resource", e);
        }
        return "unknown";
    }

    /**
     * Representation of a demo account returned by the demo accounts endpoint.
     *
     * @param email           the account email
     * @param firstName       the first name
     * @param lastName        the last name
     * @param roles           the role names assigned to this member
     * @param groups          the group names the member belongs to
     * @param tags            the tag names assigned to this member
     * @param profileComplete whether the member's profile is fully filled in
     */
    public record DemoAccount(
            String email,
            String firstName,
            String lastName,
            List<String> roles,
            List<String> groups,
            List<String> tags,
            boolean profileComplete) {}
}
