/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.entity;

/**
 * Where the title of a filed document comes from.
 */
public enum MailTitleSource {
    /**
     * What the message was called. The better answer where a human wrote the subject, and it falls
     * back to the file name where the subject is empty.
     */
    SUBJECT,
    /** What the file was called, which is the better answer where a machine sent the mail. */
    FILE_NAME
}
