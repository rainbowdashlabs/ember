/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A file kept for the members it concerns.
 *
 * @param id            the document identifier
 * @param stationId     the station the document belongs to
 * @param title         what the document is called, which is what a reader sees
 * @param fileName      the name it was uploaded under, used when it is downloaded again
 * @param mimeType      what kind of file it is, which decides whether it can be shown rather than
 *                      only offered for download
 * @param sizeBytes     how large the file is
 * @param hidden        whether it is kept from the members it belongs to and shown only to whoever
 *                      may read other members
 * @param keepOnArchive whether it survives its members being marked former
 * @param hasThumbnail  whether a picture of it was produced for the tile to show
 * @param uploadedBy    the member who put it there, or null once they are gone
 * @param createdAt     when it was put there
 */
public record MemberDocument(
        int id,
        int stationId,
        String title,
        String fileName,
        String mimeType,
        long sizeBytes,
        boolean hidden,
        boolean keepOnArchive,
        boolean hasThumbnail,
        Integer uploadedBy,
        Instant createdAt) {

    public static RowMapping<MemberDocument> map() {
        return row -> new MemberDocument(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("title"),
                row.getString("file_name"),
                row.getString("mime_type"),
                row.getLong("size_bytes"),
                row.getBoolean("hidden"),
                row.getBoolean("keep_on_archive"),
                row.getBoolean("has_thumbnail"),
                row.getObject("uploaded_by", Integer.class),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
