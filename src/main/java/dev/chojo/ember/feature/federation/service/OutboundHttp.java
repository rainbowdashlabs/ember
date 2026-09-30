/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;

/**
 * The one way Ember calls another server whose address somebody else supplied: a federation
 * partner, a discovery peer or beacon, a linked page, a transfer source.
 *
 * <p><b>The address is resolved once and the connection goes to that address.</b> Checking a host
 * name and then letting the HTTP client resolve it again leaves a gap: a name can answer the check
 * with a public address and the connection with a private one (DNS rebinding), and the public
 * discovery surface lets strangers hand this instance names to call. So the name is resolved here,
 * every address it resolves to is checked by {@link RemoteUrlValidator}, and the request is rewritten
 * to the first of them as a literal IP. The JDK client never resolves anything itself.
 *
 * <p><b>TLS still verifies the name, not the address.</b> The request carries the original name in
 * its {@code Host} header, and the client that sends it is built for that one name: its TLS
 * parameters put the name into the SNI extension, and the JDK checks the server certificate against
 * the SNI name when the peer is a literal address. A certificate that does not name the host fails
 * the handshake exactly as it would without pinning. Clients are kept per host name for a while, so
 * connections are still reused.
 *
 * <p>Setting {@code Host} is normally refused by the JDK client; {@link #allowHostHeader()} enables
 * it before any client exists, and the constructor refuses to start without it rather than silently
 * sending unpinned requests. Pinned requests use HTTP/1.1, where {@code Host} is what the server
 * routes by.
 *
 * <p>Where the operator allowed private hosts ({@code federation.allowPrivateHosts}) or the instance
 * runs as a demo or development instance, nothing is checked or pinned, as before, so development
 * setups on plain HTTP and container names keep working.
 *
 * <p>Clients for fixed, operator-configured endpoints (update check, map tiles, password breach
 * check, Cloudflare ranges) need none of this and come from
 * {@link #trustedClient(Duration, HttpClient.Redirect)}, so every outbound client in Ember is still
 * built in this one place.
 */
@Singleton
public class OutboundHttp {
    private static final String RESTRICTED_HEADERS = "jdk.httpclient.allowRestrictedHeaders";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final String UNNAMED = "";

    static {
        allowHostHeader();
    }

    private final RemoteUrlValidator validator;
    private final HostResolver resolver;
    private final SSLContext sslContext;
    private final HttpClient relaxedHttps;
    private final HttpClient relaxedHttp;
    private final Cache<String, HttpClient> pinnedClients = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener(OutboundHttp::shutDown)
            .build();

    @Inject
    public OutboundHttp(RemoteUrlValidator validator) {
        this(validator, InetAddress::getAllByName, defaultSslContext());
    }

    /**
     * Builds the gateway with its own name resolution and trust, so a test can answer a name with an
     * address of its choosing and trust a certificate it made.
     *
     * @param validator  decides which addresses may be reached
     * @param resolver   resolves host names
     * @param sslContext the TLS context every client is built with
     */
    public OutboundHttp(RemoteUrlValidator validator, HostResolver resolver, SSLContext sslContext) {
        requireHostHeader();
        this.validator = validator;
        this.resolver = resolver;
        this.sslContext = sslContext;
        this.relaxedHttps = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(CONNECT_TIMEOUT)
                .sslContext(sslContext)
                .build();
        this.relaxedHttp = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .sslContext(sslContext)
                .build();
    }

    /**
     * Lets the JDK client send a {@code Host} header. The JDK reads the setting once, when its HTTP
     * client classes are first used, so this runs as early as possible: from the application's entry
     * point and from this class's initialisation.
     */
    public static void allowHostHeader() {
        String current = System.getProperty(RESTRICTED_HEADERS, "");
        boolean present = Arrays.stream(current.split(",")).map(String::strip).anyMatch("host"::equalsIgnoreCase);
        if (!present) {
            System.setProperty(RESTRICTED_HEADERS, current.isBlank() ? "host" : current + ",host");
        }
    }

    private static void requireHostHeader() {
        try {
            HttpRequest.newBuilder(URI.create("https://pinning.check/")).header("Host", "pinning.check");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "The JDK HTTP client refuses the Host header, so outbound requests cannot be pinned to the "
                            + "address they were checked against. Start the JVM with -D" + RESTRICTED_HEADERS
                            + "=host.",
                    e);
        }
    }

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No default TLS context", e);
        }
    }

    private static void shutDown(String host, HttpClient client, RemovalCause cause) {
        if (client != null) client.shutdown();
    }

    /**
     * The JSON mapper for reading what another installation sends: unknown fields and missing
     * primitives are accepted, so a peer on a newer version can add fields without breaking older
     * ones.
     *
     * @param modules extra modules the caller's payloads need
     * @return a lenient mapper
     */
    public static JsonMapper lenientMapper(JacksonModule... modules) {
        var builder = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        for (var module : modules) {
            builder.addModule(module);
        }
        return builder.build();
    }

    /**
     * Joins a base URL and a path without doubling the slash between them.
     *
     * @param baseUrl the base URL, with or without a trailing slash
     * @param path    the path, starting with a slash
     * @return the joined URL
     */
    public static String join(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + path;
    }

    /**
     * A client for an endpoint the operator configured or Ember ships with, where nobody else chooses
     * the address and nothing has to be checked or pinned.
     *
     * @param connectTimeout how long a connection may take
     * @param redirect       whether redirects are followed
     * @return the client
     */
    public static HttpClient trustedClient(Duration connectTimeout, HttpClient.Redirect redirect) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(redirect)
                .build();
    }

    /**
     * Sends a request to an address somebody else supplied, resolved and checked once and pinned for
     * the connection. Redirects are never followed; a caller that wants to follow one sends the next
     * request through here again, so every hop is checked.
     *
     * @param request the request, addressed by host name as usual
     * @param handler how the body is read
     * @param <T>     the body type
     * @return the response
     * @throws RefusedDestinationException when the destination may not be reached
     * @throws IOException                 when the exchange fails
     * @throws InterruptedException        when the calling thread is interrupted
     */
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        URI uri = request.uri();
        if (validator.permitsPrivateHosts()) {
            return relaxedClientFor(uri).send(request, handler);
        }
        var target = pin(uri);
        if (target.serverName() == null) {
            return pinnedClient(UNNAMED).send(request, handler);
        }
        var pinned = HttpRequest.newBuilder(request, (name, value) -> true)
                .uri(target.pinnedUri())
                .header("Host", target.hostHeader())
                .build();
        return pinnedClient(target.serverName()).send(pinned, handler);
    }

    private HttpClient relaxedClientFor(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) ? relaxedHttps : relaxedHttp;
    }

    /**
     * Resolves and checks the destination, and works out the URI that names its address directly.
     */
    private Target pin(URI uri) throws RefusedDestinationException {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new RefusedDestinationException(
                    RefusedDestinationException.Reason.MALFORMED,
                    "Only HTTPS destinations may be called, not " + uri.getScheme());
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new RefusedDestinationException(
                    RefusedDestinationException.Reason.MALFORMED, "The destination names no host");
        }
        if (uri.getRawUserInfo() != null) {
            throw new RefusedDestinationException(
                    RefusedDestinationException.Reason.MALFORMED, "The destination carries credentials in its address");
        }
        InetAddress address = validator.publicAddresses(host, resolver).getFirst();
        if (isLiteral(host)) {
            return new Target(null, uri, host);
        }
        String authority = uri.getPort() == -1 ? uriHost(address) : uriHost(address) + ":" + uri.getPort();
        String hostHeader =
                uri.getPort() == -1 || uri.getPort() == DEFAULT_HTTPS_PORT ? host : host + ":" + uri.getPort();
        URI pinned = URI.create("https://" + authority + (uri.getRawPath() == null ? "" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
        return new Target(host.toLowerCase(Locale.ROOT), pinned, hostHeader);
    }

    /**
     * Whether the host already is an address, which needs no resolving and has no name to send as
     * SNI. IPv6 literals come bracketed out of {@link URI#getHost()}.
     */
    private static boolean isLiteral(String host) {
        return host.startsWith("[") || host.chars().allMatch(c -> c == '.' || Character.isDigit(c));
    }

    private static String uriHost(InetAddress address) {
        return address instanceof Inet6Address ? "[" + address.getHostAddress() + "]" : address.getHostAddress();
    }

    private HttpClient pinnedClient(String serverName) {
        return pinnedClients.get(serverName, this::buildPinnedClient);
    }

    private HttpClient buildPinnedClient(String serverName) {
        var parameters = sslContext.getDefaultSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        if (!serverName.equals(UNNAMED)) {
            parameters.setServerNames(List.of(new SNIHostName(serverName)));
        }
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .sslContext(sslContext)
                .sslParameters(parameters)
                .build();
    }

    /**
     * Resolves a host name to its addresses.
     */
    @FunctionalInterface
    public interface HostResolver {
        /**
         * @param host the host name
         * @return every address it resolves to
         * @throws IOException when it does not resolve
         */
        InetAddress[] resolve(String host) throws IOException;
    }

    /**
     * Where a request really goes.
     *
     * @param serverName the host name, lower-cased, sent as SNI and keying the client; {@code null}
     *                   when the request already named an address
     * @param pinnedUri  the request URI with the checked address in place of the name
     * @param hostHeader what the {@code Host} header carries
     */
    private record Target(String serverName, URI pinnedUri, String hostHeader) {}
}
