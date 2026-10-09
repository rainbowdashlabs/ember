/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.AccessGate;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.insights.service.BotClassifier;
import dev.chojo.ember.feature.insights.service.PageHitRecorder;
import dev.chojo.ember.feature.insights.service.RefererDomainExtractor;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.service.ApiRequestLogger;
import dev.chojo.ember.feature.traffic.service.AuthBucketClassifier;
import dev.chojo.ember.feature.traffic.service.StationResolver;
import dev.chojo.ember.feature.traffic.service.StationTrafficRecorder;
import dev.chojo.ember.util.ClientIp;
import dev.chojo.ember.util.DevErrorWriter;
import dev.chojo.ember.util.LogRedaction;
import io.javalin.Javalin;
import io.javalin.compression.CompressionStrategy;
import io.javalin.compression.Gzip;
import io.javalin.config.JavalinConfig;
import io.javalin.config.SizeUnit;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.plugin.swagger.SwaggerConfiguration;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.eclipse.jetty.server.handler.GracefulHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.javalin.http.ContentType.JSON;
import static java.util.Objects.requireNonNullElse;

/**
 * Configures and starts the Javalin HTTP server.
 * Sets up CORS, OpenAPI/Swagger, rate limits, demo mode guards and request logging, and wires the
 * {@link AccessGate}, the {@link ResponseHeaderPolicy} and the {@link ExceptionMapping} into the
 * application before registering all feature route groups.
 */
@Singleton
public class ApiServer {
    public static final String ATTR_SESSION = "session";

    /** How long requests that are already running may take to finish once the server stops. */
    public static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    private static final Logger log = LoggerFactory.getLogger(ApiServer.class);
    static final String API_PREFIX = "/api/v1";

    /**
     * Paths blocked outright in public demo mode.
     *
     * <p>The transfer token endpoints stay open on purpose: the cross-instance transfer harness needs
     * them, and the import side is gated by the administrator permission no demo account holds.
     */
    private static final Set<String> DEMO_BLOCKED_PATHS = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/set-password",
            "/api/v1/session/account",
            "/api/v1/session/gdpr-export",
            "/api/v1/station/manage/mail/test",
            "/api/v1/station/manage/mail/test-mail",
            "/api/v1/station/manage/request-delete",
            "/api/v1/station/manage/import",
            "/api/v1/station-applications",
            "/api/v1/kb/files/upload",
            "/api/v1/kb/files/import-document",
            "/api/v1/station/storage/backend/probe",
            "/api/v1/station/storage/backend/probe-config",
            "/api/v1/station/storage/backend/apply",
            "/api/v1/cluster/storage/backend/probe",
            "/api/v1/cluster/storage/backend/probe-config",
            "/api/v1/cluster/storage/backend/apply",
            "/api/v1/admin/storage/backend/probe",
            "/api/v1/admin/storage/backend/probe-config",
            "/api/v1/admin/storage/backend/apply",
            "/api/v1/ai/generate",
            "/api/v1/ai/generate-questions");

    /**
     * Paths where the write is disabled but the read is not, because the same address answers both.
     * A demo that blocked the whole address would show no avatar, no station logo and an empty media
     * library, which is not protection: it is the demo failing to demonstrate anything.
     *
     * <p>Blocking by address alone is only safe where every method on it changes something, so a
     * path belongs here rather than in {@link #DEMO_BLOCKED_PATHS} as soon as it serves a GET. Note
     * that the reverse is not automatic: the data export is a GET and stays blocked outright.
     */
    private static final Set<String> DEMO_BLOCKED_WRITE_PATHS =
            Set.of("/api/v1/session/avatar", "/api/v1/station/manage/logo", "/api/v1/media/files");

    private final Set<Routes> routes;
    private final Api apiConfig;
    private final Demo demoConfig;
    private final StationRepository stationRepository;
    private final ClusterRepository clusterRepository;
    private final ApiRequestLogger apiRequestLogger;
    private final StationTrafficRecorder trafficRecorder;
    private final StationResolver stationResolver;
    private final AuthBucketClassifier authClassifier;
    private final PageHitRecorder pageHitRecorder;
    private final RefererDomainExtractor refererExtractor;
    private final BotClassifier botClassifier;
    private final Network network;
    private final GlobalRateLimiter globalRateLimiter;
    private final AccessGate accessGate;
    private final ResponseHeaderPolicy responseHeaderPolicy;
    private final ExceptionMapping exceptionMapping;
    private final DevErrorWriter devErrorWriter;
    private volatile Javalin app;

    @Inject
    public ApiServer(
            Set<Routes> routes,
            Api apiConfig,
            Demo demoConfig,
            StationRepository stationRepository,
            ClusterRepository clusterRepository,
            ApiRequestLogger apiRequestLogger,
            StationTrafficRecorder trafficRecorder,
            StationResolver stationResolver,
            AuthBucketClassifier authClassifier,
            PageHitRecorder pageHitRecorder,
            RefererDomainExtractor refererExtractor,
            BotClassifier botClassifier,
            Network network,
            GlobalRateLimiter globalRateLimiter,
            AccessGate accessGate,
            ResponseHeaderPolicy responseHeaderPolicy,
            ExceptionMapping exceptionMapping,
            DevErrorWriter devErrorWriter) {
        this.routes = routes;
        this.apiConfig = apiConfig;
        this.demoConfig = demoConfig;
        this.stationRepository = stationRepository;
        this.clusterRepository = clusterRepository;
        this.apiRequestLogger = apiRequestLogger;
        this.trafficRecorder = trafficRecorder;
        this.stationResolver = stationResolver;
        this.authClassifier = authClassifier;
        this.pageHitRecorder = pageHitRecorder;
        this.refererExtractor = refererExtractor;
        this.botClassifier = botClassifier;
        this.network = network;
        this.globalRateLimiter = globalRateLimiter;
        this.accessGate = accessGate;
        this.responseHeaderPolicy = responseHeaderPolicy;
        this.exceptionMapping = exceptionMapping;
        this.devErrorWriter = devErrorWriter;
    }

    /**
     * Writes a request to the trace log: method, address, headers and the start of the body, with
     * secrets redacted. Nothing is assembled unless trace logging is on, because reading the body
     * and formatting every header would otherwise be paid on every request for a line nobody sees.
     */
    private static void traceRequest(Context ctx) {
        if (ctx.method() == HandlerType.OPTIONS || !log.isTraceEnabled()) return;
        String body;
        String contentType = ctx.contentType();
        if (isSensitivePath(ctx.path())) {
            body = "[REDACTED - contains sensitive data]";
        } else if (contentType == null || contentType.contains("text") || contentType.equals(JSON)) {
            body = ctx.body().substring(0, Math.min(ctx.body().length(), 180));
        } else {
            body = "Bytes";
        }
        log.trace(
                "Received request on route: {} {}\nHeaders:\n{}\nBody:\n{}",
                ctx.method() + " " + LogRedaction.redactQueryString(ctx.url()),
                LogRedaction.redactQueryString(requireNonNullElse(ctx.queryString(), "")),
                traceHeaders(ctx.headerMap()),
                body);
    }

    /**
     * Writes a response to the trace log: status, headers and the start of a JSON body, with
     * secrets redacted. Like {@link #traceRequest}, only assembled while trace logging is on.
     */
    private static void traceResponse(Context ctx) {
        if (ctx.method() == HandlerType.OPTIONS || !log.isTraceEnabled()) return;
        String body;
        if (isSensitivePath(ctx.path())) {
            body = "[REDACTED]";
        } else if (JSON.equals(ctx.res().getContentType())) {
            String result = requireNonNullElse(ctx.result(), "");
            body = result.substring(0, Math.min(result.length(), 360));
        } else {
            body = "Bytes";
        }
        var headers = new LinkedHashMap<String, String>();
        for (String name : ctx.res().getHeaderNames()) {
            headers.put(name, ctx.res().getHeader(name));
        }
        log.trace(
                "Answered request on route: {} {}\nStatus: {}\nHeaders:\n{}\nBody:\n{}",
                ctx.method() + " " + LogRedaction.redactQueryString(ctx.url()),
                LogRedaction.redactQueryString(requireNonNullElse(ctx.queryString(), "")),
                ctx.status(),
                traceHeaders(headers),
                body);
    }

    private static boolean isSensitivePath(String path) {
        return path.contains("/auth/")
                || path.contains("/ai/")
                || path.contains("/admin/config/")
                || path.contains("/signing/fields/")
                || path.contains("/signing/batch/");
    }

    private static String traceHeaders(Map<String, String> headers) {
        return LogRedaction.redactHeaders(headers).entrySet().stream()
                .map(header -> "   " + header.getKey() + ": " + header.getValue())
                .collect(Collectors.joining("\n"));
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
     * <p>A {@code 304 Not Modified} carries no body, whatever the handler produced before the tag
     * matched. Adds an approximation of response header bytes on top - same precision target as
     * ingress.
     */
    private static long estimateEgressBytes(Context ctx) {
        long bodyBytes = ctx.status() == HttpStatus.NOT_MODIFIED ? 0 : bodyBytesOf(ctx);
        long headerBytes = 0;
        for (String name : ctx.res().getHeaderNames()) {
            headerBytes += name.length();
            String value = ctx.res().getHeader(name);
            if (value != null) headerBytes += value.length();
            headerBytes += 4;
        }
        return Math.max(0, bodyBytes) + headerBytes + 12;
    }

    private static long bodyBytesOf(Context ctx) {
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
        String result = ctx.result();
        if (bodyBytes <= 0 && result != null) {
            bodyBytes = result.length();
        }
        return bodyBytes;
    }

    /**
     * Records how long an API request took and how it ended. Runs as a Javalin request logger,
     * after the response is written, so the status is the one the client received, a
     * {@code 304} included.
     */
    private void recordTiming(Context ctx, Float executionTimeMs) {
        if (!ctx.path().startsWith(API_PREFIX)) return;
        apiRequestLogger.record(
                ctx.method().name(),
                ApiRequestLogger.routeTemplate(ctx),
                ctx.statusCode(),
                Math.round(executionTimeMs));
    }

    /**
     * Counts a successful read of a public page. Only a page handler that resolved the page stores its
     * id on the context, so file serves, partner lookups and misses are never counted.
     */
    private void recordPublicPageHit(Context ctx) {
        if (ctx.method() != HandlerType.GET) return;
        if (ctx.statusCode() >= 400) return;
        Object pageIdAttr = ctx.attribute(PageHitRecorder.ATTR_PAGE_HIT_PAGE_ID);
        if (!(pageIdAttr instanceof Integer pageId)) return;
        String country = ctx.header("CF-IPCountry");
        String referer = refererExtractor.extract(ctx.header("Referer"));
        boolean isBot = botClassifier.isBot(ctx.userAgent());
        pageHitRecorder.record(pageId, country, referer, isBot);
    }

    /**
     * Adds a request to the per-station traffic counters. Runs as a Javalin request logger, after
     * the response is written, so Jetty has counted what it sent.
     */
    private void recordTraffic(Context ctx, Float executionTimeMs) {
        if (ctx.method() == HandlerType.OPTIONS) return;
        trafficRecorder.record(
                stationResolver.resolve(ctx).orElse(null),
                authClassifier.classify(ctx),
                estimateIngressBytes(ctx),
                estimateEgressBytes(ctx));
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

    /**
     * Builds the application and starts serving it on the configured host and port.
     */
    public void start() {
        if (demoConfig.dev()) {
            devErrorWriter.clearOnStartup();
        }
        app = create().start(apiConfig.host(), apiConfig.port());
        log.info("API server started on {}:{}", apiConfig.host(), apiConfig.port());
    }

    /**
     * Creates the Javalin application with all middleware, routes and plugins registered, without
     * starting it, so that the application a test talks to is the one production serves.
     *
     * <p>A development instance accepts every origin. A write from an origin the rule does not name is
     * answered 400 after the handler has already done the work, so the file is uploaded, the row is
     * written and the screen still reports a failure. A dev instance is reached from whichever address
     * happens to be in front of it that afternoon, a tunnel to a phone among them, and naming the three
     * ports it used to name meant every one of those read as a broken application.
     */
    public Javalin create() {
        return Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
            config.jetty.modifyServer(server -> {
                server.setStopTimeout(STOP_TIMEOUT.toMillis());
                server.insertHandler(new GracefulHandler());
            });
            config.jsonMapper(ApiJsonMapper.forApi(stationRepository, clusterRepository));
            configureCompression(config);
            ClientIp.installOn(config.contextResolver, network);
            config.contextResolver.scheme = ApiServer::forwardedScheme;

            config.jetty.multipartConfig.maxFileSize(apiConfig.maxUploadSizeBytes(), SizeUnit.BYTES);
            config.jetty.multipartConfig.maxInMemoryFileSize(1, SizeUnit.MB);
            config.jetty.multipartConfig.maxTotalRequestSize(apiConfig.maxRequestSizeBytes(), SizeUnit.BYTES);

            config.registerPlugin(new SwaggerPlugin(this::configureSwagger));

            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                for (String origin : apiConfig.allowedOrigins()) {
                    rule.allowHost(origin);
                }
                if (demoConfig.dev()) {
                    rule.anyHost();
                }
            }));

            config.routes.before(this::enforceGlobalRateLimit);

            config.routes.before(ApiServer::traceRequest);
            config.routes.after(ApiServer::traceResponse);

            responseHeaderPolicy.install(config.routes);

            config.requestLogger.http(this::recordTiming);
            config.requestLogger.http(this::recordTraffic);

            config.routes.after(this::recordPublicPageHit);

            if (demoConfig.enabled()) {
                config.routes.before(this::handleDemoGuard);
            }

            config.routes.beforeMatched(accessGate);
            config.routes.beforeMatched(this::handleStationReadOnly);

            exceptionMapping.install(config.routes);

            config.routes.get(ApiDocumentation.PATH, ApiDocumentation.load());

            for (Routes route : routes) {
                route.register(config.routes, API_PREFIX);
            }
        });
    }

    /**
     * Stops accepting connections and lets the requests already running finish within
     * {@link #STOP_TIMEOUT}. Does nothing when the server was never started.
     */
    public void stop() {
        var running = app;
        if (running == null) return;
        running.stop();
    }

    /**
     * Before-handler that blocks destructive or externally-effecting operations in demo mode.
     *
     * <p>Three layers of protection are layered here:
     * <ol>
     *   <li>Exact-match path blocks via {@link #DEMO_BLOCKED_PATHS} - password changes, account
     *       deletion, GDPR export, mail-relay test (sends real SMTP), station-deletion request,
     *       station data import, public station-application submission and knowledge-base file
     *       uploads.</li>
     *   <li>Exact-match path blocks for writes only via {@link #DEMO_BLOCKED_WRITE_PATHS} - the
     *       avatar, the station logo and the media library, each of which answers reads at the
     *       same address it takes uploads on.</li>
     *   <li>Method + prefix blocks for the admin-station create/delete and role-change PUTs.</li>
     *   <li>Method + regex blocks for parameterised paths - public invite acceptance (avoids a
     *       leaked token turning the demo into a real account), public waitlist registration
     *       (spam), parameterised page/knowledge-base file uploads (disk pressure), and the
     *       WebAuthn enrolment routes (would lock a demo session out behind a key that cannot
     *       be reproduced after the demo resets).</li>
     * </ol>
     *
     * <p>Attached only in public demo mode. The local development flag blocks nothing, so transfer,
     * uploads and probes stay usable there.
     */
    private void handleDemoGuard(@NotNull Context ctx) {
        String path = ctx.path();
        var method = ctx.method();

        if (DEMO_BLOCKED_PATHS.contains(path)) {
            throw SystemRefusal.DEMO_BLOCKS_ACTION.raise();
        }

        if (method != HandlerType.GET && DEMO_BLOCKED_WRITE_PATHS.contains(path)) {
            throw SystemRefusal.DEMO_BLOCKS_UPLOAD.raise();
        }

        if (path.startsWith("/api/v1/admin/stations") && (method == HandlerType.POST || method == HandlerType.DELETE)) {
            throw SystemRefusal.DEMO_BLOCKS_STATION_MANAGEMENT.raise();
        }

        if (method == HandlerType.PUT
                && (path.matches("/api/v1/station-members/\\d+/roles") || path.matches("/api/v1/groups/\\d+/roles"))) {
            throw SystemRefusal.DEMO_BLOCKS_ROLE_CHANGES.raise();
        }

        if (path.startsWith("/api/v1/account/2fa/webauthn/register/")) {
            throw SystemRefusal.DEMO_BLOCKS_SECURITY_KEY_SETUP.raise();
        }

        if (method == HandlerType.POST && path.matches("/api/v1/public/station-invite/[^/]+/accept")) {
            throw SystemRefusal.DEMO_BLOCKS_ACCEPTING_INVITES.raise();
        }

        if (method == HandlerType.POST && path.matches("/api/v1/public/station/[^/]+/waitlists/[^/]+/register")) {
            throw SystemRefusal.DEMO_BLOCKS_PUBLIC_WAITING_LIST_SIGN_UP.raise();
        }

        if (method == HandlerType.POST && path.matches("/api/v1/pages/\\d+/files")) {
            throw SystemRefusal.DEMO_BLOCKS_PAGE_UPLOADS.raise();
        }

        if (method == HandlerType.POST
                && (path.matches("/api/v1/kb/folders/\\d+/icon") || path.matches("/api/v1/kb/files/\\d+/images"))) {
            throw SystemRefusal.DEMO_BLOCKS_KB_UPLOADS.raise();
        }

        if (method == HandlerType.POST && path.matches("/api/v1/admin/discovery/peers/probe")) {
            throw SystemRefusal.DEMO_BLOCKS_PEER_PROBES.raise();
        }

        if (method == HandlerType.POST && path.matches("/api/v1/lending/requests")) {
            throw SystemRefusal.DEMO_BLOCKS_LENDING.raise();
        }

        if (method == HandlerType.POST && path.matches("/api/v1/ai/providers/[^/]+/models")) {
            throw SystemRefusal.DEMO_BLOCKS_AI_CALLS.raise();
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
            Integer stationId = session == null ? null : session.stationId();
            if (stationId == null) return;
            if (stationRepository.isReadOnlyForTransfer(stationId)) {
                throw StorageRefusal.STATION_READ_ONLY_FOR_TRANSFER.raise();
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
                throw StorageRefusal.STATION_READ_ONLY_FOR_TRANSFER_ON_ADMIN_STORAGE.raise();
            }
        }
    }

    private void configureSwagger(SwaggerConfiguration config) {
        config.withDocumentationPath(ApiDocumentation.PATH).withUiPath("/swagger-ui");
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

        RateLimits.enforce(GeneralRefusal.REQUESTS_TOO_OFTEN, globalRateLimiter.check(ctx.ip(), ctx.path()));
    }

    /**
     * The scheme the visitor used: the first entry of {@code X-Forwarded-Proto} when a proxy set
     * one, the scheme of the socket request otherwise. Installed as the context resolver so
     * {@link Context#scheme()} answers it everywhere.
     */
    private static String forwardedScheme(@NotNull Context ctx) {
        String forwarded = ctx.header("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return ctx.req().getScheme();
    }
}
