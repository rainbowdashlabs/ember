/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.nio.charset.StandardCharsets;

/**
 * A finished document with its whole filename, named by its builder, which alone knows what it covers.
 *
 * @param bytes    the finished document
 * @param filename the name, including its extension
 */
public record ExportedDocument(byte[] bytes, String filename) {

    /** Builds the header that carries the name to the browser. */
    public String contentDisposition() {
        return SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, filename);
    }

    /** A text document as UTF-8 behind a byte order mark, without which a spreadsheet garbles umlauts. */
    public static ExportedDocument ofText(String text, String filename) {
        return new ExportedDocument((BYTE_ORDER_MARK + text).getBytes(StandardCharsets.UTF_8), filename);
    }

    private static final String BYTE_ORDER_MARK = "﻿";
}
