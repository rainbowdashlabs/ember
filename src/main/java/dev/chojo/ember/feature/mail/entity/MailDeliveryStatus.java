/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

/**
 * What became of an email after the relay accepted it.
 *
 * <p>This is a different question from the send status on the queue, which only says whether the
 * relay took the message off our hands. A relay answering "250 OK" has promised to try, not to
 * succeed: the message can still be refused by the receiving server minutes later, and that answer
 * comes back as a provider event rather than as the result of our own send.
 */
public enum MailDeliveryStatus {
    /** Nothing has come back yet. Every mail starts here and most stay here until an event arrives. */
    UNKNOWN,
    /** The receiving server accepted the message. */
    DELIVERED,
    /**
     * Refused for a reason that may pass - a full mailbox, a greylisting, a sending address on a
     * block list. Worth trying again, possibly by another route.
     */
    SOFT_BOUNCE,
    /** Refused for good: the address does not exist. Sending again only damages the sender's standing. */
    HARD_BOUNCE,
    /** The receiving side refused the sender or its relay outright. */
    BLOCKED,
    /** The recipient marked it as spam. Never send to them again without being asked to. */
    SPAM,
    /** The receiving server asked for a later attempt. The provider retries on its own. */
    DEFERRED,
    /** The provider could not process the message at all. */
    ERROR;

    /**
     * Whether sending the same message again could plausibly succeed.
     *
     * <p>Deliberately narrow: a hard bounce, a spam complaint or a delivery that already worked are
     * never retried, and a deferral is the provider's own business.
     */
    public boolean worthRetrying() {
        return this == SOFT_BOUNCE || this == BLOCKED || this == ERROR;
    }
}
