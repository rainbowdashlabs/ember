/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * The mail a development instance has queued, read back the way it would have been sent.
 *
 * <p>A development instance delivers nothing, so a flow whose next step is a link in a mail can only
 * be walked by reading that mail here. Nothing outside a development instance asks for it.
 */
@Singleton
public class DevMailService {
    private final EmailQueueRepository queueRepository;

    @Inject
    public DevMailService(EmailQueueRepository queueRepository) {
        this.queueRepository = queueRepository;
    }

    /**
     * The mail most recently queued for an address.
     *
     * @param recipient the address it went to
     * @return the mail, or empty when none was queued for it
     */
    public Optional<DevMail> latestFor(String recipient) {
        return queueRepository
                .findLatestFor(recipient, null, null)
                .map(mail -> new DevMail(mail.recipient(), mail.subject(), mail.body()));
    }

    /**
     * A mail the instance has queued.
     *
     * @param recipient the address it goes to
     * @param subject   its subject
     * @param body      its HTML body
     */
    public record DevMail(String recipient, String subject, String body) {}
}
