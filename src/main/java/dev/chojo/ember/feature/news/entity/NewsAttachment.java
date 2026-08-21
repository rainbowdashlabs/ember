/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A file a news entry hands over: the minutes as a PDF, the form to bring along, the flyer for
 * the exercise.
 *
 * <p>An attachment points at a file in the station media library rather than holding bytes of
 * its own, which is what gives it deduplication, the station quota and the library's ownership
 * for free. It is never part of the stored body: the body of an entry is what the author wrote,
 * and appending a download list to it would put attachments into the search summary and the
 * notification preview, where they do not belong.
 *
 * <p>The file's own columns travel with the row because an attachment is never read without
 * them: a reader needs the name, the size and the hash to be handed anything at all.
 *
 * @param label what a reader sees instead of the file name, or {@code null} to use the file name
 */
public record NewsAttachment(
        int id,
        int newsId,
        int fileId,
        String label,
        int sortOrder,
        Instant createdAt,
        String fileName,
        String mimeType,
        long fileSize,
        String contentHash) {

    public static RowMapping<NewsAttachment> map() {
        return row -> new NewsAttachment(
                row.getInt("id"),
                row.getInt("news_id"),
                row.getInt("file_id"),
                row.getString("label"),
                row.getInt("sort_order"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getString("file_name"),
                row.getString("mime_type"),
                row.getLong("file_size"),
                row.getString("content_hash"));
    }

    /**
     * What a reader should see: the author's label where they gave one, the file name otherwise.
     */
    public String displayName() {
        return label != null && !label.isBlank() ? label : fileName;
    }
}
