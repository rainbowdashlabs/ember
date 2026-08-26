/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.station.entity.ThemeFeel;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A body that owns several stations: a district association, an umbrella organisation, a municipality.
 *
 * <p>A cluster is not a station, but it owns one. The home station is where its content, its inventory pool
 * and its federation identity live, which is what lets cluster content reach member stations through the
 * federation machinery that already exists.
 *
 * @param id               the internal identifier
 * @param uid              the stable identity used wherever the cluster is named across instances
 * @param name             what the cluster is called
 * @param description      a sentence about it, shown where it is presented
 * @param homeStationId    the station shell it owns
 * @param autoFederate     whether member stations are paired with it and each other as they join
 * @param themeLocked      whether member stations may change their theme
 * @param colorsLocked     whether member stations may change their colours
 * @param feelLocked       whether member stations may change the rest of their look and feel
 * @param logoLocked       whether member stations may change their logo
 * @param storagePoolBytes how much storage it has to hand out, or {@code null} for no pool of its own
 * @param lossReportRequires what a station has to bring when it reports a piece of this body's gear missing
 * @param storageBackendReach  how far its own storage reaches, which is what it decided rather than where
 *                             anybody's bytes are
 * @param storageBackendLocked whether a station may point itself anywhere
 * @param createdAt        when it was created
 */
public record Cluster(
        int id,
        UUID uid,
        String name,
        String description,
        int homeStationId,
        boolean autoFederate,
        boolean themeLocked,
        boolean colorsLocked,
        boolean feelLocked,
        boolean logoLocked,
        Long storagePoolBytes,
        String defaultTheme,
        String customThemeColors,
        ThemeFeel defaultFeel,
        boolean usesInventory,
        LossReportRequirement lossReportRequires,
        ClusterBackendReach storageBackendReach,
        boolean storageBackendLocked,
        Instant createdAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<Cluster> map() {
        return row -> new Cluster(
                row.getInt("id"),
                row.get("uid", StandardValueConverter.UUID_STRING),
                row.getString("name"),
                row.getString("description"),
                row.getInt("home_station_id"),
                row.getBoolean("auto_federate"),
                row.getBoolean("theme_locked"),
                row.getBoolean("colors_locked"),
                row.getBoolean("feel_locked"),
                row.getBoolean("logo_locked"),
                row.getObject("storage_pool_bytes", Long.class),
                row.getString("default_theme"),
                row.getString("custom_theme_colors"),
                row.getEnum("default_feel", ThemeFeel.class),
                row.getBoolean("uses_inventory"),
                row.getEnum("loss_report_requires", LossReportRequirement.class),
                row.getEnum("storage_backend_reach", ClusterBackendReach.class),
                row.getBoolean("storage_backend_locked"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
