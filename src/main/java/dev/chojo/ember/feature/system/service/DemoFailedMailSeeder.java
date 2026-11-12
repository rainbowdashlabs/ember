/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.mail.entity.MailFailureReason;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * One mail of the instance that the queue gave up on, so the overview, the mail log and the Monitoring
 * count have a failed mail to show and to acknowledge.
 *
 * <p>A demo instance cannot fail a mail of its own: without a provider the instance's mail waits for one
 * instead of failing. The recipient is on a domain that cannot exist and the subject says what the mail
 * is, so nobody takes it for real post. It is queued and given up on in one transaction, so no sender
 * ever sees it waiting.
 */
@Singleton
public class DemoFailedMailSeeder implements DemoSeeder {
    /** Whom the failed mail was meant for. */
    public static final String RECIPIENT = "fehlgeschlagen@demo.invalid";

    /** What the failed mail is about. */
    public static final String SUBJECT = "Beispiel einer fehlgeschlagenen E-Mail";

    private static final String BODY = "<p>Diese E-Mail ist ein Beispiel und wurde nie versendet.</p>";

    private final EmailQueueRepository queue;

    @Inject
    public DemoFailedMailSeeder(EmailQueueRepository queue) {
        this.queue = queue;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seed(DemoRunContext run) {
        Transactions.run(() -> {
            queue.enqueue(RECIPIENT, SUBJECT, BODY);
            queue.findLatestFor(RECIPIENT, SUBJECT, null)
                    .ifPresent(mail -> queue.markFailed(mail.id(), MailFailureReason.RETRIES_EXHAUSTED));
        });
    }
}
