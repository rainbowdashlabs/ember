/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Generic share record used for KB files/folders, quiz catalogs, and protocols.
 */
public record FederationShare(
        int id,
        int stationId,
        Integer fileId,
        Integer folderId,
        Integer catalogId,
        Integer protocolId,
        ShareScope shareScope) {

    public static RowMapping<FederationShare> mapKb() {
        return row -> new FederationShare(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("file_id", Integer.class),
                row.getObject("folder_id", Integer.class),
                null,
                null,
                row.getEnum("share_scope", ShareScope.class));
    }

    public static RowMapping<FederationShare> mapQuiz() {
        return row -> new FederationShare(
                row.getInt("id"),
                row.getInt("station_id"),
                null,
                null,
                row.getInt("catalog_id"),
                null,
                row.getEnum("share_scope", ShareScope.class));
    }

    public static RowMapping<FederationShare> mapProtocol() {
        return row -> new FederationShare(
                row.getInt("id"),
                row.getInt("station_id"),
                null,
                null,
                null,
                row.getInt("protocol_id"),
                row.getEnum("share_scope", ShareScope.class));
    }
}
