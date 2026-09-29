/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.api.Jackson3Mapper;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.account.entity.AccountToken;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.twofactor.route.TwoFactorRoutes;
import dev.chojo.ember.feature.twofactor.service.TrustedDeviceService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAttemptTracker;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService.VerifyBackupCodeResult;
import dev.chojo.ember.feature.twofactor.service.WebAuthnService;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Serves real requests to prove that {@code ctx.ip()} answers the resolved visitor address once
 * {@link ClientIp#installOn} has run, that a header from an untrusted hop is still ignored, and
 * that a backup-code sign-in behind a trusted proxy records the visitor's address, not the proxy's.
 */
class ClientIpContextResolverTest {

    private static final List<String> LOOPBACK_PROXY = List.of("127.0.0.0/8", "::1/128");
    private static final String VISITOR = "203.0.113.42";

    private Javalin app;

    @AfterEach
    void stop() {
        if (app != null) app.stop();
    }

    @Test
    void trustedProxyForwardsTheVisitorAddressThroughContextIp() throws Exception {
        start(LOOPBACK_PROXY, config -> config.routes.get("/ip", ctx -> ctx.result(ctx.ip())));
        assertEquals(VISITOR, send(HttpRequest.newBuilder(uri("/ip")).GET()));
    }

    @Test
    void untrustedHopKeepsItsOwnAddress() throws Exception {
        start(List.of(), config -> config.routes.get("/ip", ctx -> ctx.result(ctx.ip())));
        String seen = send(HttpRequest.newBuilder(uri("/ip")).GET());
        assertTrue(InetAddress.ofLiteral(seen).isLoopbackAddress());
    }

    @Test
    void backupCodeSignInRecordsTheForwardedVisitorAddress() throws Exception {
        int accountId = 7;
        TwoFactorService twoFactorService = mock(TwoFactorService.class);
        AccountRepository accounts = mock(AccountRepository.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        Instant now = Instant.now();
        when(accounts.findToken("pre-auth"))
                .thenReturn(Optional.of(new AccountToken(
                        1,
                        accountId,
                        "hash",
                        TokenType.TWO_FACTOR_PENDING,
                        null,
                        now.plus(Duration.ofMinutes(5)),
                        now,
                        null)));
        when(rateLimiter.tryTwoFactor(anyString(), anyInt())).thenReturn(Optional.empty());
        when(twoFactorService.verifyBackupCode(anyInt(), anyString(), anyString()))
                .thenReturn(new VerifyBackupCodeResult(false, 3));
        TwoFactorRoutes routes = new TwoFactorRoutes(
                twoFactorService,
                mock(TwoFactorAuditService.class),
                accounts,
                mock(AuthService.class),
                mock(TokenHasher.class),
                mock(WebAuthnService.class),
                mock(Demo.class),
                mock(TrustedDeviceService.class),
                rateLimiter,
                mock(TwoFactorAttemptTracker.class));

        start(LOOPBACK_PROXY, config -> {
            config.jsonMapper(new Jackson3Mapper(JsonMapper.builder().build()));
            routes.register(config.routes, "/api/v1");
        });
        send(HttpRequest.newBuilder(uri("/api/v1/auth/2fa"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"preAuthToken":"pre-auth","factor":"BACKUP_CODE","proof":"abcd-efgh"}""")));

        verify(twoFactorService).verifyBackupCode(accountId, "abcd-efgh", VISITOR);
        verify(rateLimiter).tryTwoFactor(VISITOR, accountId);
    }

    private void start(List<String> trustedProxies, Consumer<JavalinConfig> routes) {
        Network network = mock(Network.class);
        when(network.trustedProxies()).thenReturn(trustedProxies);
        when(network.cloudflare()).thenReturn(false);
        app = Javalin.create(config -> {
            ClientIp.installOn(config.contextResolver, network);
            routes.accept(config);
        });
        app.start(0);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + app.port() + path);
    }

    private static String send(HttpRequest.Builder request) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.header("X-Forwarded-For", VISITOR).build(), HttpResponse.BodyHandlers.ofString())
                    .body();
        }
    }
}
