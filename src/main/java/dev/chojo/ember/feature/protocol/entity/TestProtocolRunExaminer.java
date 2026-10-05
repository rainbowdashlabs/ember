/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One examiner of one section of a test run. The assignment covers the section and every section under
 * it.
 *
 * @param runId     the run
 * @param sectionId the section examined
 * @param memberId  the station member examining it
 */
public record TestProtocolRunExaminer(int runId, int sectionId, int memberId) {

    public static RowMapping<TestProtocolRunExaminer> map() {
        return row ->
                new TestProtocolRunExaminer(row.getInt("run_id"), row.getInt("section_id"), row.getInt("member_id"));
    }
}
