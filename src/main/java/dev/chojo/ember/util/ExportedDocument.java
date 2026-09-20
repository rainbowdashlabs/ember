/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.nio.charset.StandardCharsets;

/**
 * A document and the name it should reach the reader under.
 *
 * <p>The two travel together because whoever builds a document is the only one who knows what it
 * turned out to be: which period it covers, whose list it is, which language the station reads. A
 * route handed only the bytes would have to work all of that out a second time.
 *
 * @param bytes    the finished document
 * @param filename the name, already whole, including its extension
 */
public record ExportedDocument(byte[] bytes, String filename) {

    /** Builds the header that carries the name to the browser. */
    public String contentDisposition() {
        return SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, filename);
    }

    /**
     * A document that was written as text rather than rendered.
     *
     * <p>A spreadsheet opens its umlauts as rubbish without a byte order mark in front, which is the
     * single most reported thing about a CSV and costs two bytes to prevent. It goes in here so that
     * no writer has to remember it.
     */
    public static ExportedDocument ofText(String text, String filename) {
        return new ExportedDocument((BYTE_ORDER_MARK + text).getBytes(StandardCharsets.UTF_8), filename);
    }

    private static final String BYTE_ORDER_MARK = "﻿";
}
