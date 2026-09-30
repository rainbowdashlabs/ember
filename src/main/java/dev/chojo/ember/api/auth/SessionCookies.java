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
 * The one place that writes, reads and clears the cookie a browser's session travels in.
 *
 * <p>The session token lives in an {@code HttpOnly} cookie and nowhere else, so no script running on
 * the page can read it, copy it or send it anywhere. {@code SameSite=Lax} keeps it off requests another
 * site starts in the background. {@code Secure} is left off on a dev or demo instance, which is reached
 * over plain HTTP, exactly as the remembered-device cookie does it.
 *
 * <p>Every cookie is added as a header of its own rather than set, so a response that carries a session
 * and a remembered device at once keeps both.
 */
@Singleton
public class SessionCookies {
    /** The cookie holding the session token. Never readable by a script. */
    public static final String SESSION_COOKIE = "ember_session";

    private static final String SET_COOKIE = "Set-Cookie";

    private final Demo demo;

    @Inject
    public SessionCookies(Demo demo) {
        this.demo = demo;
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
     * nothing there.
     *
     * @param ctx   the response to write to
     * @param login what the sign-in decided
     */
    public void issue(Context ctx, LoginResult login) {
        if (login != null && login.isSession()) {
            issue(ctx, login.token(), login.expiresAt());
        }
    }

    /**
     * Writes the session cookie, living exactly as long as the session.
     *
     * @param ctx       the response to write to
     * @param token     the session token
     * @param expiresAt when the session ends
     */
    public void issue(Context ctx, String token, Instant expiresAt) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
        ctx.res().addHeader(SET_COOKIE, cookie(SESSION_COOKIE, token, maxAge));
    }

    /**
     * Removes the session cookie from the browser.
     *
     * @param ctx the response to write to
     */
    public void clear(Context ctx) {
        ctx.res().addHeader(SET_COOKIE, cookie(SESSION_COOKIE, "", 0));
    }

    private String cookie(String name, String value, long maxAge) {
        StringBuilder cookie = new StringBuilder()
                .append(name)
                .append('=')
                .append(value)
                .append("; Path=/; Max-Age=")
                .append(maxAge)
                .append("; HttpOnly; SameSite=Lax");
        if (secure()) cookie.append("; Secure");
        return cookie.toString();
    }

    private boolean secure() {
        return !demo.dev() && !demo.enabled();
    }
}
