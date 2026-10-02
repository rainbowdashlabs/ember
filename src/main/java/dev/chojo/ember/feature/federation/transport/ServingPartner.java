/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.feature.federation.entity.FederationPartner;

import java.util.UUID;

/**
 * The partnership as the serving station sees it: its own row for the asking station.
 *
 * <p>Share targets reference the serving side's rows, so every share check a serving function
 * makes is made against {@link #partnerId()}, whichever way the request arrived.
 *
 * @param row               the serving station's partner row for the asking station
 * @param askingStationUid  the public identity of the station that asked
 */
public record ServingPartner(FederationPartner row, UUID askingStationUid) {

    /**
     * The partnership a signed {@code /remote} request arrived on.
     *
     * @param session the verified federation session of the request
     * @return the serving side of it
     */
    public static ServingPartner of(FederationSession session) {
        return new ServingPartner(session.partner(), session.partnerStationUid());
    }

    /**
     * The station answering the request.
     *
     * @return its internal id
     */
    public int servingStationId() {
        return row.stationId();
    }

    /**
     * The serving station's partner row, which is what share targets reference.
     *
     * @return the row's id
     */
    public int partnerId() {
        return row.id();
    }
}
