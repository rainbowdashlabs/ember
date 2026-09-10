/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.entity;

/**
 * What happened to one attachment.
 *
 * <p>The grain is the attachment and not the message, because a mail carrying three scans can have
 * one imported, the next refused for its type and the third be a duplicate. A log that could say only
 * one thing per message would be unable to report that, and reporting it is the whole point of keeping
 * a log: a rule that quietly imports nothing is the likeliest complaint there is.
 */
public enum MailImportOutcome {
    /** Filed as a document. */
    IMPORTED,
    /** These same bytes are already in this station, however they arrived. */
    DUPLICATE,
    /** No rule of this mailbox trusts the address it came from. */
    SENDER_NOT_ALLOWED,
    /** The sender is trusted by nobody here, or no rule's subject and name filters fit. */
    NO_RULE_MATCHED,
    /** The bytes are not a kind of file the rule accepts, whatever the file was called. */
    TYPE_NOT_ALLOWED,
    /** Larger than the station allows a file to be. */
    TOO_LARGE,
    /** Smaller than the rule counts as a document, which is what keeps signature logos out. */
    TOO_SMALL,
    /** The message carried nothing the rule could look at. */
    NO_ATTACHMENT,
    /** The station has no room left, so nothing was written rather than half of it. */
    QUOTA_EXCEEDED,
    /**
     * The receiving server said the sending domain failed its own published checks. A guard against
     * carelessness rather than a security boundary: the header can be forged as easily as the address.
     */
    AUTHENTICATION_FAILED,
    /** Something went wrong that none of the others describes. */
    FAILED
}
