/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.MailProviderBlockRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailChainService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.mail.service.MailRetryService;
import dev.chojo.ember.feature.mail.service.MailTemplateRenderer;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The real notices and copies of signing for tests against the database: notifications are written, and mail
 * lands in the email queue, rendered from the real templates, where a test reads it back. Nothing is sent.
 */
public final class TestNotices {
    /** The public address the links in the mails start with. */
    public static final String BASE_URL = "https://ember.example.org";

    private TestNotices() {}

    /**
     * An email service that renders the real templates and queues into the database, under {@link #BASE_URL}.
     *
     * @param queue the queue to write to
     * @return the service
     */
    public static EmailService emailService(EmailQueueRepository queue) {
        var api = mock(Api.class);
        when(api.baseUrl()).thenReturn(BASE_URL);
        return new EmailService(
                mock(Mailing.class),
                api,
                mock(Demo.class),
                queue,
                new MailTemplateRenderer(),
                mock(StationReadOnlyGuard.class),
                mock(MailChainService.class),
                mock(MailProviderBlockRepository.class),
                mock(MailRetryService.class));
    }

    /**
     * The notices of signing, writing real notifications and queueing real mail.
     *
     * @param notifier the notifier to write with
     * @param queue    the email queue
     * @param stations the stations
     * @param members  the members
     * @param accounts the accounts
     * @param documents the member documents
     * @return the notices
     */
    public static SignatureNotices notices(
            Notifier notifier,
            EmailQueueRepository queue,
            StationRepository stations,
            StationMemberRepository members,
            AccountRepository accounts,
            DocumentRepository documents) {
        return new SignatureNotices(
                notifier,
                new NotificationText(),
                emailService(queue),
                new MailRecipientService(accounts, members),
                stations,
                members,
                documents);
    }

    /**
     * The signer's copies, queueing real mail.
     *
     * @param queue    the email queue
     * @param stations the stations
     * @param members  the members
     * @param accounts the accounts
     * @return the copies
     */
    public static SignedCopies copies(
            EmailQueueRepository queue,
            StationRepository stations,
            StationMemberRepository members,
            AccountRepository accounts) {
        return new SignedCopies(
                emailService(queue), new MailRecipientService(accounts, members), stations, new NotificationText());
    }
}
