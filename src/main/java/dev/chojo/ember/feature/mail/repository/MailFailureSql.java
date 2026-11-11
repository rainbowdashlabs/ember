/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

/**
 * The SQL that says whether a mail did not arrive, for every read that counts, lists or takes note of
 * failed mails, so the overview, the sidebar count and the mail log cannot disagree.
 *
 * <p>A mail did not arrive when the queue gave up on it, or when a provider took it and reported
 * afterwards that the address does not exist, that the sender was refused or that the recipient
 * marked it as spam. A soft bounce or a deferral is not a failure: the provider is still trying.
 *
 * <p>Every fragment reads {@code email_queue} without an alias.
 */
public final class MailFailureSql {

    /** The condition that the mail did not arrive. */
    public static final String DID_NOT_ARRIVE = """
            (status = 'FAILED' OR (status = 'SENT' AND delivery_status IN ('HARD_BOUNCE', 'BLOCKED', 'SPAM')))""";

    /** The condition that the mail did not arrive and nobody has taken note of it yet. */
    public static final String UNACKNOWLEDGED = "(acknowledged_at IS NULL AND " + DID_NOT_ARRIVE + ")";

    /**
     * Why the mail did not arrive, as a {@code FailedMailCause}: the queue's reason for a mail it gave
     * up on, the provider's report otherwise.
     */
    public static final String CAUSE = """
            (CASE WHEN status = 'FAILED' THEN coalesce(failure_reason, 'UNRECORDED') ELSE delivery_status END)""";

    /**
     * When the mail failed: when the queue gave up on it, and when the provider's report came in
     * otherwise. A give-up recorded before that moment was kept falls back to when the queue last
     * took it in hand.
     */
    public static final String FAILED_AT = """
            (CASE WHEN status = 'FAILED' THEN coalesce(failed_at, claimed_at, created_at)
                  ELSE coalesce(delivery_updated_at, sent_at, created_at) END)""";

    private MailFailureSql() {}
}
