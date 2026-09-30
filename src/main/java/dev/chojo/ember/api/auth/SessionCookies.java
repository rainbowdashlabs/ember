/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.LoginResult;
import io.javalin.http.Context;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The one place that writes, reads and clears the cookies a browser's session travels in.
 *
 * <p>The session token lives in an {@code HttpOnly} cookie and nowhere else, so no script running on
 * the page can read it, copy it or send it anywhere. Beside it sits a readable cookie with the token
 * {@link CsrfGuard} wants back on every change, which also tells the page that a session exists.
 * Both are {@code SameSite=Lax}, which keeps them off requests another site starts in the background,
 * and live exactly as long as the session. {@code Secure} is left off on a dev or demo instance, which
 * is reached over plain HTTP, exactly as the remembered-device cookie does it.
 *
 * <p>Every cookie is added as a header of its own rather than set, so a response that carries a session
 * and a remembered device at once keeps all of them.
 */
@Singleton
public class SessionCookies {
    /** The cookie holding the session token. Never readable by a script. */
    public static final String SESSION_COOKIE = "ember_session";

    /** The readable cookie holding the token a page sends back as {@value CsrfGuard#HEADER}. */
    public static final String CSRF_COOKIE = "ember_csrf";

    private static final String SET_COOKIE = "Set-Cookie";

    private final Demo demo;
    private final CsrfGuard csrfGuard;

    @Inject
    public SessionCookies(Demo demo, CsrfGuard csrfGuard) {
        this.demo = demo;
        this.csrfGuard = csrfGuard;
    }

    /**
     * The session token the request carries.
     *
     * @param ctx the request
     * @return the token, or empty when the request carries no session
     */
    public static Optional<String> token(Context ctx) {
        return Optional.ofNullable(ctx.cookie(SESSION_COOKIE)).filter(token -> !token.isBlank());
    }

    /**
     * Puts a finished sign-in into the browser. A refusal, or a result that still owes a step, puts
     * nothing there, and neither does a sign-in another site started.
     *
     * @param ctx   the response to write to
     * @param login what the sign-in decided
     */
    public void issue(Context ctx, LoginResult login) {
        if (login != null && login.isSession()) {
            csrfGuard.requireOwnOrigin(ctx);
            issue(ctx, login.token(), login.expiresAt());
        }
    }

    /**
     * Writes both cookies, living exactly as long as the session.
     *
     * @param ctx       the response to write to
     * @param token     the session token
     * @param expiresAt when the session ends
     */
    public void issue(Context ctx, String token, Instant expiresAt) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
        ctx.res().addHeader(SET_COOKIE, cookie(SESSION_COOKIE, token, maxAge, true));
        ctx.res().addHeader(SET_COOKIE, cookie(CSRF_COOKIE, csrfGuard.tokenFor(token), maxAge, false));
    }

    /**
     * Removes both cookies from the browser.
     *
     * @param ctx the response to write to
     */
    public void clear(Context ctx) {
        ctx.res().addHeader(SET_COOKIE, cookie(SESSION_COOKIE, "", 0, true));
        ctx.res().addHeader(SET_COOKIE, cookie(CSRF_COOKIE, "", 0, false));
    }

    private String cookie(String name, String value, long maxAge, boolean httpOnly) {
        StringBuilder cookie = new StringBuilder()
                .append(name)
                .append('=')
                .append(value)
                .append("; Path=/; Max-Age=")
                .append(maxAge);
        if (httpOnly) cookie.append("; HttpOnly");
        cookie.append("; SameSite=Lax");
        if (secure()) cookie.append("; Secure");
        return cookie.toString();
    }

    private boolean secure() {
        return !demo.dev() && !demo.enabled();
    }
}
