/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

/**
 * A file a mail carries beside its text.
 *
 * <p>The bytes are handed over as they are, without a copy: an attachment is built once for the one mail
 * it goes with and read once when that mail is sent.
 *
 * @param fileName    the name the file carries in the mail
 * @param contentType its media type, such as {@code application/pdf}
 * @param content     the file
 */
public record MailAttachment(String fileName, String contentType, byte[] content) {

    /** The media type of a PDF. */
    public static final String PDF = "application/pdf";
}
