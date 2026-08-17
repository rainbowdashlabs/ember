/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.feature.mail.entity.MailDeliveryStatus;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * What a mail relay reports about a message after it has taken it.
 *
 * <p>Sending tells us only that the relay accepted the message. Whether it arrived is decided
 * afterwards, between the relay and the receiving server, and comes back as an event: a delivery,
 * a bounce, a refusal. Until this service existed, none of it reached Ember: a message the receiving
 * side refused was recorded as sent and nobody could tell otherwise.
 */
@Singleton
public class MailDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(MailDeliveryService.class);

    private final EmailQueueRepository queueRepository;
    private final MailChainService chainService;

    @Inject
    public MailDeliveryService(EmailQueueRepository queueRepository, MailChainService chainService) {
        this.queueRepository = queueRepository;
        this.chainService = chainService;
    }

    /**
     * One thing a provider has reported about one message.
     *
     * @param status        what became of the message
     * @param recipient     the address the provider names
     * @param subject       the subject the provider names, or null
     * @param correlationId the token we sent with the message and got back, or null when the
     *                      provider dropped it
     * @param messageId     the provider's own message id, or null
     * @param detail        the reason the provider gave, or null
     */
    public record DeliveryEvent(
            MailDeliveryStatus status,
            String recipient,
            String subject,
            String correlationId,
            String messageId,
            String detail) {}

    /**
     * Records an event against the mail it belongs to.
     *
     * <p>An event that cannot be matched is logged and dropped rather than guessed at: recording a
     * bounce against the wrong message would be worse than recording nothing.
     *
     * @param event     what the provider reported
     * @param stationId the station whose key authorised the report, or null when the instance key
     *                  did. A station may only be told about its own mail.
     * @return whether the event could be matched to a queued mail
     */
    public boolean record(DeliveryEvent event, Integer stationId) {
        var mail = match(event, stationId);
        if (mail.isEmpty()) {
            log.warn(
                    "Delivery event '{}' for {} could not be matched to a queued mail",
                    event.status(),
                    event.recipient());
            return false;
        }
        int id = mail.get().id();
        queueRepository.recordDelivery(id, event.status(), event.detail(), event.messageId());
        if (event.status() == MailDeliveryStatus.DELIVERED) {
            log.debug("Mail {} was delivered to {}", id, event.recipient());
        } else {
            log.warn(
                    "Mail {} to {} came back as {}: {}",
                    id,
                    event.recipient(),
                    event.status(),
                    event.detail() == null ? "no reason given" : event.detail());
            retry(mail.get(), event.status());
        }
        return true;
    }

    /**
     * Sends a refused mail out again, by another route where the route was the problem.
     *
     * <p>A hard bounce or a spam complaint is never retried, because the address does not exist or the
     * reader asked not to hear from us. What is retried is refused differently:
     *
     * <ul>
     *   <li><b>Blocked</b> means the receiving side refused our relay, not our message. Trying the
     *       same relay again would be refused the same way, so the mail goes straight to the next
     *       provider in the chain. This is the case a relay on somebody's block list produces.
     *   <li><b>A soft bounce or an error</b> may pass on its own (a full mailbox, a server having
     *       a bad minute), so the same provider keeps its remaining attempts before the chain moves
     *       on.
     * </ul>
     *
     * <p>When the chain has nothing left, the send loop finds no provider in turn and records the
     * mail as failed, which is where an operator sees it.
     */
    private void retry(EmailQueueRepository.QueuedEmail mail, MailDeliveryStatus status) {
        if (!status.worthRetrying()) return;
        var chain = mail.stationId() == null ? chainService.forInstance() : chainService.forStation(mail.stationId());
        if (status == MailDeliveryStatus.BLOCKED) {
            queueRepository.advanceProvider(mail.id());
        } else {
            int allowed = chainService
                    .at(chain, mail.providerPosition())
                    .map(entry -> entry.attempts())
                    .orElse(1);
            queueRepository.countAttempt(mail.id());
            if (mail.attempts() + 1 >= allowed) queueRepository.advanceProvider(mail.id());
        }
        queueRepository.requeue(mail.id());
        log.info("Mail {} to {} goes out again after {}", mail.id(), mail.recipient(), status);
    }

    /**
     * Finds the mail an event belongs to: by our own token where the provider carried it, otherwise
     * by what every event names: the recipient, and the subject where there is one.
     *
     * <p>A report authorised by a station key is held to that station's own mail, so a station
     * cannot learn about, or interfere with, anybody else's.
     */
    private Optional<EmailQueueRepository.QueuedEmail> match(DeliveryEvent event, Integer stationId) {
        if (event.correlationId() != null && !event.correlationId().isBlank()) {
            try {
                var byToken = queueRepository.findById(
                        Integer.parseInt(event.correlationId().trim()));
                if (byToken.filter(mail -> permitted(mail, stationId)).isPresent()) return byToken;
            } catch (NumberFormatException e) {
                log.debug("Delivery event carried a token that is not one of ours: {}", event.correlationId());
            }
        }
        if (event.recipient() == null || event.recipient().isBlank()) return Optional.empty();
        return queueRepository
                .findLatestFor(event.recipient(), event.subject(), stationId)
                .filter(mail -> permitted(mail, stationId));
    }

    /**
     * Whether the key that authorised this report may speak for this mail. The instance key may
     * speak for all of it; a station key only for what that station sent.
     */
    private static boolean permitted(EmailQueueRepository.QueuedEmail mail, Integer stationId) {
        return stationId == null || stationId.equals(mail.stationId());
    }
}
