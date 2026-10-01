/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keeps another site from acting with the session cookie a browser sends along on its own.
 *
 * <p>A change made with the cookie has to carry the {@value #HEADER} header, holding the token the
 * readable {@value SessionCookies#CSRF_COOKIE} cookie gives this site's own pages. Another site can
 * make a browser send the cookies, but it cannot read either of them, so it cannot write the header.
 * The token is derived from the session token and the server's pepper rather than stored: it needs no
 * column, and it changes whenever the session token does.
 *
 * <p>A sign-in carries no session yet, so the header cannot protect it. A sign-in whose {@code
 * Origin} names another site is refused instead, so no site can sign a browser in here as somebody
 * else. A request without an {@code Origin}, which is how a script outside a browser calls, passes.
 */
@Singleton
public class CsrfGuard {
    /** The header a change made with the session cookie has to carry. */
    public static final String HEADER = "X-CSRF-Token";

    private static final Set<HandlerType> SAFE_METHODS = Set.of(HandlerType.GET, HandlerType.HEAD, HandlerType.OPTIONS);

    private final TokenHasher tokenHasher;
    private final Api apiConfig;
    private final Demo demo;

    @Inject
    public CsrfGuard(TokenHasher tokenHasher, Api apiConfig, Demo demo) {
        this.tokenHasher = tokenHasher;
        this.apiConfig = apiConfig;
        this.demo = demo;
    }

    /**
     * The token a page has to send back with every change made under this session.
     *
     * @param sessionToken the session token
     * @return the derived token
     */
    public String tokenFor(String sessionToken) {
        return tokenHasher.hash("csrf:" + tokenHasher.hash(sessionToken));
    }

    /**
     * Whether a request under this session may go ahead: a request that changes nothing always may,
     * a change only with the matching header.
     *
     * @param ctx          the request
     * @param sessionToken the session token its cookie carries
     * @return true when the request may use the session
     */
    public boolean permits(Context ctx, String sessionToken) {
        if (SAFE_METHODS.contains(ctx.method())) return true;
        String sent = ctx.header(HEADER);
        if (sent == null) return false;
        return MessageDigest.isEqual(
                sent.getBytes(StandardCharsets.UTF_8), tokenFor(sessionToken).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Refuses a change made under this session without the matching header.
     *
     * @param ctx          the request
     * @param sessionToken the session token its cookie carries
     */
    public void require(Context ctx, String sessionToken) {
        if (!permits(ctx, sessionToken)) {
            throw MemberRefusal.REQUEST_NOT_FROM_THIS_PAGE.raise();
        }
    }

    /**
     * Refuses a sign-in that another site started.
     *
     * @param ctx the sign-in request
     */
    public void requireOwnOrigin(Context ctx) {
        String origin = ctx.header("Origin");
        if (origin == null || demo.dev()) return;
        if (!allowedOrigins().contains(normalise(origin).orElse(""))) {
            throw MemberRefusal.SIGN_IN_FROM_ANOTHER_SITE.raise();
        }
    }

    private Set<String> allowedOrigins() {
        Stream<String> configured = apiConfig.allowedOrigins().stream();
        if (demo.enabled()) configured = Stream.concat(configured, Stream.of(apiConfig.demoUrl()));
        return configured.map(CsrfGuard::normalise).flatMap(Optional::stream).collect(Collectors.toSet());
    }

    /**
     * The scheme, host and port of an address, which is all an origin is. A configured base address
     * may carry a path or a trailing slash; the {@code Origin} header never does.
     */
    private static Optional<String> normalise(String address) {
        try {
            URI uri = URI.create(address.trim());
            if (uri.getScheme() == null || uri.getHost() == null) return Optional.empty();
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            return Optional.of((uri.getScheme() + "://" + uri.getHost() + port).toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
