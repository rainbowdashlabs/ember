/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.push.entity.PushSubscription;
import dev.chojo.ember.feature.push.repository.PushOutboxRepository;
import dev.chojo.ember.feature.push.repository.PushSubscriptionRepository;
import dev.chojo.ember.feature.push.repository.VapidKeyRepository;
import dev.chojo.ember.feature.push.service.EcP256;
import dev.chojo.ember.feature.push.service.PushService;
import dev.chojo.ember.feature.push.service.VapidKeys;
import dev.chojo.ember.feature.signing.service.SigningKeyWrap;
import dev.chojo.ember.feature.system.service.InstanceSettingsService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A browser is registered for the signed-in account and its sign-in only, after its address and
 * keys were checked, and only its owner lists, tests or forgets it.
 */
class PushRoutesTest extends RepositoryTestBase {
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    private static final String PUSH_SERVICE = "https://fcm.googleapis.com/fcm/send/";

    private final PushSubscriptionRepository subscriptions = new PushSubscriptionRepository();
    private final PushOutboxRepository outbox = new PushOutboxRepository();
    private final InstanceSettingsService instance = mock(InstanceSettingsService.class);
    private RouteHarness harness;
    private Account owner;
    private Account other;
    private int ownerSession;
    private int otherSession;

    @BeforeEach
    void setUp() {
        var urls = mock(RemoteUrlValidator.class);
        when(urls.isAllowed(anyString()))
                .thenAnswer(call -> call.getArgument(0, String.class).startsWith(PUSH_SERVICE));
        when(instance.pushEnabled()).thenReturn(true);
        harness = RouteHarness.serving(new PushRoutes(new PushService(
                subscriptions,
                outbox,
                new VapidKeys(
                        new VapidKeyRepository(),
                        new SigningKeyWrap(Base64.getEncoder().encodeToString(new byte[32]))),
                urls,
                instance)));
        owner = accountRepo.create("push-route-" + UUID.randomUUID() + "@test.com", "Olga", "Owner");
        other = accountRepo.create("push-route-" + UUID.randomUUID() + "@test.com", "Otto", "Other");
        ownerSession = signIn(owner.id());
        otherSession = signIn(other.id());
    }

    @AfterEach
    void cleanup() {
        accountRepo.delete(owner.id());
        accountRepo.delete(other.id());
    }

    @Test
    void aBrowserIsRegisteredForTheAskingSignInAndListedAsThisOne() {
        var registered = subscribe(
                owner, ownerSession, PUSH_SERVICE + UUID.randomUUID(), publicKey(), URL.encodeToString(new byte[16]));
        assertEquals(200, registered.code());
        var device = json(registered);
        assertTrue(device.get("current").asBoolean());
        assertEquals("Firefox auf Android", device.get("label").asString());

        var listed = json(harness.request(client ->
                client.get(RouteHarness.PREFIX + "/push/subscriptions", harness.as(as(owner, ownerSession)))));
        assertEquals(1, listed.size());
        var key = json(harness.request(
                client -> client.get(RouteHarness.PREFIX + "/push/key", harness.as(as(owner, ownerSession)))));
        assertEquals(87, key.get("publicKey").asString().length(), "65 bytes in base64url");
        assertTrue(key.get("enabled").asBoolean());
    }

    @Test
    void anAddressOrKeysThatCannotBeUsedAreRefused() {
        assertEquals(
                "PS-003",
                refusalOf(subscribe(
                                owner,
                                ownerSession,
                                "https://intranet.local/push",
                                publicKey(),
                                URL.encodeToString(new byte[16])))
                        .code());
        assertEquals(
                "PS-004",
                refusalOf(subscribe(
                                owner,
                                ownerSession,
                                PUSH_SERVICE + "x",
                                URL.encodeToString(new byte[65]),
                                URL.encodeToString(new byte[16])))
                        .code());
        assertEquals(
                "PS-004",
                refusalOf(subscribe(
                                owner, ownerSession, PUSH_SERVICE + "y", publicKey(), URL.encodeToString(new byte[8])))
                        .code());
        assertEquals(
                "PS-002",
                refusalOf(subscribe(owner, ownerSession, null, publicKey(), URL.encodeToString(new byte[16])))
                        .code());
        assertTrue(subscriptions.findByAccount(owner.id()).isEmpty());
    }

    /** One account cannot name endless addresses, nor one of absurd length, for Ember to post to. */
    @Test
    void anAccountKeepsTwentyBrowsersAndNoAbsurdAddress() {
        assertEquals(
                "PS-003",
                refusalOf(subscribe(
                                owner,
                                ownerSession,
                                PUSH_SERVICE + "x".repeat(2100),
                                publicKey(),
                                URL.encodeToString(new byte[16])))
                        .code());

        for (int i = 0; i < 22; i++) {
            assertEquals(
                    200,
                    subscribe(
                                    owner,
                                    ownerSession,
                                    PUSH_SERVICE + "device-" + i,
                                    publicKey(),
                                    URL.encodeToString(new byte[16]))
                            .code());
        }

        var kept = subscriptions.findByAccount(owner.id());
        assertEquals(20, kept.size());
        assertTrue(kept.stream()
                .noneMatch(
                        s -> s.endpoint().endsWith("device-0") || s.endpoint().endsWith("device-1")));
    }

    @Test
    void nobodyReachesAnotherAccountsDevice() {
        int id = json(subscribe(
                        owner,
                        ownerSession,
                        PUSH_SERVICE + UUID.randomUUID(),
                        publicKey(),
                        URL.encodeToString(new byte[16])))
                .get("id")
                .asInt();

        var test = harness.request(client -> client.post(
                RouteHarness.PREFIX + "/push/subscriptions/" + id + "/test",
                null,
                harness.as(as(other, otherSession))));
        var forget = harness.request(client -> client.delete(
                RouteHarness.PREFIX + "/push/subscriptions/" + id, null, harness.as(as(other, otherSession))));

        assertEquals("PS-005", refusalOf(test).code());
        assertEquals("PS-005", refusalOf(forget).code());
        assertFalse(subscriptions.findByAccount(owner.id()).isEmpty());

        var own = harness.request(client -> client.post(
                RouteHarness.PREFIX + "/push/subscriptions/" + id + "/test",
                null,
                harness.as(as(owner, ownerSession))));
        assertEquals(202, own.code());
        var gone = harness.request(client -> client.delete(
                RouteHarness.PREFIX + "/push/subscriptions/" + id, null, harness.as(as(owner, ownerSession))));
        assertEquals(204, gone.code());
    }

    /**
     * A browser removed by hand is not registered again when the page renews it on its own, only when the account
     * allows it anew, after which renewing works again.
     */
    @Test
    void aBrowserRemovedByHandIsNotRenewedOnItsOwn() {
        String endpoint = PUSH_SERVICE + UUID.randomUUID();
        String auth = URL.encodeToString(new byte[16]);
        int id = json(subscribe(owner, ownerSession, endpoint, publicKey(), auth))
                .get("id")
                .asInt();
        harness.request(client -> client.delete(
                RouteHarness.PREFIX + "/push/subscriptions/" + id, null, harness.as(as(owner, ownerSession))));

        assertEquals(
                204, renew(owner, ownerSession, endpoint, publicKey(), auth).code());
        assertTrue(subscriptions.findByAccount(owner.id()).isEmpty());

        assertEquals(
                200, subscribe(owner, ownerSession, endpoint, publicKey(), auth).code());
        assertEquals(
                200, renew(owner, ownerSession, endpoint, publicKey(), auth).code());
        assertEquals(1, subscriptions.findByAccount(owner.id()).size());
    }

    /**
     * A browser somebody else allowed does not start pushing to the next person signing in there; that takes them
     * allowing push themselves.
     */
    @Test
    void aBrowserAnotherAccountAllowedIsNotRenewedForTheNextOne() {
        String endpoint = PUSH_SERVICE + UUID.randomUUID();
        String auth = URL.encodeToString(new byte[16]);
        subscribe(owner, ownerSession, endpoint, publicKey(), auth);

        assertEquals(
                204, renew(other, otherSession, endpoint, publicKey(), auth).code());
        assertEquals(1, subscriptions.findByAccount(owner.id()).size());
        assertTrue(subscriptions.findByAccount(other.id()).isEmpty());
    }

    /**
     * A browser that moved to a new address keeps pushing when the page brings the old one along, and the old one
     * is gone afterwards; one removed by hand stays out under its new address too.
     */
    @Test
    void aBrowserThatMovedIsRenewedFromItsOldAddressUnlessItWasRemoved() {
        String auth = URL.encodeToString(new byte[16]);
        String first = PUSH_SERVICE + UUID.randomUUID();
        String moved = PUSH_SERVICE + UUID.randomUUID();
        subscribe(owner, ownerSession, first, publicKey(), auth);

        assertEquals(204, renew(owner, ownerSession, moved, publicKey(), auth).code());
        assertEquals(
                200,
                renewFrom(owner, ownerSession, moved, first, publicKey(), auth).code());
        assertEquals(
                List.of(moved),
                subscriptions.findByAccount(owner.id()).stream()
                        .map(PushSubscription::endpoint)
                        .toList());
        assertEquals(
                204,
                renewFrom(owner, ownerSession, PUSH_SERVICE + UUID.randomUUID(), first, publicKey(), auth)
                        .code());

        int id = subscriptions.findByAccount(owner.id()).getFirst().id();
        harness.request(client -> client.delete(
                RouteHarness.PREFIX + "/push/subscriptions/" + id, null, harness.as(as(owner, ownerSession))));
        assertEquals(
                204,
                renewFrom(owner, ownerSession, PUSH_SERVICE + UUID.randomUUID(), moved, publicKey(), auth)
                        .code());
    }

    @Test
    void nothingIsRegisteredWhilePushIsSwitchedOff() {
        when(instance.pushEnabled()).thenReturn(false);

        assertEquals(
                "PS-001",
                refusalOf(subscribe(
                                owner,
                                ownerSession,
                                PUSH_SERVICE + UUID.randomUUID(),
                                publicKey(),
                                URL.encodeToString(new byte[16])))
                        .code());
    }

    private Response subscribe(Account account, int session, String endpoint, String p256dh, String auth) {
        return register(account, session, endpoint, p256dh, auth, false);
    }

    private Response renew(Account account, int session, String endpoint, String p256dh, String auth) {
        return renewFrom(account, session, endpoint, null, p256dh, auth);
    }

    private Response renewFrom(
            Account account, int session, String endpoint, String previous, String p256dh, String auth) {
        String json = "{\"endpoint\":\"" + endpoint + "\",\"p256dh\":\"" + p256dh + "\",\"auth\":\"" + auth
                + "\",\"label\":\"Firefox auf Android\",\"renewal\":true"
                + (previous == null ? "" : ",\"previousEndpoint\":\"" + previous + "\"") + "}";
        return harness.request(client ->
                client.put(RouteHarness.PREFIX + "/push/subscriptions", body(json), harness.as(as(account, session))));
    }

    private Response register(
            Account account, int session, String endpoint, String p256dh, String auth, boolean renewal) {
        String json = "{" + (endpoint == null ? "" : "\"endpoint\":\"" + endpoint + "\",") + "\"p256dh\":\"" + p256dh
                + "\",\"auth\":\"" + auth + "\",\"label\":\"Firefox auf Android\",\"renewal\":" + renewal + "}";
        return harness.request(client ->
                client.put(RouteHarness.PREFIX + "/push/subscriptions", body(json), harness.as(as(account, session))));
    }

    private static String publicKey() {
        return URL.encodeToString(
                EcP256.uncompressed((ECPublicKey) EcP256.generate().getPublic()));
    }

    private static UserSession as(Account account, int session) {
        return new UserSession(
                account,
                session,
                null,
                null,
                null,
                EnumSet.of(StationPermission.USER, StationPermission.LOGIN),
                Set.of(),
                null);
    }

    private static int signIn(int accountId) {
        return query("""
                        INSERT INTO account_session (account_id, token_hash, expires_at)
                        VALUES (:account_id, :token_hash, :expires_at) RETURNING id;""")
                .single(call().bind("account_id", accountId)
                        .bind(
                                "token_hash",
                                HexFormat.of()
                                        .formatHex(UUID.randomUUID()
                                                .toString()
                                                .repeat(2)
                                                .getBytes())
                                        .substring(0, 64))
                        .bind("expires_at", Instant.now().plus(Duration.ofDays(1)), INSTANT_TIMESTAMP))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }
}
