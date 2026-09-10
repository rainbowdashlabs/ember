/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

/**
 * What the operator, rather than a station, decides about reading mail for documents.
 *
 * <p>A station says which mailboxes it watches and how often. Three things are not theirs to say,
 * because they are about the instance and about the providers it talks to rather than about one
 * station's paperwork.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("MAIL_IMPORT")
public class MailImport {

    private static final int LOWEST_ALLOWED_FLOOR_MINUTES = 5;
    private static final int HIGHEST_ALLOWED_FLOOR_MINUTES = 1440;
    private static final int MIN_ATTACHMENTS_PER_CYCLE = 1;
    private static final int MAX_ATTACHMENTS_PER_CYCLE = 1000;
    private static final int MIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_TIMEOUT_SECONDS = 300;
    private static final int MIN_LOG_RETENTION_DAYS = 7;
    private static final int MAX_LOG_RETENTION_DAYS = 3650;

    /**
     * Whether mail is read for documents at all.
     *
     * <p>Off switches the poller off for the whole instance whatever any station has configured, for
     * an operator who does not want a background job reaching out to other people's mail servers from
     * their host.
     */
    @Overwrite(env = @Env)
    private boolean enabled = true;

    /**
     * The shortest interval a station may set on a mailbox.
     *
     * <p>Fifteen minutes is the default and the floor, so nobody points a one minute poll at a
     * provider that will lock the account for it. An operator whose provider is stricter than that
     * raises this, and every mailbox on the instance is held to the higher number without anybody
     * editing them.
     */
    @Overwrite(env = @Env)
    private int minimumIntervalMinutes = 15;

    /**
     * How many attachments one cycle of one mailbox may file.
     *
     * <p>The cap is what keeps the first cycle of a long neglected mailbox from running for an hour.
     * What it does not reach is picked up by the next cycle, so a backlog drains over a few rounds
     * instead of in one.
     */
    @Overwrite(env = @Env)
    private int maxAttachmentsPerCycle = 50;

    /**
     * How long to wait on a mail server before giving up on it.
     *
     * <p>Load bearing rather than housekeeping. One thread walks the mailboxes in turn, so a host that
     * neither fails nor answers would hold every other station's import for as long as the socket took
     * to give up. Backoff cannot help there, because backoff answers a mailbox that has failed and this
     * one has not failed yet.
     */
    @Overwrite(env = @Env)
    private int timeoutSeconds = 30;

    /**
     * How long the readable half of the import log is kept.
     *
     * <p>The subject and the reason are what a reader looks at, and a year of them is plenty. The
     * duplicate keys are not covered by this and are never pruned, because a mail redelivered after the
     * pruning would otherwise become a second document.
     */
    @Overwrite(env = @Env)
    private int logRetentionDays = 365;

    public boolean enabled() {
        return enabled;
    }

    public int minimumIntervalMinutes() {
        return Math.clamp(minimumIntervalMinutes, LOWEST_ALLOWED_FLOOR_MINUTES, HIGHEST_ALLOWED_FLOOR_MINUTES);
    }

    public int maxAttachmentsPerCycle() {
        return Math.clamp(maxAttachmentsPerCycle, MIN_ATTACHMENTS_PER_CYCLE, MAX_ATTACHMENTS_PER_CYCLE);
    }

    public int timeoutSeconds() {
        return Math.clamp(timeoutSeconds, MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
    }

    public int logRetentionDays() {
        return Math.clamp(logRetentionDays, MIN_LOG_RETENTION_DAYS, MAX_LOG_RETENTION_DAYS);
    }

    @Override
    public String toString() {
        return "MailImport{enabled=" + enabled()
                + ", minimumIntervalMinutes=" + minimumIntervalMinutes()
                + ", maxAttachmentsPerCycle=" + maxAttachmentsPerCycle()
                + ", timeoutSeconds=" + timeoutSeconds()
                + ", logRetentionDays=" + logRetentionDays()
                + '}';
    }
}
