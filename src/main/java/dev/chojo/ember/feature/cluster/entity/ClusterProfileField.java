/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;

/**
 * A question a cluster asks of the people at its stations.
 *
 * <p>The same shape as a station's own profile field, deliberately: it is the same kind of thing asked by
 * somebody else. Sharing {@link ProfileFieldConfig} rather than copying it means a setting added to station
 * fields later applies to these for free, and that the two lay out beside each other in one profile rather
 * than in two blocks that happen to look similar.
 *
 * @param id              the field
 * @param clusterId       the cluster asking
 * @param name            the label
 * @param fieldType       what kind of answer it takes
 * @param config          the same settings a station field carries
 * @param position        where it sits among the cluster's own fields
 * @param scope           which kind of member it applies to
 * @param stationReadonly whether the people at the station may only read the answer
 * @param keepOnArchive   whether the answer survives the member being marked as having left
 */
public record ClusterProfileField(
        int id,
        int clusterId,
        String name,
        ProfileFieldType fieldType,
        ProfileFieldConfig config,
        int position,
        ProfileFieldScope scope,
        boolean stationReadonly,
        boolean keepOnArchive) {

    public static RowMapping<ClusterProfileField> map() {
        return row -> new ClusterProfileField(
                row.getInt("id"),
                row.getInt("cluster_id"),
                row.getString("name"),
                row.getEnum("field_type", ProfileFieldType.class),
                ProfileFieldConfig.parse(row.getString("config")),
                row.getInt("position"),
                row.getEnum("scope", ProfileFieldScope.class),
                row.getBoolean("station_readonly"),
                row.getBoolean("keep_on_archive"));
    }
}
