/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Somebody the reader may write to, with one station they share as a hint of who they are.
 *
 * @param person      the person
 * @param stationName the name of a station both are current members of
 */
public record ReachablePerson(Person person, String stationName) {

    /**
     * Reads a row carrying the person under {@code person_} and the station name.
     *
     * @return the mapping
     */
    public static RowMapping<ReachablePerson> map() {
        return row -> new ReachablePerson(Person.require(row, "person_"), row.getString("station_name"));
    }
}
