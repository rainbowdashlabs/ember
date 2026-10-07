/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.owner;

import de.chojo.sadu.queries.api.call.Call;

import static de.chojo.sadu.queries.api.call.Call.call;

/**
 * Finds the rows of one owner in a table that names it with a {@code station_id} and a
 * {@code cluster_id} column, the instance's rows having neither.
 *
 * <p>Both columns are compared with {@code IS NOT DISTINCT FROM}, so one query finds the rows of a
 * station, an association or the instance alike.
 */
public final class OwnerColumns {

    /** The condition that keeps the rows of the owner {@link #bind} binds. */
    public static final String OWNED_BY = """
            station_id IS NOT DISTINCT FROM :station_id::int
            AND cluster_id IS NOT DISTINCT FROM :cluster_id::int""";

    private OwnerColumns() {}

    /**
     * @param owner the station, the association or the instance
     * @return a call binding the owner's {@code station_id} and {@code cluster_id}, null where it has none
     */
    public static Call bind(Owner owner) {
        Integer stationId = owner instanceof Owner.Station station ? station.stationId() : null;
        Integer clusterId = owner instanceof Owner.Association association ? association.clusterId() : null;
        return call().bind("station_id", stationId).bind("cluster_id", clusterId);
    }
}
