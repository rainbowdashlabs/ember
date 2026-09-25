/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.api.Failures;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;

import javax.net.ssl.SSLException;

/**
 * HTTP client for discovery-protocol traffic.
 *
 * <p>Two flavours: <em>unauthenticated GETs</em> for {@code /public/discovery/info} and
 * {@code /public/discovery/stations}, and <em>signed POSTs</em> for {@code /discovery/ping}
 * and {@code /discovery/peers}. Signatures use the per-instance Ed25519 key via
 * {@link DiscoverySigningService}.
 *
 * <p>Distinct from {@code FederationHttpClient} - that one ties every request to a specific
 * federation partner's RSA key pair; discovery uses a single instance-wide identity.
 *
 * <p>The embedded {@link JsonMapper} intentionally disables
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} so a peer running a newer discovery protocol
 * version can add fields to a response without breaking older peers.
 *
 * <p>Every outbound target URL is checked against {@link RemoteUrlValidator} before
 * the request is sent, so attacker-supplied peer base URLs and ping callback URLs
 * cannot be used to reach loopback, link-local, or otherwise private addresses.
 */
@Singleton
public class DiscoveryHttpClient {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryHttpClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final DiscoverySigningService signingService;
    private final RemoteUrlValidator urlValidator;
    private final JsonMapper mapper;

    @Inject
    public DiscoveryHttpClient(DiscoverySigningService signingService, RemoteUrlValidator urlValidator) {
        this.signingService = signingService;
        this.urlValidator = urlValidator;
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    private static String joinUrl(String baseUrl, String path) {
        String host = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return host + path;
    }

    /**
     * Performs an unauthenticated GET and deserializes the response. Returns {@code null} on
     * non-2xx or transport failure.
     */
    public <T> T get(String baseUrl, String path, Class<T> responseType) {
        try {
            String url = joinUrl(baseUrl, path);
            if (!urlValidator.isAllowed(url)) {
                log.warn("Discovery GET rejected by RemoteUrlValidator: {}", url);
                return null;
            }
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return mapper.readValue(response.body(), responseType);
            }
            log.debug("Discovery GET {} on {} returned HTTP {}", path, baseUrl, response.statusCode());
            return null;
        } catch (Exception e) {
            log.debug("Discovery GET {} on {} failed: {}", path, baseUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Reaches for something a peer publishes, and says what stopped it when nothing came back.
     *
     * <p>{@link #get} answers {@code null} for a name that does not resolve, a certificate that
     * will not verify, a port nothing is listening on, a host that timed out and a host that
     * answered with something else entirely. Those are five different problems with five different
     * fixes, and an operator told only that the peer "did not respond" cannot tell a mistyped
     * hostname from a firewall. This tells them which.
     *
     * <p>What comes back is written for an operator and never carries anything of Ember's: the
     * address is the one they typed, and any words taken from the failure itself pass
     * {@link Failures#readable} first.
     *
     * @param baseUrl      the peer's base URL, as the operator wrote it
     * @param path         the path to ask for
     * @param responseType what the answer is read as
     * @return what the peer said, or a sentence saying why it said nothing
     */
    public <T> Probe<T> probe(String baseUrl, String path, Class<T> responseType) {
        String url;
        try {
            url = joinUrl(baseUrl, path);
        } catch (RuntimeException e) {
            return Probe.stoppedBy("That address could not be read as a web address");
        }
        if (!urlValidator.isAllowed(url)) {
            log.warn("Discovery probe rejected by RemoteUrlValidator: {}", url);
            return Probe.stoppedBy("This instance may not reach that address. Addresses on the local network, "
                    + "on loopback and in private ranges are refused before the request is sent");
        }
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Probe.stoppedBy("The address answered with HTTP " + response.statusCode()
                        + ". Something is running there, but it is not offering what a peer offers, "
                        + "so it may not be an Ember instance");
            }
            return Probe.reachedWith(mapper.readValue(response.body(), responseType));
        } catch (JacksonException e) {
            log.debug("Discovery probe {} on {} answered with something unreadable", path, baseUrl, e);
            return Probe.stoppedBy("The address answered, but not with anything a peer would send. "
                    + "It is probably not an Ember instance");
        } catch (Exception e) {
            log.debug("Discovery probe {} on {} failed", path, baseUrl, e);
            return Probe.stoppedBy(whyItFailed(e));
        }
    }

    /**
     * Turns a transport failure into the sentence that names the fix.
     */
    private static String whyItFailed(Exception failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof UnknownHostException unknown) {
                return "The name " + unknown.getMessage() + " does not resolve. Check the spelling of the address";
            }
            if (cause instanceof SSLException) {
                return "The secure connection could not be set up. The certificate may be self-signed, "
                        + "out of date, or issued for a different name";
            }
            if (cause instanceof HttpTimeoutException) {
                return "The address did not answer within " + REQUEST_TIMEOUT.toSeconds()
                        + " seconds. The instance may be down, or a firewall may be dropping the connection";
            }
            if (cause instanceof ConnectException) {
                return "The connection was refused. The address resolves, but nothing is listening on that port";
            }
        }
        return Failures.readable(failure.getMessage())
                .map(said -> "The address could not be reached: " + said)
                .orElse("The address could not be reached, and what went wrong is only in the log");
    }

    /**
     * What came of reaching for something a peer publishes.
     *
     * @param value   what the peer sent, or {@code null} where nothing usable came back
     * @param problem why nothing came back, in a sentence an operator can act on, or {@code null}
     *         where something did
     */
    public record Probe<T>(T value, String problem) {
        static <T> Probe<T> reachedWith(T value) {
            return new Probe<>(value, null);
        }

        static <T> Probe<T> stoppedBy(String problem) {
            return new Probe<>(null, problem);
        }

        /**
         * Whether the peer answered with what was asked for.
         *
         * @return true when {@link #value} is there to be used
         */
        public boolean reached() {
            return value != null;
        }
    }

    /**
     * Sends a signed POST. Returns true on 2xx, false otherwise.
     *
     * @param baseUrl peer base URL (without {@code /api/v1})
     * @param path    API path including the {@code /api/v1} prefix
     * @param body    Java object to serialize as the request body
     */
    public boolean signedPost(String baseUrl, String path, Object body) {
        try {
            String url = joinUrl(baseUrl, path);
            if (!urlValidator.isAllowed(url)) {
                log.warn("Discovery POST rejected by RemoteUrlValidator: {}", url);
                return false;
            }
            String json = mapper.writeValueAsString(body);
            String signature = signingService.sign(json);
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header(DiscoverySigningService.SIGNATURE_HEADER, signature)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.debug("Discovery POST {} on {} failed: {}", path, baseUrl, e.getMessage(), e);
            return false;
        }
    }

    /**
     * A signed POST to a beacon, carrying the key its signature is to be checked against.
     *
     * <p>Separate from {@link #signedPost} because the two are addressed to different sorts of
     * reader. A discovery peer has met this instance and holds its key already; a beacon is reported
     * to by instances it has never heard of, so the key travels with the delivery. Without it every
     * delivery is refused as unsigned, which is what this method exists to stop happening again.
     *
     * <p>Answers what the beacon said rather than whether it was happy, because a refusal is worth
     * naming in a log: a boolean that is false for a closed port, a wrong address and a rejected
     * signature alike is what made this invisible in the first place. The answer it gives back
     * carries the beacon's own words, since a beacon refuses with 403 for three different reasons
     * and the number alone does not tell them apart.
     *
     * @return what the beacon answered, or empty where it could not be reached
     */
    public Optional<Answer> beaconPost(String baseUrl, String path, Object body) {
        try {
            String url = joinUrl(baseUrl, path);
            if (!urlValidator.isAllowed(url)) {
                log.warn("Beacon POST rejected by RemoteUrlValidator: {}", url);
                return Optional.empty();
            }
            String json = mapper.writeValueAsString(body);
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header(DiscoverySigningService.SIGNATURE_HEADER, signingService.sign(json))
                    .header(DiscoverySigningService.BEACON_KEY_HEADER, signingService.publicKeyBase64())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return Optional.of(new Answer(response.statusCode(), response.body()));
        } catch (Exception e) {
            log.debug("Beacon POST {} on {} failed: {}", path, baseUrl, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * What a beacon answered a delivery with.
     *
     * @param status what it answered
     * @param body   what it said, which is how one 403 is told from another
     */
    public record Answer(int status, String body) {
        public boolean accepted() {
            return status >= 200 && status < 300;
        }
    }
}
