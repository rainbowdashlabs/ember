/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.Context;

/**
 * Answers with a stored file that somebody uploaded: a member document, whichever door it is read
 * through, or the evidence attached to an equipment movement.
 *
 * <p>The type and the name were both chosen by the uploader, so neither reaches the reader unchecked.
 * Only a type that is safe to show is shown in the browser, everything else is offered as a download,
 * and the name is written so it cannot break out of its header.
 */
public final class FileResponse {
    private FileResponse() {}

    /**
     * Writes the file as the answer.
     *
     * @param ctx      the request being answered
     * @param mimeType the type it was stored as
     * @param fileName the name it was uploaded under
     * @param data     its bytes
     */
    public static void send(Context ctx, String mimeType, String fileName, byte[] data) {
        var disposition = SafeInlineMime.isInlineSafe(mimeType)
                ? SafeContentDisposition.Disposition.INLINE
                : SafeContentDisposition.Disposition.ATTACHMENT;
        ctx.contentType(SafeInlineMime.safeContentType(mimeType));
        ctx.header("Content-Disposition", SafeContentDisposition.build(disposition, fileName));
        ctx.result(data);
    }
}
