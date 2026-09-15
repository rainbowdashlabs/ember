/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
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
 * @param required        whether an answer is expected, unless an audience says otherwise
 * @param readonly        whether only the member management may write the answer, for everybody asked it
 * @param width           how much of a row it takes, unless an audience says otherwise
 * @param stationReadonly whether the people at the station may only read the answer
 * @param keepOnArchive   whether the answer survives the member being marked as having left
 * @param stationGroupId  the group of stations it is asked of, or {@code null} for every station under the
 *                        association
 */
public record ClusterProfileField(
        int id,
        int clusterId,
        String name,
        ProfileFieldType fieldType,
        ProfileFieldConfig config,
        boolean required,
        boolean readonly,
        String width,
        boolean stationReadonly,
        boolean keepOnArchive,
        Integer stationGroupId) {

    public static RowMapping<ClusterProfileField> map() {
        return row -> new ClusterProfileField(
                row.getInt("id"),
                row.getInt("cluster_id"),
                row.getString("name"),
                row.getEnum("field_type", ProfileFieldType.class),
                ProfileFieldConfig.parse(row.getString("config")),
                row.getBoolean("required"),
                row.getBoolean("readonly"),
                row.getString("width"),
                row.getBoolean("station_readonly"),
                row.getBoolean("keep_on_archive"),
                row.getObject("station_group_id", Integer.class));
    }
}
