/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.insights.service.BotClassifier;
import dev.chojo.ember.feature.insights.service.PageHitRecorder;
import dev.chojo.ember.feature.insights.service.RefererDomainExtractor;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyForTransferException;
import dev.chojo.ember.feature.system.service.ApiRequestLogger;
import dev.chojo.ember.feature.system.service.DemoService;
import dev.chojo.ember.feature.traffic.service.AuthBucketClassifier;
import dev.chojo.ember.feature.traffic.service.StationResolver;
import dev.chojo.ember.feature.traffic.service.StationTrafficRecorder;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.util.ClientIp;
import dev.chojo.ember.util.DevErrorWriter;
import dev.chojo.ember.util.LogRedaction;
import io.javalin.Javalin;
import io.javalin.compression.CompressionStrategy;
import io.javalin.compression.Gzip;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import io.javalin.config.SizeUnit;
import io.javalin.http.BadRequestResponse;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
    // Note on the transfer endpoints: /station/transfer/create-token and
    // /station/transfer/abort are NOT blocked here on purpose. They are mandatory for the
    // cross-instance transfer test harness (the compose.dev.yaml "transfer" profile), and the
    // import-side counterpart /admin/transfer/import is already gated by
    // InstancePermission.ADMINISTRATOR which demo accounts do not hold - so the source can
    // mint a token but a stranger on the demo cannot pull a station off of it.
    private static final Set<String> DEMO_BLOCKED_PATHS = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/set-password",
            "/api/v1/session/account",
            "/api/v1/session/gdpr-export",
            "/api/v1/session/avatar",
            "/api/v1/station/manage/mail/test",
            "/api/v1/station/manage/mail/test-mail",
            "/api/v1/station/manage/request-delete",
            "/api/v1/station/manage/import",
            "/api/v1/station/manage/logo",
            "/api/v1/station-applications",
            "/api/v1/media/files",
            "/api/v1/kb/files/upload",
            "/api/v1/kb/files/import-document",
            "/api/v1/station/storage/backend/probe",
            "/api/v1/station/storage/backend/probe-config",
            "/api/v1/station/storage/backend/apply",
            "/api/v1/admin/storage/backend/probe",
            "/api/v1/admin/storage/backend/probe-config",
            "/api/v1/admin/storage/backend/apply",
            "/api/v1/ai/generate",
            "/api/v1/ai/generate-questions");

    private final Set<Routes> routes;
    private final Api apiConfig;
    private final Auth authConfig;
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
    private final StationTrafficRecorder trafficRecorder;
    private final StationResolver stationResolver;
    private final AuthBucketClassifier authClassifier;
    private final PageHitRecorder pageHitRecorder;
    private final RefererDomainExtractor refererExtractor;
    private final BotClassifier botClassifier;
    private final TwoFactorService twoFactorService;
    private final Network network;
    private final GlobalRateLimiter globalRateLimiter;

    @Inject
    public ApiServer(
            Set<Routes> routes,
            Api apiConfig,
            Auth authConfig,
            Demo demoConfig,
            AccessManager accessManager,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            StationRepository stationRepository,
            ProfileFieldService profileFieldService,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository,
            ApiRequestLogger apiRequestLogger,
            DemoService demoService,
            StationTrafficRecorder trafficRecorder,
            StationResolver stationResolver,
            AuthBucketClassifier authClassifier,
            PageHitRecorder pageHitRecorder,
            RefererDomainExtractor refererExtractor,
            BotClassifier botClassifier,
            TwoFactorService twoFactorService,
            Network network,
            GlobalRateLimiter globalRateLimiter) {
        this.routes = routes;
        this.apiConfig = apiConfig;
        this.authConfig = authConfig;
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
        this.trafficRecorder = trafficRecorder;
        this.stationResolver = stationResolver;
        this.authClassifier = authClassifier;
        this.pageHitRecorder = pageHitRecorder;
        this.refererExtractor = refererExtractor;
        this.botClassifier = botClassifier;
        this.twoFactorService = twoFactorService;
        this.network = network;
        this.globalRateLimiter = globalRateLimiter;
        this.apiRequestLogger.start();
        this.trafficRecorder.start();
        this.pageHitRecorder.start();
    }

    /**
     * Estimates the inbound byte count for a request: declared content length (zero when
     * not set or unknown) plus a cheap header-bytes approximation. Used by the per-station
     * traffic recorder; the precision is operational-observability grade, not billing-grade.
     */
    private static long estimateIngressBytes(Context ctx) {
        long bodyBytes = Math.max(0, ctx.req().getContentLengthLong());
        long headerBytes = 0;
        for (var entry : ctx.headerMap().entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name != null) headerBytes += name.length();
            if (value != null) headerBytes += value.length();
            headerBytes += 4;
        }
        String method = ctx.method() != null ? ctx.method().name() : "";
        String path = ctx.path() != null ? ctx.path() : "";
        return bodyBytes + headerBytes + method.length() + path.length() + 12;
    }

    /**
     * Estimates the outbound byte count for a response. Resolution order:
     *
     * <ol>
     *   <li>{@link #jettyContentCount Jetty's response-side content counter}, which covers
     *       streamed downloads (page files, feeds, large JSON) that never set a
     *       {@code Content-Length} header.</li>
     *   <li>The declared {@code Content-Length} response header for fixed-length responses.</li>
     *   <li>The length of {@code ctx.result()} for legacy {@code String}-bodied routes.</li>
     * </ol>
     *
     * <p>Adds an approximation of response header bytes on top - same precision target as
     * ingress.
     */
    private static long estimateEgressBytes(Context ctx) {
        long bodyBytes = jettyContentCount(ctx);
        if (bodyBytes <= 0) {
            String contentLength = ctx.res().getHeader("Content-Length");
            if (contentLength != null) {
                try {
                    bodyBytes = Long.parseLong(contentLength);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (bodyBytes <= 0 && ctx.result() != null) {
            bodyBytes = ctx.result().length();
        }
        long headerBytes = 0;
        for (String name : ctx.res().getHeaderNames()) {
            headerBytes += name.length();
            String value = ctx.res().getHeader(name);
            if (value != null) headerBytes += value.length();
            headerBytes += 4;
        }
        return Math.max(0, bodyBytes) + headerBytes + 12;
    }

    /**
     * Returns the number of bytes Jetty has written for the current response, by walking the
     * servlet response wrapper chain and reflectively invoking
     * {@code org.eclipse.jetty.server.Response#getContentCount()}. Returns {@code -1} when
     * the lookup fails - callers must fall back to {@code Content-Length} / {@code ctx.result()}.
     *
     * <p>Reflection lets the recorder stay independent of the Jetty version pinned by Javalin
     * - Jetty 11 named the method {@code getContentCount}; Jetty 12 added
     * {@code getBytesWritten}. We try both.
     */
    private static long jettyContentCount(Context ctx) {
        HttpServletResponse res = ctx.res();
        while (res instanceof HttpServletResponseWrapper wrapper
                && wrapper.getResponse() instanceof HttpServletResponse inner) {
            res = inner;
        }
        for (String method : new String[] {"getContentCount", "getBytesWritten"}) {
            try {
                var m = res.getClass().getMethod(method);
                Object value = m.invoke(res);
                if (value instanceof Long l) return l;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return -1;
    }

    private static String bodyDigest(String body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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
            configureCompression(config);

            config.jetty.multipartConfig.maxFileSize(apiConfig.maxUploadSizeBytes(), SizeUnit.BYTES);
            config.jetty.multipartConfig.maxInMemoryFileSize(1, SizeUnit.MB);
            config.jetty.multipartConfig.maxTotalRequestSize(apiConfig.maxRequestSizeBytes(), SizeUnit.BYTES);

            config.registerPlugin(new OpenApiPlugin(this::configureOpenApi));
            config.registerPlugin(new SwaggerPlugin(this::configureSwagger));

            if (demoConfig.dev()) {
                // config.bundledPlugins.enableDevLogging();
            }

            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                for (String origin : apiConfig.allowedOrigins()) {
                    rule.allowHost(origin);
                }
                if (demoConfig.dev()) {
                    // The frontends a dev instance is asked from: the dev server, the second one
                    // the cross-instance transfer harness runs, and the end-to-end suite's own.
                    // An origin missing here is refused with 400 on every write, which reads as
                    // the request being malformed rather than as the port being unknown.
                    rule.allowHost("http://localhost:3000");
                    rule.allowHost("http://localhost:3001");
                    rule.allowHost("http://localhost:3010");
                }
            }));

            config.routes.before(this::enforceGlobalRateLimit);

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
                        ctx.method() + " " + LogRedaction.redactQueryString(ctx.url()),
                        LogRedaction.redactQueryString(requireNonNullElse(ctx.queryString(), "")),
                        LogRedaction.redactHeaders(ctx.headerMap()).entrySet().stream()
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
                var responseHeaders = new LinkedHashMap<String, String>();
                for (String h : ctx.res().getHeaderNames()) {
                    responseHeaders.put(h, ctx.res().getHeader(h));
                }
                log.trace(
                        "Answered request on route: {} {}\nStatus: {}\nHeaders:\n{}\nBody:\n{}",
                        ctx.method() + " " + LogRedaction.redactQueryString(ctx.url()),
                        LogRedaction.redactQueryString(requireNonNullElse(ctx.queryString(), "")),
                        ctx.status(),
                        LogRedaction.redactHeaders(responseHeaders).entrySet().stream()
                                .map(h -> "   " + h.getKey() + ": " + h.getValue())
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

            // Per-station traffic counters. Recorded after the response is
            // committed so Jetty has populated content-length on the response side.
            config.routes.after(ctx -> {
                if (ctx.method() == HandlerType.OPTIONS) return;
                long ingress = estimateIngressBytes(ctx);
                long egress = estimateEgressBytes(ctx);
                trafficRecorder.record(
                        stationResolver.resolve(ctx).orElse(null), authClassifier.classify(ctx), ingress, egress);
            });

            // Per-public-page hit counters. Only fires when a public page
            // handler has resolved the page row and stashed its id on the context - file
            // serves, partner lookups, and 404s are excluded by construction.
            config.routes.after(ctx -> {
                if (ctx.method() != HandlerType.GET) return;
                if (ctx.statusCode() >= 400) return;
                Object pageIdAttr = ctx.attribute(PageHitRecorder.ATTR_PAGE_HIT_PAGE_ID);
                if (!(pageIdAttr instanceof Integer pageId)) return;
                String country = ctx.header("CF-IPCountry");
                String referer = refererExtractor.extract(ctx.header("Referer"));
                boolean isBot = botClassifier.isBot(ctx.userAgent());
                pageHitRecorder.record(pageId, country, referer, isBot);
            });

            // demoConfig.enabled() vs demoConfig.dev():
            //   - enabled(): public demo mode - the instance is reset on an idle timer and
            //     handed to anonymous visitors. handleDemoGuard runs to block destructive
            //     and externally-effecting endpoints (account deletion, external probes,
            //     real-mail tests, file uploads, AI calls, …). See DEMO_BLOCKED_PATHS.
            //   - dev(): local-development flag. Enables /api/v1/dev/errors, relaxes secure-
            //     cookie requirements over plain HTTP, and skips the eager admin bootstrap.
            //     NO endpoints are blocked in dev mode - the demo guard is intentionally
            //     not attached so transfer, uploads, probes and everything else are usable.
            if (demoConfig.enabled()) {
                config.routes.before(this::handleDemoGuard);
            }

            config.routes.beforeMatched(this::handleAccess);
            config.routes.beforeMatched(this::handleStationReadOnly);

            setupExceptionHandlers(config.routes);

            // Public endpoints
            config.routes.get(
                    API_PREFIX + "/public/config",
                    ctx -> ctx.json(new PublicConfigResponse(
                            apiConfig.demoUrl() != null ? apiConfig.demoUrl() : "",
                            demoConfig.enabled() || demoConfig.dev(),
                            loadAppVersion())));

            // Public demo endpoints
            config.routes.get(
                    API_PREFIX + "/demo/status",
                    ctx -> ctx.json(new DemoStatusResponse(demoConfig.enabled(), demoConfig.dev())));

            if (demoConfig.enabled() || demoConfig.dev()) {
                config.routes.get(API_PREFIX + "/demo/accounts", this::handleDemoAccounts);
            }

            if (demoConfig.dev()) {
                config.routes.post(API_PREFIX + "/dev/errors", this::handleDevErrorReport);
                config.routes.post(API_PREFIX + "/dev/reset", this::handleDevReset);
            }

            for (Routes route : routes) {
                route.register(config.routes, API_PREFIX);
            }
        });
        app.start(apiConfig.host(), apiConfig.port());
        log.info("API server started on {}:{}", apiConfig.host(), apiConfig.port());
    }

    /**
     * Before-handler that blocks destructive or externally-effecting operations in demo mode.
     *
     * <p>Three layers of protection are layered here:
     * <ol>
     *   <li>Exact-match path blocks via {@link #DEMO_BLOCKED_PATHS} - password changes, account
     *       deletion, GDPR export, mail-relay test (sends real SMTP), station-deletion request,
     *       station data import, public station-application submission, page-editor and
     *       knowledge-base file uploads.</li>
     *   <li>Method + prefix blocks for the admin-station create/delete and role-change PUTs.</li>
     *   <li>Method + regex blocks for parameterised paths - public invite acceptance (avoids a
     *       leaked token turning the demo into a real account), public waitlist registration
     *       (spam), parameterised page/knowledge-base file uploads (disk pressure), and the
     *       WebAuthn enrolment routes (would lock a demo session out behind a key that cannot
     *       be reproduced after the demo resets).</li>
     * </ol>
     */
    private void handleDemoGuard(@NotNull Context ctx) {
        String path = ctx.path();
        var method = ctx.method();

        if (DEMO_BLOCKED_PATHS.contains(path)) {
            throw new BadRequestResponse("This action is disabled in demo mode");
        }

        if (path.startsWith("/api/v1/admin/stations") && (method == HandlerType.POST || method == HandlerType.DELETE)) {
            throw new BadRequestResponse("Station management is disabled in demo mode");
        }

        if (method == HandlerType.PUT
                && (path.matches("/api/v1/station-members/\\d+/roles") || path.matches("/api/v1/groups/\\d+/roles"))) {
            throw new BadRequestResponse("Role changes are disabled in demo mode");
        }

        if (path.startsWith("/api/v1/account/2fa/webauthn/register/")) {
            throw new BadRequestResponse("Security-key setup is disabled in demo mode");
        }

        if (method == HandlerType.POST && path.matches("/api/v1/public/station-invite/[^/]+/accept")) {
            throw new BadRequestResponse("Accepting invites is disabled in demo mode");
        }

        if (method == HandlerType.POST && path.matches("/api/v1/public/station/[^/]+/waitlists/[^/]+/register")) {
            throw new BadRequestResponse("Public waiting-list registration is disabled in demo mode");
        }

        if (method == HandlerType.POST && path.matches("/api/v1/pages/\\d+/files")) {
            throw new BadRequestResponse("File uploads are disabled in demo mode");
        }

        if (method == HandlerType.POST
                && (path.matches("/api/v1/kb/folders/\\d+/icon") || path.matches("/api/v1/kb/files/\\d+/images"))) {
            throw new BadRequestResponse("File uploads are disabled in demo mode");
        }

        if (method == HandlerType.POST && path.matches("/api/v1/admin/discovery/peers/probe")) {
            throw new BadRequestResponse("External probes are disabled in demo mode");
        }

        if (method == HandlerType.POST && path.matches("/api/v1/lending/requests")) {
            throw new BadRequestResponse("Cross-station lending is disabled in demo mode");
        }

        if (method == HandlerType.POST && path.matches("/api/v1/ai/providers/[^/]+/models")) {
            throw new BadRequestResponse("External AI calls are disabled in demo mode");
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

    /**
     * Throws the data away and seeds it again, so a test run starts from the state the seeder
     * describes rather than from whatever the run before it left behind.
     *
     * <p>Registered only while the backend runs with {@code demo.dev}, alongside the password-free
     * login that serves the same purpose. It is destructive by design and has no place anywhere a
     * real station's data lives.
     */
    private void handleDevReset(@NotNull Context ctx) {
        log.info("Dev reset requested, discarding all data and seeding again");
        demoService.resetAndSeed();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void handleDemoAccounts(@NotNull Context ctx) {
        var allStations = stationRepository.findAll();
        var stationGroups = new ArrayList<DemoStationGroup>();
        for (var station : allStations) {
            var members = stationMemberRepository.findByStation(station.id());
            var accounts = new ArrayList<DemoAccount>();
            for (StationMember member : members) {
                if (member.accountId() == null) continue;
                accountRepository.findById(member.accountId()).ifPresent(account -> {
                    var permissions = stationMemberRepository.findPermissions(member.id());
                    var permissionNames =
                            permissions.stream().map(p -> p.permission().name()).toList();
                    var groupNames = memberGroupRepository.findGroupsForMember(member.id()).stream()
                            .map(MemberGroup::name)
                            .toList();
                    var tagNames = userTagRepository.findTagsForMember(member.id()).stream()
                            .map(UserTag::name)
                            .toList();
                    boolean complete =
                            profileFieldService.isProfileComplete(member.id(), station.id(), permissionNames);
                    accounts.add(new DemoAccount(
                            account.email(),
                            account.firstName(),
                            account.lastName(),
                            member.userType(),
                            permissionNames,
                            groupNames,
                            tagNames,
                            complete,
                            account.instanceUserType() == InstanceUserType.ADMINISTRATOR));
                });
            }
            if (!accounts.isEmpty()) {
                stationGroups.add(new DemoStationGroup(station.uid().toString(), station.name(), accounts));
            }
        }

        var noStationAccounts = new ArrayList<DemoAccount>();
        for (var account : accountRepository.findAll()) {
            if (!stationMemberRepository.findAllByAccountId(account.id()).isEmpty()) {
                continue;
            }
            boolean administrator = account.instanceUserType() == InstanceUserType.ADMINISTRATOR;
            StationUserType bucket = administrator ? StationUserType.MANAGER : StationUserType.MEMBER;
            noStationAccounts.add(new DemoAccount(
                    account.email(),
                    account.firstName(),
                    account.lastName(),
                    bucket,
                    List.of(),
                    List.of(),
                    List.of(),
                    true,
                    administrator));
        }

        ctx.json(new DemoAccountsResponse(noStationAccounts, stationGroups));
    }

    /**
     * Before-matched handler that enforces authentication and role-based authorization.
     * Resolves the session from the Authorization header, stores it as a context attribute,
     * and checks that the user has at least one of the required route roles.
     */
    private void handleAccess(@NotNull Context ctx) {
        Set<RouteRole> routeRoles = ctx.routeRoles();

        // Routes with no roles defined are public - still populate session if token or federation headers present
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

        String authHeader = ctx.header("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        if (token == null || token.isBlank()) {
            throw new UnauthorizedResponse("Missing or invalid Authorization header");
        }

        Station station = null;
        String stationIdHeader = ctx.header("X-Station-Id");
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
        if (routeRoles.size() == 1 && routeRoles.contains(StationPermission.LOGIN)) {
            return;
        }

        // Check if user has any of the required permissions (permissions are already expanded).
        // Routes can declare a StepUpCategory alongside permissions; permission roles still gate access,
        // and a fresh 2FA verification is additionally required when any StepUpCategory is present.
        boolean permissionRequired = false;
        boolean permissionGranted = false;
        StepUpCategory stepUpCategory = null;
        for (RouteRole required : routeRoles) {
            if (required instanceof StepUpCategory sc) {
                stepUpCategory = sc;
                continue;
            }
            permissionRequired = true;
            if (required instanceof StationPermission sp && session.hasPermission(sp)) {
                permissionGranted = true;
            } else if (required instanceof InstancePermission ip && session.hasInstancePermission(ip)) {
                permissionGranted = true;
            }
        }

        if (permissionRequired && !permissionGranted) {
            ctx.header("X-Required-Permissions", routeRoles.toString());
            ctx.header("X-User-Permissions", session.permissions().toString());
            throw new ForbiddenResponse(
                    "Insufficient permissions. Required: " + routeRoles + ", Current: " + session.permissions());
        }

        if (stepUpCategory != null && !isStepUpFresh(session)) {
            throw new StepUpRequiredException(stepUpCategory);
        }
    }

    /**
     * Rejects every state-changing request that targets a station which has been flagged
     * read-only for an in-flight cross-instance transfer. Catches both per-session station
     * routes ({@code /api/v1/station/*}) and admin routes that name a specific station via
     * {@code {stationUid}} in the path. GET / HEAD / OPTIONS pass through unchanged. The
     * {@code /station/transfer/abort} and {@code /station/transfer/status} endpoints are
     * exempt so the operator can still cancel the transfer and the banner can poll.
     */
    private void handleStationReadOnly(@NotNull Context ctx) {
        var method = ctx.method();
        if (method == HandlerType.GET || method == HandlerType.HEAD || method == HandlerType.OPTIONS) {
            return;
        }
        String path = ctx.path();

        if (path.startsWith(API_PREFIX + "/station/")) {
            if (path.equals(API_PREFIX + "/station/transfer/abort")) return;
            if (path.equals(API_PREFIX + "/station/transfer/status")) return;
            UserSession session = ctx.attribute(ATTR_SESSION);
            if (session == null || session.stationId() == null) return;
            int stationId = session.stationId();
            if (stationRepository.isReadOnlyForTransfer(stationId)) {
                throw new StationReadOnlyForTransferException(stationId);
            }
            return;
        }

        if (path.startsWith(API_PREFIX + "/admin/storage/recalculate/")
                || (path.startsWith(API_PREFIX + "/admin/storage/stations/") && path.contains("/quotas"))) {
            String stationUidParam = ctx.pathParam("stationUid");
            if (stationUidParam == null || stationUidParam.isBlank()) return;
            UUID uid;
            try {
                uid = UUID.fromString(stationUidParam);
            } catch (IllegalArgumentException e) {
                return;
            }
            Optional<Station> stationOpt = stationRepository.findByUid(uid);
            if (stationOpt.isEmpty()) return;
            int stationId = stationOpt.get().id();
            if (stationRepository.isReadOnlyForTransfer(stationId)) {
                throw new StationReadOnlyForTransferException(stationId);
            }
        }
    }

    /**
     * Returns true when step-up enforcement is satisfied for the session: either the user has no
     * 2FA enrolled (in which case there is nothing to step up against), or the session's last 2FA
     * verification is within the configured freshness window.
     */
    private boolean isStepUpFresh(UserSession session) {
        Instant verifiedAt = session.twoFactorVerifiedAt();
        if (verifiedAt != null) {
            Duration freshness = Duration.ofSeconds(authConfig.twoFactor().stepUpFreshnessSeconds());
            if (verifiedAt.isAfter(Instant.now().minus(freshness))) return true;
        }
        return !twoFactorService.isEnrolled(session.accountId());
    }

    /**
     * Creates the Jackson 3 JSON mapper configured with ISO date formatting.
     */
    private Jackson3Mapper jacksonMapper() {
        // FAIL_ON_UNKNOWN_PROPERTIES is Jackson's default but pinned explicitly here so
        // an inbound payload with extra fields is rejected with 400 rather than silently
        // dropped. A future contributor copying a mapper from another site (e.g. the
        // federation HTTP client, which intentionally tolerates unknown fields for
        // cross-version compatibility) will not accidentally regress this.
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new StationIdModule(stationRepository))
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX"))
                .build();
        return new Jackson3Mapper(mapper);
    }

    private void configureOpenApi(OpenApiPluginConfiguration config) {
        config.withDocumentationPath("/docs")
                .withDefinitionConfiguration((_, definition) -> definition.info(info -> {
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

        routes.exception(StepUpRequiredException.class, (err, ctx) -> {
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.header("X-StepUp-Required", err.category().name());
            ctx.json(Map.of(
                    "error", "step_up_required", "category", err.category().name()));
        });

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
            log.warn("Invalid input on {} {}: {}", ctx.method(), ctx.path(), err.getMessage(), err);
            ctx.json(new ErrorResponseWrapper("Invalid Input", "Invalid input")).status(HttpStatus.BAD_REQUEST);
        });

        routes.exception(StreamReadException.class, (err, ctx) -> {
            log.warn("Malformed body on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            ctx.json(new ErrorResponseWrapper("Bad Request", "The request body is not valid JSON"))
                    .status(HttpStatus.BAD_REQUEST);
        });

        routes.exception(MismatchedInputException.class, (err, ctx) -> {
            String detail = err instanceof UnrecognizedPropertyException unknown
                    ? "The request body carries a field this endpoint does not accept: " + unknown.getPropertyName()
                    : "The request body does not match what this endpoint expects";
            log.warn("Rejected body on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            ctx.json(new ErrorResponseWrapper("Bad Request", detail)).status(HttpStatus.BAD_REQUEST);
        });

        routes.exception(Exception.class, (err, ctx) -> {
            log.error("Unhandled exception on route {} {}", ctx.method(), ctx.path(), err);
            if (devErrors) DevErrorWriter.write(err, ctx.method() + " " + ctx.path());
            ctx.json(new ErrorResponseWrapper("Internal Server Error")).status(HttpStatus.INTERNAL_SERVER_ERROR);
        });
    }

    /**
     * Installs a gzip-only compression strategy on the Javalin HTTP config. Universal gzip,
     * brotli explicitly out of scope. The default Javalin {@code excludedMimeTypes}
     * already covers the binary types we want to skip (already-compressed media), so the
     * level + threshold are the only knobs we expose.
     */
    private void configureCompression(JavalinConfig config) {
        if (!apiConfig.httpGzipEnabled()) {
            config.http.compressionStrategy = CompressionStrategy.NONE;
            return;
        }
        var strategy = new CompressionStrategy(null, new Gzip(apiConfig.httpGzipLevel()));
        strategy.setDefaultMinSizeForCompression(apiConfig.httpGzipMinSizeBytes());
        config.http.compressionStrategy = strategy;
    }

    /**
     * Coarse per-IP rate limit applied to every non-preflight API request, on top of
     * the finer auth-endpoint limits. Answers {@code 429 Too Many Requests} with a
     * {@code Retry-After} header when a client exceeds its budget. Server-to-server
     * federation traffic under {@code /remote/} is exempt - it is authenticated by
     * request signature and replay-protected already.
     *
     * <p>A dev instance is exempt as a whole. Everything on it arrives from one address - the
     * browser of whoever is working, or a test suite running several of them at once - so the
     * limit measures nothing there except how busy the developer is, and it answers
     * {@code 429} to pages that are simply loading their data.
     */
    private void enforceGlobalRateLimit(@NotNull Context ctx) {
        if (ctx.method() == HandlerType.OPTIONS) return;
        if (demoConfig.dev()) return;
        if (ctx.path().startsWith(API_PREFIX + "/remote/")) return;

        String clientIp;
        try {
            clientIp = ClientIp.resolve(ctx, network).getHostAddress();
        } catch (Exception e) {
            clientIp = ctx.ip();
        }

        boolean expensivePath = ctx.path().contains("/ai/");
        Optional<Long> retryAfter = globalRateLimiter.check(clientIp, expensivePath);
        if (retryAfter.isPresent()) {
            throw new HttpResponseException(
                    HttpStatus.TOO_MANY_REQUESTS.getCode(),
                    "Too many requests, please try again later",
                    Map.of("Retry-After", Long.toString(retryAfter.get())));
        }
    }

    /**
     * After-handler that sets Cache-Control and ETag headers based on the request path.
     *
     * <p>Ordering matters: content-hashed page files get an immutable year-long cache;
     * everything under {@code /public/} is publicly cacheable; only then are non-public
     * binary resources given a short private cache. Error responses receive no caching
     * headers, and the binary-resource match is segment-precise so an authenticated path
     * that merely contains {@code image}/{@code logo} as a substring (e.g. the logout
     * endpoint) is not mis-tagged as cacheable.
     */
    private void applyCacheHeaders(@NotNull Context ctx) {
        if (ctx.method() != HandlerType.GET) return;
        if (ctx.statusCode() >= 400) return;

        String path = ctx.path();

        if (path.startsWith(API_PREFIX + "/public/pages/") && path.contains("/files/")) {
            ctx.header("Cache-Control", "public, max-age=31536000, immutable");
            ctx.header("Vary", "Accept");
            return;
        }

        if (path.startsWith(API_PREFIX + "/public/")) {
            ctx.header("Cache-Control", "public, max-age=3600");
            addETag(ctx);
            return;
        }

        if (isBinaryResourcePath(path)) {
            ctx.header("Cache-Control", "private, max-age=300");
            return;
        }

        if (path.startsWith(API_PREFIX + "/demo/")) {
            ctx.header("Cache-Control", "public, max-age=60");
            return;
        }

        if (path.startsWith(API_PREFIX + "/")) {
            ctx.header("Cache-Control", "private, no-cache");
            addETag(ctx);
        }
    }

    /**
     * Matches the binary-resource endpoints (avatars, logos, images) by whole path
     * segment or suffix rather than substring, so unrelated paths that merely contain
     * {@code avatar}/{@code logo}/{@code image} - such as {@code /auth/logout} - are
     * excluded.
     */
    private static boolean isBinaryResourcePath(String path) {
        return path.endsWith("/avatar")
                || path.endsWith("/logo")
                || path.endsWith("/image")
                || path.endsWith("/images")
                || path.contains("/images/")
                || path.contains("/logo-fragment/");
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
            stationRepository
                    .findById(fedSession.stationId())
                    .ifPresent(station -> FederationHeaders.setStationHeaders(ctx, station));
        }
    }

    /**
     * Computes an ETag from the SHA-256 of the response body (truncated to 16
     * hex chars / 64 bits) and handles conditional 304 Not Modified responses.
     * SHA-256 is collision-resistant for the 64-bit truncation we expose, so an
     * attacker cannot craft a different body that produces the same ETag the way
     * a {@code String.hashCode()}-based tag would have allowed.
     */
    private void addETag(@NotNull Context ctx) {
        String body = ctx.result();
        if (body == null || body.isEmpty()) return;

        String etag = "\"" + bodyDigest(body) + "\"";
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
     * @param userType        the user type assigned to this member
     * @param groups          the group names the member belongs to
     * @param tags            the tag names assigned to this member
     * @param profileComplete whether the member's profile is fully filled in
     */
    /**
     * One account the demo instance offers for signing in.
     *
     * @param instanceAdministrator whether the account administers the instance itself. Station
     *                              permissions say nothing about that, so a caller looking for
     *                              someone who may reach the admin area has no other way to tell.
     */
    public record DemoAccount(
            String email,
            String firstName,
            String lastName,
            StationUserType userType,
            List<String> permissions,
            List<String> groups,
            List<String> tags,
            boolean profileComplete,
            boolean instanceAdministrator) {}

    public record PublicConfigResponse(String demoUrl, boolean demo, String version) {}

    public record DemoStatusResponse(boolean demo, boolean dev) {}

    public record DemoStationGroup(String stationId, String stationName, List<DemoAccount> accounts) {}

    public record DemoAccountsResponse(List<DemoAccount> noStationAccounts, List<DemoStationGroup> stationGroups) {}
}
