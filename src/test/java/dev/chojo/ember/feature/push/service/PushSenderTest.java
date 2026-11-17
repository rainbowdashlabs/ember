/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.messages.service.MessagePushes;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.notifications.service.NotificationVisibility;
import dev.chojo.ember.feature.push.entity.PushSubscription;
import dev.chojo.ember.feature.push.repository.PushOutboxRepository;
import dev.chojo.ember.feature.push.repository.PushSubscriptionRepository;
import dev.chojo.ember.feature.push.repository.VapidKeyRepository;
import dev.chojo.ember.feature.signing.service.SigningKeyWrap;
import dev.chojo.ember.feature.system.service.InstanceSettingsService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Queued pushes go out with the headers Web Push needs, and what the push service answers decides
 * what happens next: taken, tried again later, or the browser forgotten. A browser takes at most
 * twenty an hour, and what that holds back arrives as one summary.
 */
class PushSenderTest extends RepositoryTestBase {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    private final PushSubscriptionRepository subscriptions = new PushSubscriptionRepository();
    private final PushOutboxRepository outbox = new PushOutboxRepository();
    private final InstanceSettingsService instance = mock(InstanceSettingsService.class);
    private final List<Map<String, String>> sent = new ArrayList<>();
    private int answer = 201;
    private PushSender sender;
    private Account account;
    private PushSubscription browser;

    @BeforeEach
    void setUp() {
        when(instance.pushEnabled()).thenReturn(true);
        PushTransport transport = (URI endpoint, Map<String, String> headers, byte[] body) -> {
            if (endpoint.toString().equals(browser.endpoint())) sent.add(headers);
            return new PushTransport.Answer(answer, Duration.ZERO);
        };
        sender = new PushSender(
                outbox,
                subscriptions,
                new PushMessages(
                        new NotificationText(),
                        new MailLocaleService(accountRepo, applicationSettingRepo),
                        accountRepo,
                        notificationRepo,
                        mock(NotificationVisibility.class),
                        mock(MessagePushes.class),
                        new Api()),
                new VapidKeys(new VapidKeyRepository(), new SigningKeyWrap(SECRET)),
                transport,
                instance,
                new Api());
        query("DELETE FROM push_vapid_key;").single(call()).delete();
        account = accountRepo.create("push-sender-" + UUID.randomUUID() + "@test.com", "Sina", "Sender");
        var keys = EcP256.generate();
        browser = subscriptions.register(
                account.id(),
                signIn(account.id()),
                "https://push.test/" + UUID.randomUUID(),
                EcP256.uncompressed((ECPublicKey) keys.getPublic()),
                new byte[16],
                "Firefox");
    }

    @AfterEach
    void cleanup() {
        accountRepo.delete(account.id());
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

    private void sweep() {
        sender.sweep(Instant.now());
    }

    @Test
    void aTakenPushIsSentWithTheHeadersWebPushNeeds() {
        outbox.enqueueTest(browser.id());

        sweep();

        assertEquals(1, sent.size());
        var headers = sent.getFirst();
        assertEquals("aes128gcm", headers.get("Content-Encoding"));
        assertEquals(String.valueOf(PushMessage.DEFAULT_TTL.toSeconds()), headers.get("TTL"));
        assertEquals(32, headers.get("Topic").length());
        assertTrue(headers.get("Authorization").startsWith("vapid t="), headers.get("Authorization"));
        assertEquals(1, outbox.sentWithinHour(browser.id()));
        assertTrue(
                subscriptions.findOwn(browser.id(), account.id()).orElseThrow().lastSuccessAt() != null);
    }

    @Test
    void aBrowserThePushServiceNoLongerKnowsIsForgotten() {
        answer = 410;
        outbox.enqueueTest(browser.id());

        sweep();

        assertTrue(subscriptions.findOwn(browser.id(), account.id()).isEmpty());
    }

    @Test
    void aPushTheServiceCannotTakeNowIsTriedAgainLater() {
        answer = 503;
        outbox.enqueueTest(browser.id());

        sweep();
        sweep();

        assertEquals(1, sent.size(), "not before the pause is over");
        assertEquals(0, outbox.sentWithinHour(browser.id()));
        int attempts = query("SELECT attempts FROM push_outbox WHERE subscription_id = :id;")
                .single(call().bind("id", browser.id()))
                .map(row -> row.getInt("attempts"))
                .first()
                .orElseThrow();
        assertEquals(1, attempts);
    }

    /**
     * Within one hour a browser gets twenty pushes. What comes after waits; once the oldest send
     * leaves the hour, the waiting ones go out as one summary, which counts as one send, so the
     * next burst waits again rather than slipping through.
     */
    @Test
    void theHourlyLimitHoldsAcrossBurstsWithinTheHour() {
        for (int i = 0; i < PushSender.PER_HOUR; i++) outbox.enqueueTest(browser.id());
        sweep();
        assertEquals(PushSender.PER_HOUR, sent.size());

        for (int i = 0; i < 3; i++) outbox.enqueueTest(browser.id());
        sweep();
        assertEquals(PushSender.PER_HOUR, sent.size(), "held back by the limit");

        ageOldestSends(1);
        sweep();
        assertEquals(PushSender.PER_HOUR + 1, sent.size(), "the three held back go out as one");
        assertEquals(PushSender.PER_HOUR, outbox.sentWithinHour(browser.id()), "and count once");

        for (int i = 0; i < 3; i++) outbox.enqueueTest(browser.id());
        sweep();
        assertEquals(PushSender.PER_HOUR + 1, sent.size(), "the hour is full again");
    }

    /** Moves the oldest sends out of the hour, as time passing would. */
    private void ageOldestSends(int count) {
        query("""
                        UPDATE push_outbox SET sent_at = now() - INTERVAL '2 hours'
                        WHERE id IN (SELECT id FROM push_outbox WHERE subscription_id = :id AND sent_at IS NOT NULL
                                     ORDER BY sent_at, id LIMIT :count);""").single(call().bind("id", browser.id()).bind("count", count)).update();
        query(
                        "UPDATE push_outbox SET next_attempt_at = now() - INTERVAL '1 second' WHERE subscription_id = :id AND deferred;")
                .single(call().bind("id", browser.id()))
                .update();
    }

    /** A push this side cannot build is dropped, and the browser stays registered. */
    @Test
    void aPushThatCannotBeBuiltIsDroppedAndTheBrowserKept() {
        var message = new PushMessage(
                "Titel", "Text", "https://ember.example/" + "x".repeat(5000), "topic", PushMessage.DEFAULT_TTL);

        assertThrows(IllegalArgumentException.class, message::payload);
        assertTrue(subscriptions.findOwn(browser.id(), account.id()).isPresent());
    }

    @Test
    void aLongHeadingAndBodyAreCutToFitOnePush() {
        var message = new PushMessage(
                "T".repeat(500), "Wort ".repeat(2000), "https://ember.example/a", "topic", PushMessage.DEFAULT_TTL);

        byte[] payload = message.payload();

        assertTrue(payload.length <= PushPayloadCipher.MAX_PLAINTEXT_BYTES);
        assertTrue(new String(payload, java.nio.charset.StandardCharsets.UTF_8).contains("https://ember.example/a"));
    }

    /** Switched off, nothing goes out, and what was queued is dropped rather than sent stale later. */
    @Test
    void nothingGoesOutWhilePushIsSwitchedOffAndNothingPilesUp() {
        when(instance.pushEnabled()).thenReturn(false);
        outbox.enqueueTest(browser.id());

        sweep();
        when(instance.pushEnabled()).thenReturn(true);
        sweep();

        assertTrue(sent.isEmpty());
    }

    @Test
    void thePauseGrowsAndStaysWithinAnHour() {
        assertEquals(Duration.ofMinutes(1), PushSender.pause(0, Duration.ZERO));
        assertEquals(Duration.ofMinutes(8), PushSender.pause(3, Duration.ZERO));
        assertEquals(Duration.ofMinutes(30), PushSender.pause(0, Duration.ofMinutes(30)));
        assertEquals(Duration.ofHours(1), PushSender.pause(9, Duration.ofHours(5)));
    }
}
