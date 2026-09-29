/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Settles where a mail goes after a provider failed on it for the moment, as {@link MailRetryPolicy}
 * decides: back to the same provider after a growing delay, on to the next provider of the chain,
 * or, once the chain is used up, to failed.
 *
 * <p>This is what makes a relay that has stopped working survivable: the mail does not sit in the
 * queue being refused by the same route forever, it moves on to another, and it ends where an
 * operator sees it when no route is left.
 */
@Singleton
public class MailRetryService {
    private static final Logger log = LoggerFactory.getLogger(MailRetryService.class);

    private final EmailQueueRepository queueRepository;
    private final MailChainService chainService;

    @Inject
    public MailRetryService(EmailQueueRepository queueRepository, MailChainService chainService) {
        this.queueRepository = queueRepository;
        this.chainService = chainService;
    }

    /**
     * Counts the attempt the mail just used and puts it where it goes next.
     *
     * @param email the mail as it was claimed, before the failed attempt was counted
     * @return the step taken
     */
    public MailRetryPolicy.Step afterTransientFailure(EmailQueueRepository.QueuedEmail email) {
        var chain = email.stationId() == null ? chainService.forInstance() : chainService.forStation(email.stationId());
        int allowed = chainService
                .at(chain, email.providerPosition())
                .map(MailChainEntry::attempts)
                .orElse(1);
        int attemptsUsed = email.attempts() + 1;
        queueRepository.countAttempt(email.id());
        var step = MailRetryPolicy.after(attemptsUsed, allowed, email.providerPosition(), chain.size());
        switch (step) {
            case RETRY_SAME_PROVIDER -> retrySameProvider(email, attemptsUsed, allowed);
            case NEXT_PROVIDER -> moveToNextProvider(email);
            case GIVE_UP -> giveUp(email);
        }
        return step;
    }

    private void retrySameProvider(EmailQueueRepository.QueuedEmail email, int attemptsUsed, int allowed) {
        var delay = MailRetryPolicy.delayAfter(attemptsUsed);
        queueRepository.retryAfter(email.id(), delay);
        log.warn(
                "Email {} to {} failed on provider {}; retrying in {}s, {} attempt(s) left before the next one",
                email.id(),
                email.recipient(),
                email.providerPosition(),
                delay.toSeconds(),
                allowed - attemptsUsed);
    }

    private void moveToNextProvider(EmailQueueRepository.QueuedEmail email) {
        queueRepository.advanceProvider(email.id());
        queueRepository.retryAfter(email.id(), Duration.ZERO);
        log.warn(
                "Email {} to {} moves from provider {} to {}",
                email.id(),
                email.recipient(),
                email.providerPosition(),
                email.providerPosition() + 1);
    }

    private void giveUp(EmailQueueRepository.QueuedEmail email) {
        queueRepository.markFailed(email.id());
        log.warn("Email {} to {} has exhausted every provider; marking failed", email.id(), email.recipient());
    }
}
