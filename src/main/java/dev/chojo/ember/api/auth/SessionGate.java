/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.station.entity.Station;
import io.javalin.http.Context;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Turns the session cookie a request carries into the session it stands for.
 *
 * <p>A change made under the session has to prove it came from this site's own pages, see
 * {@link CsrfGuard}. A session in use is kept alive here: every admitted request records when and
 * from where it was made, and a session that has used up half of its lifetime is renewed and its
 * cookies written again. That is what replaced the browser asking for a fresh token on a timer. A
 * cookie that names no live session is cleared, so the browser stops presenting it.
 *
 * <p>Routes open to everybody take the session along when it is there and the request may use it,
 * and simply go without otherwise. That is what exempts sign-in, public forms, feeds, federation and
 * mail provider callbacks from the header without a list of them.
 */
@Singleton
public class SessionGate {
    private final AccessManager accessManager;
    private final AccountRepository accountRepository;
    private final Auth authConfig;
    private final SessionCookies sessionCookies;
    private final CsrfGuard csrfGuard;

    @Inject
    public SessionGate(
            AccessManager accessManager,
            AccountRepository accountRepository,
            Auth authConfig,
            SessionCookies sessionCookies,
            CsrfGuard csrfGuard) {
        this.accessManager = accessManager;
        this.accountRepository = accountRepository;
        this.authConfig = authConfig;
        this.sessionCookies = sessionCookies;
        this.csrfGuard = csrfGuard;
    }

    /**
     * Admits a request to a route that needs a session.
     *
     * @param ctx     the request
     * @param token   the session token its cookie carries
     * @param station the station the request named, or {@code null}
     * @param cluster the cluster the request named, or {@code null}
     * @return the session, or empty when the token names no live one
     */
    public Optional<UserSession> admit(Context ctx, String token, Station station, Cluster cluster) {
        Optional<UserSession> session = accessManager.resolveUserSession(token, station, cluster);
        if (session.isEmpty()) {
            sessionCookies.clear(ctx);
            return session;
        }
        csrfGuard.require(ctx, token);
        accountRepository.touchSession(token, ctx.userAgent(), ctx.header("CF-IPCountry"));
        accountRepository
                .renewSession(token, authConfig.sessionMinutes(false), authConfig.sessionMinutes(true))
                .ifPresent(expiresAt -> sessionCookies.issue(ctx, token, expiresAt));
        return session;
    }

    /**
     * Attaches the session a request to a public route carries, when it carries a live one and the
     * request may use it. A public route works without it, so nothing is refused here.
     *
     * @param ctx     the request
     * @param token   the session token its cookie carries
     * @param station the station the request named, or {@code null}
     */
    public void attach(Context ctx, String token, Station station) {
        if (!csrfGuard.permits(ctx, token)) return;
        accessManager.resolveUserSession(token, station).ifPresent(s -> ctx.attribute(ApiServer.ATTR_SESSION, s));
    }
}
