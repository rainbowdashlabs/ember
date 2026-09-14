/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A file an event hands over: the route sheet, the form to bring along, the plan the crew works from.
 *
 * <p>An attachment points at a file in the station media library rather than holding bytes of its
 * own, which is what gives it the deduplication, the station quota and the library's ownership for
 * free. The file's own columns travel with the row because an attachment is never read without them:
 * a reader needs the name, the size and the type to be handed anything at all.
 *
 * @param internal whether the file is kept back from the room. An open file is for everybody who may
 *                 see the event and travels to a partner station the event is shared with; an
 *                 internal one is read with the right to read what an event keeps internal, and stays
 *                 at the station that owns it
 * @param label    what a reader sees instead of the file name, or {@code null} to use the file name
 */
public record EventAttachment(
        int id,
        int eventId,
        int fileId,
        String label,
        boolean internal,
        int sortOrder,
        Instant createdAt,
        String fileName,
        String mimeType,
        long fileSize,
        String contentHash) {

    /** What a reader is shown for this file, which is the label where one was written. */
    public String displayName() {
        return label == null || label.isBlank() ? fileName : label;
    }

    public static RowMapping<EventAttachment> map() {
        return row -> new EventAttachment(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getInt("file_id"),
                row.getString("label"),
                row.getBoolean("internal"),
                row.getInt("sort_order"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getString("file_name"),
                row.getString("mime_type"),
                row.getLong("file_size"),
                row.getString("content_hash"));
    }
}
