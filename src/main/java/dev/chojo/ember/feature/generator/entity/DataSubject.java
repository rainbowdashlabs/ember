/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A person whose data went into a generated document.
 *
 * @param memberId the person's membership at the station
 * @param role     why their data is in it
 */
public record DataSubject(int memberId, SubjectRole role) {

    public static RowMapping<DataSubject> map() {
        return row -> new DataSubject(row.getInt("member_id"), row.getEnum("role", SubjectRole.class));
    }
}
