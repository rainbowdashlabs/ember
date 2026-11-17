/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browsers registered for push and the queue of pushes to them: a browser belongs to whoever
 * registered it last, goes with the sign-in it was made in, and a queued push is handed out once.
 */
class PushRepositoryTest extends RepositoryTestBase {
    private final PushSubscriptionRepository subscriptions = new PushSubscriptionRepository();
    private final PushOutboxRepository outbox = new PushOutboxRepository();
    private Account anna;
    private Account ben;

    @BeforeEach
    void accounts() {
        anna = accountRepo.create("push-anna-" + UUID.randomUUID() + "@test.com", "Anna", "Push");
        ben = accountRepo.create("push-ben-" + UUID.randomUUID() + "@test.com", "Ben", "Push");
    }

    @AfterEach
    void cleanup() {
        accountRepo.delete(anna.id());
        accountRepo.delete(ben.id());
    }

    /** Creates a sign-in directly, as the sign-in flow would. */
    static int signIn(int accountId, Instant expiresAt) {
        return query("""
                        INSERT INTO account_session (account_id, token_hash, expires_at)
                        VALUES (:account_id, :token_hash, :expires_at)
                        RETURNING id;""")
                .single(call().bind("account_id", accountId)
                        .bind(
                                "token_hash",
                                HexFormat.of()
                                        .formatHex(UUID.randomUUID()
                                                .toString()
                                                .repeat(2)
                                                .getBytes())
                                        .substring(0, 64))
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    private static Instant inADay() {
        return Instant.now().plus(Duration.ofDays(1));
    }

    @Test
    void aBrowserRegisteredAgainMovesToWhoeverRegisteredItLast() {
        int annaSession = signIn(anna.id(), inADay());
        int benSession = signIn(ben.id(), inADay());
        var first = subscriptions.register(
                anna.id(), annaSession, "https://push.test/a", new byte[] {1}, new byte[] {2}, "Firefox");
        subscriptions.recordFailure(first.id());

        var moved = subscriptions.register(
                ben.id(), benSession, "https://push.test/a", new byte[] {3}, new byte[] {4}, "Firefox");

        assertEquals(first.id(), moved.id(), "one row per browser");
        assertEquals(ben.id(), moved.accountId());
        assertEquals(benSession, moved.sessionId());
        assertEquals(0, moved.failureCount(), "a fresh registration starts its count of refusals over");
        assertTrue(subscriptions.findByAccount(anna.id()).isEmpty());
    }

    @Test
    void onlyTheOwnerForgetsABrowser() {
        var subscription = subscriptions.register(
                anna.id(), signIn(anna.id(), inADay()), "https://push.test/b", new byte[] {1}, new byte[] {2}, "");

        assertFalse(subscriptions.deleteOwn(subscription.id(), ben.id()));
        assertTrue(subscriptions.findOwn(subscription.id(), ben.id()).isEmpty());
        assertTrue(subscriptions.deleteOwn(subscription.id(), anna.id()));
    }

    /** Signing out ends the sign-in, and the browser stops receiving pushes with it. */
    @Test
    void aBrowserGoesWithItsSignIn() {
        int session = signIn(anna.id(), inADay());
        subscriptions.register(anna.id(), session, "https://push.test/c", new byte[] {1}, new byte[] {2}, "");

        query("DELETE FROM account_session WHERE id = :id;")
                .single(call().bind("id", session))
                .delete();

        assertTrue(subscriptions.findByAccount(anna.id()).isEmpty());
    }

    /**
     * A due push is handed out once, and not for a browser whose sign-in ran out; the hand-out is a
     * lease, so a second sweep finds nothing until it lapses.
     */
    @Test
    void aDuePushIsHandedOutOnceAndOnlyForALiveSignIn() {
        var live = subscriptions.register(
                anna.id(), signIn(anna.id(), inADay()), "https://push.test/live", new byte[] {1}, new byte[] {2}, "");
        var stale = subscriptions.register(
                anna.id(),
                signIn(anna.id(), Instant.now().minus(Duration.ofMinutes(1))),
                "https://push.test/stale",
                new byte[] {1},
                new byte[] {2},
                "");
        outbox.enqueueTest(live.id());
        outbox.enqueueTest(stale.id());

        var claimed = mine(outbox.claimDue(500, Duration.ofMinutes(2)), live.id(), stale.id());
        assertEquals(
                List.of(live.id()),
                claimed.stream().map(PushOutboxRepository.Due::subscriptionId).toList());
        assertTrue(
                mine(outbox.claimDue(500, Duration.ofMinutes(2)), live.id(), stale.id())
                        .isEmpty(),
                "leased");

        outbox.markSent(List.of(claimed.getFirst().id()));
        assertEquals(1, outbox.sentWithinHour(live.id()));
        assertTrue(outbox.sendableAgainAt(live.id(), 20).isEmpty(), "below the limit, nothing to wait for");
        assertTrue(outbox.sendableAgainAt(live.id(), 1).isPresent(), "at the limit, the window reopens in an hour");
    }

    /**
     * Held-back pushes of one browser that are due can be claimed together, but not those another
     * sweep holds, and a summary that stood in for several counts once.
     */
    @Test
    void heldBackPushesAreClaimedTogetherAndCountOnceSent() {
        var subscription = subscriptions.register(
                anna.id(), signIn(anna.id(), inADay()), "https://push.test/held", new byte[] {1}, new byte[] {2}, "");
        for (int i = 0; i < 3; i++) outbox.enqueueTest(subscription.id());
        var due = mine(outbox.claimDue(500, Duration.ofMinutes(10)), subscription.id());
        for (var push : due) outbox.defer(push.id(), Instant.now().minus(Duration.ofSeconds(1)));

        var first = mine(outbox.claimDue(1, Duration.ofMinutes(10)), subscription.id());
        var rest = outbox.claimHeldBack(subscription.id(), Duration.ofMinutes(10));
        assertEquals(2, rest.size(), "the one already held by this sweep is not claimed twice");
        assertTrue(
                outbox.claimHeldBack(subscription.id(), Duration.ofMinutes(10)).isEmpty(), "leased");

        var all = new java.util.ArrayList<>(rest);
        all.add(first.getFirst().id());
        outbox.markSummarySent(first.getFirst().id(), all);
        assertEquals(1, outbox.sentWithinHour(subscription.id()));
    }

    /** A browser that changes hands takes none of the previous account's waiting pushes along. */
    @Test
    void aBrowserThatChangesHandsTakesNoWaitingPushesAlong() {
        var subscription = subscriptions.register(
                anna.id(), signIn(anna.id(), inADay()), "https://push.test/shared", new byte[] {1}, new byte[] {2}, "");
        outbox.enqueueTest(subscription.id());

        subscriptions.register(
                ben.id(), signIn(ben.id(), inADay()), "https://push.test/shared", new byte[] {1}, new byte[] {2}, "");

        assertTrue(mine(outbox.claimDue(500, Duration.ofMinutes(10)), subscription.id())
                .isEmpty());
    }

    /** A push left for later is not handed out before its time, and counts the try it took. */
    @Test
    void aPushLeftForLaterWaitsForItsTime() {
        var subscription = subscriptions.register(
                anna.id(), signIn(anna.id(), inADay()), "https://push.test/later", new byte[] {1}, new byte[] {2}, "");
        outbox.enqueueTest(subscription.id());
        var push = mine(outbox.claimDue(500, Duration.ofMinutes(10)), subscription.id())
                .getFirst();

        outbox.retryLater(push.id(), Instant.now().plus(Duration.ofHours(1)));
        assertTrue(mine(outbox.claimDue(500, Duration.ofMinutes(10)), subscription.id())
                .isEmpty());

        outbox.retryLater(push.id(), Instant.now().minus(Duration.ofSeconds(1)));
        var again = mine(outbox.claimDue(500, Duration.ofMinutes(10)), subscription.id());
        assertEquals(1, again.size());
        assertEquals(2, again.getFirst().attempts(), "each try left for later counts");
    }

    /** A push given up on is gone, and switching push off empties whatever still waits. */
    @Test
    void pushesGivenUpOrWaitingWhilePushIsOffAreGone() {
        var subscription = subscriptions.register(
                anna.id(), signIn(anna.id(), inADay()), "https://push.test/gone", new byte[] {1}, new byte[] {2}, "");
        outbox.enqueueTest(subscription.id());
        var push = mine(outbox.claimDue(500, Duration.ofMinutes(10)), subscription.id())
                .getFirst();
        outbox.drop(push.id());
        outbox.retryLater(push.id(), Instant.now().minus(Duration.ofSeconds(1)));
        assertTrue(mine(outbox.claimDue(500, Duration.ofMinutes(10)), subscription.id())
                .isEmpty());

        outbox.enqueueTest(subscription.id());
        outbox.dropWaiting();
        assertTrue(mine(outbox.claimDue(500, Duration.ofMinutes(10)), subscription.id())
                .isEmpty());
    }

    /** An account that switched message pushes off has none queued for its browsers. */
    @Test
    void noMessagePushIsQueuedForAnAccountThatSwitchedThemOff() {
        var subscription = subscriptions.register(
                anna.id(), signIn(anna.id(), inADay()), "https://push.test/quiet", new byte[] {1}, new byte[] {2}, "");
        query("UPDATE account SET messages_push = FALSE WHERE id = :id;")
                .single(call().bind("id", anna.id()))
                .update();

        outbox.enqueueMessage(Integer.MAX_VALUE, List.of(anna.id()));

        assertTrue(mine(outbox.claimDue(500, Duration.ofMinutes(10)), subscription.id())
                .isEmpty());
    }

    @Test
    void anAccountKeepsOnlyItsNewestBrowsers() {
        int session = signIn(anna.id(), inADay());
        for (int i = 0; i < 4; i++) {
            subscriptions.register(
                    anna.id(), session, "https://push.test/many/" + i, new byte[] {1}, new byte[] {2}, "");
        }

        subscriptions.keepNewest(anna.id(), 2);

        assertEquals(
                List.of("https://push.test/many/3", "https://push.test/many/2"),
                subscriptions.findByAccount(anna.id()).stream()
                        .map(s -> s.endpoint())
                        .toList());
    }

    private static List<PushOutboxRepository.Due> mine(List<PushOutboxRepository.Due> due, Integer... ids) {
        var wanted = List.of(ids);
        return due.stream()
                .filter(push -> wanted.contains(push.subscriptionId()))
                .toList();
    }
}
