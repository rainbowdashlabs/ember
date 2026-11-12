/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.mail.entity.FailedMail;
import dev.chojo.ember.feature.mail.entity.FailedMailCause;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The demo's failed mail: one mail of the instance, given up on by the queue, unacknowledged and able to
 * be sent again, as the mail log lists it.
 */
class DemoFailedMailSeederTest extends RepositoryTestBase {
    private static List<FailedMail> seeded;

    @BeforeAll
    static void seedTheFailedMail() {
        new DemoFailedMailSeeder(emailQueueRepo).seed(new DemoRunContext("unused"));
        seeded = emailQueueRepo.unacknowledgedInstanceFailures(100, 0).stream()
                .filter(mail -> mail.recipient().equals(DemoFailedMailSeeder.RECIPIENT))
                .toList();
    }

    /** Exactly one mail is seeded, for the fixed recipient and subject. */
    @Test
    void oneMailIsSeeded() {
        assertEquals(1, seeded.size());
        assertEquals(DemoFailedMailSeeder.SUBJECT, seeded.getFirst().subject());
    }

    /** The queue gave up on it after its attempts, so it can be sent again. */
    @Test
    void theQueueGaveUpOnIt() {
        var mail = seeded.getFirst();
        assertEquals(FailedMailCause.RETRIES_EXHAUSTED, mail.cause());
        assertTrue(mail.requeueable());
    }
}
