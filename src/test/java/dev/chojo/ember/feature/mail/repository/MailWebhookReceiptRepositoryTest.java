/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailWebhookReceiptRepositoryTest extends RepositoryTestBase {

    private final MailWebhookReceiptRepository repository = new MailWebhookReceiptRepository();

    @Test
    void aReportIsClaimedOnceAndRecognisedWhenItComesAgain() {
        assertTrue(repository.claim(MailProviderType.SWEEGO, "msg_once"));
        assertFalse(repository.claim(MailProviderType.SWEEGO, "msg_once"));
        assertTrue(
                repository.claim(MailProviderType.BREVO, "msg_once"),
                "the same id from another provider is another report");
    }

    @Test
    void ofCopiesArrivingAtOnceExactlyOneIsClaimed() {
        long claimed = IntStream.range(0, 8)
                .parallel()
                .filter(attempt -> repository.claim(MailProviderType.SWEEGO, "msg_racing"))
                .count();

        assertEquals(1, claimed);
    }

    @Test
    void aReleasedReportIsClaimedAgain() {
        repository.claim(MailProviderType.SWEEGO, "msg_failed");

        repository.release(MailProviderType.SWEEGO, "msg_failed");

        assertTrue(repository.claim(MailProviderType.SWEEGO, "msg_failed"));
    }

    @Test
    void onlyReceiptsPastTheirDaysArePruned() {
        repository.claim(MailProviderType.SWEEGO, "msg_old");
        repository.claim(MailProviderType.SWEEGO, "msg_fresh");
        query("""
                        UPDATE mail_webhook_receipt
                        SET received_at = now() - INTERVAL '8 days'
                        WHERE webhook_id = :webhook_id;""").single(call().bind("webhook_id", "msg_old")).update();

        assertTrue(repository.prune(7) >= 1);

        assertTrue(repository.claim(MailProviderType.SWEEGO, "msg_old"), "the old receipt is gone");
        assertFalse(repository.claim(MailProviderType.SWEEGO, "msg_fresh"), "the fresh one stays");
    }
}
