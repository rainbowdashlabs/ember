/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.FederationHeaders;
import dev.chojo.ember.api.PublicIdModule;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.HttpStatus;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP client for cross-instance federation communication.
 * Calls remote federation endpoints and signs requests as the calling station through
 * {@link StationSigner}; callers name the station and never handle its key.
 * The remote host is determined per-partner from the {@code remote_host} field, and every request
 * goes out through {@link OutboundHttp}, which connects to the address it checked.
 * <p>
 * Every signed request binds the HTTP method, request path (with sorted query
 * string), the recipient station UUID, the timestamp and the body. A per-request
 * nonce is sent in the {@code X-Federation-Nonce} header so the receiver can
 * reject replays.
 * <p>
 * Every signed request gives the partner ten seconds to answer. A partner that accepts the
 * connection and then stalls counts as a failed call instead of holding the caller forever.
 * <p>
 * All public methods accept and return typed objects. JSON serialization/deserialization
 * is handled internally - callers never deal with raw JSON strings.
 * <p>
 * The embedded {@link JsonMapper} intentionally disables
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} so a federation peer running a newer protocol
 * version can add fields to a response without breaking older peers. The main API
 * mapper in {@link dev.chojo.ember.api.ApiJsonMapper} keeps the strict default for
 * inbound client payloads. It also carries {@link PublicIdModule#forPartnerResponses()}, which reads
 * the station ids a partner publishes as UUIDs without trying to resolve them locally.
 */
@Singleton
public class FederationHttpClient {
    private static final Logger log = LoggerFactory.getLogger(FederationHttpClient.class);
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final OutboundHttp outbound;
    private final StationSigner signer;
    private final StationRepository stationRepository;
    private final Provider<FederationContractRefreshService> refreshService;
    private final JsonMapper mapper = OutboundHttp.lenientMapper(PublicIdModule.forPartnerResponses());
    private final Duration requestTimeout;

    @Inject
    public FederationHttpClient(
            StationSigner signer,
            StationRepository stationRepository,
            OutboundHttp outbound,
            Provider<FederationContractRefreshService> refreshService) {
        this(signer, stationRepository, outbound, refreshService, REQUEST_TIMEOUT);
    }

    /**
     * Builds the client with its own limit on how long a signed request may wait for the partner's
     * answer, so a test can stand in a stalled partner without waiting out the production limit.
     */
    FederationHttpClient(
            StationSigner signer,
            StationRepository stationRepository,
            OutboundHttp outbound,
            Provider<FederationContractRefreshService> refreshService,
            Duration requestTimeout) {
        this.signer = signer;
        this.stationRepository = stationRepository;
        this.outbound = outbound;
        this.refreshService = refreshService;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Performs the handshake that establishes a partnership between two instances.
     *
     * <p>This is the one federation call that carries no request signature, because the two
     * instances have no partnership yet and therefore no key to sign an envelope with. What
     * authenticates it instead travels inside the body: the caller's public key and an enrollment
     * signature over the payload, which the receiver checks before it acts on anything.
     *
     * <p>Unlike the typed helpers around it, this one reports what went wrong rather than folding
     * every failure into {@code null}. A person is waiting on the answer, and "we could not reach
     * them" and "they say that code is used up" are different things to be told.
     *
     * @param remoteBaseUrl the base URL of the instance that issued the code
     * @param body          the handshake payload
     * @return what the far side said, or why it could not be asked
     */
    public HandshakeAttempt handshake(String remoteBaseUrl, RemoteFederationRoutes.HandshakeRequest body) {
        String url = apiUrl(remoteBaseUrl) + RemoteFederationRoutes.HANDSHAKE.path();
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HANDSHAKE_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            var response = outbound.send(request, HttpResponse.BodyHandlers.ofString());
            var status = handshakeStatus(response.statusCode());
            if (status != HandshakeStatus.ESTABLISHED) {
                log.warn("Federation handshake with {} answered HTTP {}", remoteBaseUrl, response.statusCode());
                return new HandshakeAttempt(status, null);
            }
            return new HandshakeAttempt(
                    status, mapper.readValue(response.body(), RemoteFederationRoutes.HandshakeResponse.class));
        } catch (RefusedDestinationException e) {
            log.warn("Federation handshake URL {} refused: {}", url, e.getMessage());
            return new HandshakeAttempt(HandshakeStatus.HOST_REFUSED, null);
        } catch (HttpTimeoutException e) {
            log.warn("Federation handshake with {} timed out", remoteBaseUrl, e);
            return new HandshakeAttempt(HandshakeStatus.TIMEOUT, null);
        } catch (Exception e) {
            log.warn("Federation handshake with {} failed", remoteBaseUrl, e);
            return new HandshakeAttempt(HandshakeStatus.UNREACHABLE, null);
        }
    }

    /**
     * Whether a station can send signed requests at all, which it cannot before it has a key.
     *
     * @param stationId the station that would send
     * @return true when it has a key to sign with
     */
    public boolean canSign(int stationId) {
        return signer.canSign(stationId);
    }

    private static HandshakeStatus handshakeStatus(int statusCode) {
        return switch (statusCode) {
            case 200, 201 -> HandshakeStatus.ESTABLISHED;
            case 404 -> HandshakeStatus.STATION_GONE;
            case 409 -> HandshakeStatus.CONTRACT_MISMATCH;
            case 410 -> HandshakeStatus.TOKEN_SPENT;
            case 400, 403, 422 -> HandshakeStatus.REFUSED;
            default -> HandshakeStatus.UNREACHABLE;
        };
    }

    /**
     * Performs a signed GET and deserializes the response as a single typed object.
     * Returns null on error or non-2xx status.
     */
    public <T> @Nullable T get(
            String remoteHost,
            FederationRequest request,
            UUID partnerStationUid,
            int localStationId,
            Class<T> responseType) {
        request.requireResponseType(responseType);
        try {
            var response = sendSigned("GET", remoteHost, request, null, partnerStationUid, localStationId);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return mapper.readValue(response.body(), responseType);
            }
            log.warn("Signed GET {} failed: HTTP {}", request.path(), response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed signed GET {} on {}", request.path(), remoteHost, e);
            return null;
        }
    }

    // -- Generic typed methods --

    /**
     * Performs a signed GET and deserializes the response as a list of typed objects.
     * Returns an empty list on error or non-200 status.
     */
    public <T> List<T> getList(
            String remoteHost,
            FederationRequest request,
            UUID partnerStationUid,
            int localStationId,
            Class<T> elementType) {
        request.requireResponseType(elementType);
        try {
            var response = sendSigned("GET", remoteHost, request, null, partnerStationUid, localStationId);
            if (response.statusCode() != 200) {
                log.warn("Signed GET list {} failed: HTTP {}", request.path(), response.statusCode());
                return List.of();
            }
            var type = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch list from {} {}", remoteHost, request.path(), e);
            return List.of();
        }
    }

    /**
     * Performs a signed POST with a request body and deserializes the response as a typed object.
     * The request body is serialized to JSON internally.
     * Returns null on error or non-2xx status.
     */
    public <T> @Nullable T post(
            String remoteHost,
            FederationRequest request,
            Object requestBody,
            UUID partnerStationUid,
            int localStationId,
            Class<T> responseType) {
        request.requireResponseType(responseType);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = sendSigned("POST", remoteHost, request, jsonBody, partnerStationUid, localStationId);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return mapper.readValue(response.body(), responseType);
            }
            log.warn("Signed POST {} failed: HTTP {}", request.path(), response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed signed POST {} on {}", request.path(), remoteHost, e);
            return null;
        }
    }

    /**
     * Performs a signed POST with a request body and deserializes the response as a list of typed objects.
     * The request body is serialized to JSON internally.
     * Returns an empty list on error or non-2xx status.
     */
    public <T> List<T> postList(
            String remoteHost,
            FederationRequest request,
            Object requestBody,
            UUID partnerStationUid,
            int localStationId,
            Class<T> elementType) {
        request.requireResponseType(elementType);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = sendSigned("POST", remoteHost, request, jsonBody, partnerStationUid, localStationId);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                var type = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
                return mapper.readValue(response.body(), type);
            }
            log.warn("Signed POST list {} failed: HTTP {}", request.path(), response.statusCode());
            return List.of();
        } catch (Exception e) {
            log.error("Failed signed POST list {} on {}", request.path(), remoteHost, e);
            return List.of();
        }
    }

    /**
     * Performs a signed POST with a request body, returning true on 2xx success.
     * The request body is serialized to JSON internally.
     */
    public boolean post(
            String remoteHost,
            FederationRequest request,
            Object requestBody,
            UUID partnerStationUid,
            int localStationId) {
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = sendSigned("POST", remoteHost, request, jsonBody, partnerStationUid, localStationId);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.error("Failed signed POST {} on {}", request.path(), remoteHost, e);
            return false;
        }
    }

    /**
     * Performs a signed PUT with a request body and deserializes the response as a typed object.
     * The request body is serialized to JSON internally.
     * Returns null on error or non-2xx status.
     */
    public <T> @Nullable T put(
            String remoteHost,
            FederationRequest request,
            Object requestBody,
            UUID partnerStationUid,
            int localStationId,
            Class<T> responseType) {
        request.requireResponseType(responseType);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = sendSigned("PUT", remoteHost, request, jsonBody, partnerStationUid, localStationId);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return mapper.readValue(response.body(), responseType);
            }
            log.warn("Signed PUT {} failed: HTTP {}", request.path(), response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed signed PUT {} on {}", request.path(), remoteHost, e);
            return null;
        }
    }

    /**
     * Performs a signed PUT with a request body, returning true on 2xx success.
     * The request body is serialized to JSON internally.
     */
    public boolean put(
            String remoteHost,
            FederationRequest request,
            Object requestBody,
            UUID partnerStationUid,
            int localStationId) {
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = sendSigned("PUT", remoteHost, request, jsonBody, partnerStationUid, localStationId);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.error("Failed signed PUT {} on {}", request.path(), remoteHost, e);
            return false;
        }
    }

    /**
     * Performs a signed DELETE without a request body, returning true on 2xx success.
     */
    public boolean delete(String remoteHost, FederationRequest request, UUID partnerStationUid, int localStationId) {
        try {
            var response = sendSigned("DELETE", remoteHost, request, "", partnerStationUid, localStationId);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.error("Failed signed DELETE {} on {}", request.path(), remoteHost, e);
            return false;
        }
    }

    /**
     * Performs a signed DELETE with a request body, returning true on 2xx success.
     * The request body is serialized to JSON internally.
     */
    public boolean delete(
            String remoteHost,
            FederationRequest request,
            Object requestBody,
            UUID partnerStationUid,
            int localStationId) {
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = sendSigned("DELETE", remoteHost, request, jsonBody, partnerStationUid, localStationId);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.error("Failed signed DELETE {} on {}", request.path(), remoteHost, e);
            return false;
        }
    }

    private String resolveStationName(int stationId) {
        return stationRepository.findById(stationId).map(Station::name).orElse("");
    }

    // -- Internal HTTP primitives --

    /**
     * Converts a base URL like {@code https://ember.example.com} to the API prefix.
     */
    private String apiUrl(String remoteHost) {
        return OutboundHttp.join(remoteHost, "/api/v1");
    }

    /**
     * Builds and sends a signed request with the canonical envelope, including
     * a per-request nonce. {@code body} of {@code null} means no body (e.g. GET);
     * an empty string {@code ""} is also accepted as "no body".
     */
    private HttpResponse<String> sendSigned(
            String method,
            String remoteHost,
            FederationRequest request,
            @Nullable String body,
            UUID partnerStationUid,
            int localStationId)
            throws Exception {
        String url = apiUrl(remoteHost) + request.path();
        String timestampStr = Instant.now().toString();
        var uri = URI.create(url);
        String pathWithQuery = FederationSigningService.canonicalPathWithQuery(uri);
        String signedBody = body == null ? "" : body;
        String nonce = UUID.randomUUID().toString();
        String signature = signer.signRequest(
                localStationId, method, pathWithQuery, partnerStationUid, nonce, signedBody, timestampStr);
        String stationUid = stationRepository.requireUid(localStationId)
                .toString();

        var local = FederationContractVersions.current();
        var builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(requestTimeout)
                .header(FederationHeaders.HEADER_STATION_ID, stationUid)
                .header(FederationHeaders.HEADER_STATION_NAME, resolveStationName(localStationId))
                .header("X-Federation-Target-Station-Id", partnerStationUid.toString())
                .header("X-Federation-Signature", signature)
                .header("X-Federation-Timestamp", timestampStr)
                .header("X-Federation-Nonce", nonce)
                .header(FederationHeaders.HEADER_CORE, local.core());
        var surface = request.endpoint().surface();
        if (surface != FederationSurface.CORE) {
            builder.header(FederationHeaders.HEADER_SURFACE, local.featureHash(surface.requireCapability()));
        }

        BodyPublisher publisher = body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body);
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, publisher);

        var response = outbound.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == HttpStatus.CONFLICT.getCode()) {
            handleContractMismatch(response.body(), localStationId, partnerStationUid);
        }
        return response;
    }

    /**
     * What became of a handshake attempt.
     *
     * @param status   how it ended
     * @param response what the far side answered, only present once it is {@code ESTABLISHED}
     */
    public record HandshakeAttempt(
            HandshakeStatus status, RemoteFederationRoutes.@Nullable HandshakeResponse response) {}

    /** How a handshake attempt ended, in terms a person entering a code can be told about. */
    public enum HandshakeStatus {
        /** The far side accepted, redeemed the token and answered with its own half. */
        ESTABLISHED,
        /** The address in the code is not one this instance is willing to call. */
        HOST_REFUSED,
        /** Nothing answered at that address. */
        UNREACHABLE,
        /** Something is there, but it did not answer in time. */
        TIMEOUT,
        /** The far side answered and would not accept this station. */
        REFUSED,
        /** The far side no longer has the station the code names. */
        STATION_GONE,
        /** The far side has no record of that token, or it has already been redeemed. */
        TOKEN_SPENT,
        /** The two instances run federation versions that cannot talk to each other. */
        CONTRACT_MISMATCH
    }

    /**
     * A {@code 409} carrying a contract mismatch body means the stored vector of the called
     * partner is stale - the partner redeployed since the last exchange. Kick off a
     * background ping so the vector heals without waiting for the next startup broadcast.
     */
    private void handleContractMismatch(String body, int localStationId, UUID partnerStationUid) {
        try {
            var mismatch = mapper.readValue(body, FederationContractBinder.MismatchResponse.class);
            if (!FederationContractBinder.CORE_MISMATCH.equals(mismatch.error())
                    && !FederationContractBinder.FEATURE_MISMATCH.equals(mismatch.error())) {
                return;
            }
            log.warn(
                    "Federation partner station {} rejected the request with {} (theirs {}, ours {}) - refreshing its contract vector",
                    partnerStationUid,
                    mismatch.error(),
                    mismatch.local(),
                    mismatch.remote());
            refreshService.get().refreshAsync(localStationId, partnerStationUid);
        } catch (Exception e) {
            log.debug("Could not act on a 409 from partner station {}: {}", partnerStationUid, e.getMessage());
        }
    }
}
