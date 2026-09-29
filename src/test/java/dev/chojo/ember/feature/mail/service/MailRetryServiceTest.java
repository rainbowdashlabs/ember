/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.ProviderSecretRepository;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What becomes of a mail after a provider failed on it for the moment: it waits longer each time,
 * moves on along the chain, and ends as failed once nothing is left to try instead of going out
 * again for ever.
 */
class MailRetryServiceTest extends RepositoryTestBase {

    private static final EmailQueueRepository queue = new EmailQueueRepository();
    private static final StationMailProviderRepository providers = new StationMailProviderRepository();
    private static MailRetryService service;
    private static Station single;
    private static Station chained;

    @BeforeAll
    static void setup() {
        var chainService = new MailChainService(new Mailing(), providers, new ProviderSecretRepository());
        service = new MailRetryService(queue, chainService);
        single = stationRepo.create("Retry Station Single");
        chained = stationRepo.create("Retry Station Chained");
        providers.replace(single.id(), List.of(entry(2)));
        providers.replace(chained.id(), List.of(entry(1), entry(1)));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(single.id());
        stationRepo.delete(chained.id());
    }

    private static MailChainEntry entry(int attempts) {
        return new MailChainEntry(
                0,
                MailProviderType.SMTP,
                "smtp.retry.test",
                587,
                false,
                "user",
                "secret",
                "",
                "noreply@retry.test",
                "Retry",
                attempts,
                0,
                "",
                "");
    }

    private static EmailQueueRepository.QueuedEmail claim(String recipient) {
        return queue.fetchPending(50, true).stream()
                .filter(mail -> mail.recipient().equals(recipient))
                .findFirst()
                .orElseThrow();
    }

    private static boolean claimable(String recipient) {
        return queue.fetchPending(50, true).stream()
                .anyMatch(mail -> mail.recipient().equals(recipient));
    }

    private static void makeDue(String recipient) {
        query("UPDATE email_queue SET next_attempt_at = now() - INTERVAL '1 second' WHERE recipient = :recipient;")
                .single(call().bind("recipient", recipient))
                .update();
    }

    private static String statusOf(String recipient) {
        return query("SELECT status FROM email_queue WHERE recipient = :recipient;")
                .single(call().bind("recipient", recipient))
                .map(row -> row.getString("status"))
                .first()
                .orElseThrow();
    }

    @Test
    void aFailedAttemptHoldsTheMailBackBeforeTheSameProviderTriesAgain() {
        queue.enqueue("backoff@retry.test", "Backoff", "<p>body</p>", single.id());

        var step = service.afterTransientFailure(claim("backoff@retry.test"));

        assertEquals(MailRetryPolicy.Step.RETRY_SAME_PROVIDER, step);
        assertEquals("PENDING", statusOf("backoff@retry.test"));
        assertFalse(claimable("backoff@retry.test"), "the mail is not due before its delay has passed");
        makeDue("backoff@retry.test");
        var retried = claim("backoff@retry.test");
        assertEquals(1, retried.attempts(), "the failed attempt was counted");
        assertEquals(0, retried.providerPosition(), "the same provider tries again");
    }

    @Test
    void theLastProviderUsingItsAttemptsMarksTheMailFailed() {
        queue.enqueue("exhausted@retry.test", "Exhausted", "<p>body</p>", single.id());
        service.afterTransientFailure(claim("exhausted@retry.test"));
        makeDue("exhausted@retry.test");

        var step = service.afterTransientFailure(claim("exhausted@retry.test"));

        assertEquals(MailRetryPolicy.Step.GIVE_UP, step);
        assertEquals("FAILED", statusOf("exhausted@retry.test"));
        makeDue("exhausted@retry.test");
        assertFalse(claimable("exhausted@retry.test"), "a failed mail is never sent again by itself");
    }

    @Test
    void aProviderOutOfAttemptsHandsTheMailToTheNextAtOnce() {
        queue.enqueue("next@retry.test", "Next", "<p>body</p>", chained.id());

        var step = service.afterTransientFailure(claim("next@retry.test"));

        assertEquals(MailRetryPolicy.Step.NEXT_PROVIDER, step);
        var moved = claim("next@retry.test");
        assertEquals(1, moved.providerPosition(), "the second provider is in turn");
        assertEquals(0, moved.attempts(), "and starts with all of its attempts");

        assertEquals(MailRetryPolicy.Step.GIVE_UP, service.afterTransientFailure(moved));
        assertEquals("FAILED", statusOf("next@retry.test"));
    }
}
