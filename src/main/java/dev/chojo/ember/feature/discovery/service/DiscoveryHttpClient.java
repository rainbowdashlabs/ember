/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.api.Failures;
import dev.chojo.ember.feature.discovery.entity.PictureTags;
import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.federation.service.RefusedDestinationException;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.UnknownHostException;
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
 * <p>Every request goes out through {@link OutboundHttp}: the target host is resolved once, every
 * address is checked against {@link RemoteUrlValidator}, and the connection goes to the checked
 * address, so attacker-supplied peer base URLs and ping callback URLs cannot be used to reach
 * loopback, link-local, or otherwise private addresses, not even by answering DNS differently the
 * second time.
 */
@Singleton
public class DiscoveryHttpClient {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryHttpClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final OutboundHttp outbound;
    private final DiscoverySigningService signingService;
    private final JsonMapper mapper = OutboundHttp.lenientMapper();

    @Inject
    public DiscoveryHttpClient(DiscoverySigningService signingService, OutboundHttp outbound) {
        this.signingService = signingService;
        this.outbound = outbound;
    }

    /**
     * Performs an unauthenticated GET and deserializes the response. Returns {@code null} on
     * non-2xx or transport failure.
     */
    public <T> @Nullable T get(String baseUrl, String path, Class<T> responseType) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(OutboundHttp.join(baseUrl, path)))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            var response = outbound.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return mapper.readValue(response.body(), responseType);
            }
            log.debug("Discovery GET {} on {} returned HTTP {}", path, baseUrl, response.statusCode());
            return null;
        } catch (RefusedDestinationException e) {
            log.warn("Discovery GET {} on {} refused: {}", path, baseUrl, e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("Discovery GET {} on {} failed: {}", path, baseUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches a picture a peer publishes, unsigned like every other read of what a peer publishes.
     *
     * <p>The request goes out the way {@link #get} does, so the same timeout and the same refusal of
     * private addresses hold, and a redirect is never followed: a picture comes from the address asked
     * or not at all. The body is read up to {@code maxBytes} and no further, so a peer cannot make this
     * instance hold more than that.
     *
     * @param url      the picture's address
     * @param known    what was sent with the copy kept here, asked back with so an unchanged picture is
     *                 not sent again; {@link PictureTags#NONE} where no copy is kept
     * @param maxBytes the most the picture may weigh
     * @return what came of it
     */
    public PictureFetch fetchPicture(String url, PictureTags known, int maxBytes) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET();
            String etag = known.etag();
            if (etag != null) request.header("If-None-Match", etag);
            String lastModified = known.lastModified();
            if (lastModified != null) request.header("If-Modified-Since", lastModified);
            var response = outbound.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                return pictureFrom(response, body, maxBytes);
            }
        } catch (RefusedDestinationException e) {
            log.warn("Discovery picture {} refused: {}", url, e.getMessage());
            return new PictureFetch.Failed();
        } catch (Exception e) {
            log.debug("Discovery picture {} failed: {}", url, e.getMessage());
            return new PictureFetch.Failed();
        }
    }

    private static PictureFetch pictureFrom(HttpResponse<InputStream> response, InputStream body, int maxBytes)
            throws IOException {
        int status = response.statusCode();
        if (status == HttpURLConnection.HTTP_NOT_MODIFIED) return new PictureFetch.Unchanged();
        if (status == HttpURLConnection.HTTP_NO_CONTENT
                || status == HttpURLConnection.HTTP_NOT_FOUND
                || status == HttpURLConnection.HTTP_GONE) {
            return new PictureFetch.Absent();
        }
        if (status != HttpURLConnection.HTTP_OK) return new PictureFetch.Failed();
        if (response.headers().firstValueAsLong("Content-Length").orElse(0) > maxBytes) {
            return new PictureFetch.Failed();
        }
        byte[] data = body.readNBytes(maxBytes + 1);
        if (data.length > maxBytes) return new PictureFetch.Failed();
        var headers = response.headers();
        return new PictureFetch.Fetched(
                data,
                new PictureTags(
                        headers.firstValue("ETag").orElse(null),
                        headers.firstValue("Last-Modified").orElse(null)));
    }

    /**
     * What came of fetching a picture a peer publishes.
     */
    public sealed interface PictureFetch {
        /**
         * The picture, with what the peer sent to recognise it by next time.
         *
         * @param data the bytes, no more than were allowed
         * @param tags what the peer sent to recognise this picture by
         */
        record Fetched(byte[] data, PictureTags tags) implements PictureFetch {}

        /** The peer says the copy kept here is still the picture it publishes. */
        record Unchanged() implements PictureFetch {}

        /** The peer says it publishes no picture there. */
        record Absent() implements PictureFetch {}

        /** Nothing usable came back: no answer, a refused address, an error, a redirect or too many bytes. */
        record Failed() implements PictureFetch {}
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
        URI uri;
        try {
            uri = URI.create(OutboundHttp.join(baseUrl, path));
        } catch (RuntimeException e) {
            return Probe.stoppedBy("That address could not be read as a web address");
        }
        try {
            var request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            var response = outbound.send(request, HttpResponse.BodyHandlers.ofString());
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
        } catch (RefusedDestinationException e) {
            log.warn("Discovery probe {} on {} refused: {}", path, baseUrl, e.getMessage());
            return Probe.stoppedBy(whyItWasRefused(e, uri));
        } catch (Exception e) {
            log.debug("Discovery probe {} on {} failed", path, baseUrl, e);
            return Probe.stoppedBy(whyItFailed(e));
        }
    }

    /**
     * Turns a refused destination into the sentence that names the fix.
     */
    private static String whyItWasRefused(RefusedDestinationException refusal, URI uri) {
        return switch (refusal.reason()) {
            case UNRESOLVABLE -> "The name " + uri.getHost() + " does not resolve. Check the spelling of the address";
            case MALFORMED, NOT_PUBLIC ->
                "This instance may not reach that address. Addresses on the local network, "
                        + "on loopback and in private ranges are refused before the request is sent";
        };
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
    public record Probe<T>(@Nullable T value, @Nullable String problem) {
        static <T> Probe<T> reachedWith(T value) {
            return new Probe<>(value, null);
        }

        static <T> Probe<T> stoppedBy(String problem) {
            return new Probe<>(null, problem);
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
        return post(baseUrl, path, body, true);
    }

    /**
     * Sends a POST that carries no signature. Returns true on 2xx, false otherwise.
     *
     * <p>For a body whose only promise is that it does not say who sent it. A signature would put
     * this instance's identity beside it and break that promise, however anonymous the body itself.
     *
     * @param baseUrl peer base URL (without {@code /api/v1})
     * @param path    API path including the {@code /api/v1} prefix
     * @param body    Java object to serialize as the request body
     */
    public boolean unsignedPost(String baseUrl, String path, Object body) {
        return post(baseUrl, path, body, false);
    }

    private boolean post(String baseUrl, String path, Object body, boolean signed) {
        try {
            String json = mapper.writeValueAsString(body);
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(OutboundHttp.join(baseUrl, path)))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json");
            if (signed) request.header(DiscoverySigningService.SIGNATURE_HEADER, signingService.sign(json));
            var response = outbound.send(
                    request.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (RefusedDestinationException e) {
            log.warn("Discovery POST {} on {} refused: {}", path, baseUrl, e.getMessage());
            return false;
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
            String json = mapper.writeValueAsString(body);
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(OutboundHttp.join(baseUrl, path)))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header(DiscoverySigningService.SIGNATURE_HEADER, signingService.sign(json))
                    .header(DiscoverySigningService.BEACON_KEY_HEADER, signingService.publicKeyBase64())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            var response = outbound.send(request, HttpResponse.BodyHandlers.ofString());
            return Optional.of(new Answer(response.statusCode(), response.body()));
        } catch (RefusedDestinationException e) {
            log.warn("Beacon POST {} on {} refused: {}", path, baseUrl, e.getMessage());
            return Optional.empty();
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
