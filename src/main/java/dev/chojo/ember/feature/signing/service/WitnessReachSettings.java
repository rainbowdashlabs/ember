/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one audience a station narrows its witnesses by, set next to the permission to collect signatures in
 * person and read by {@link WitnessReach}. It holds for every holder of the permission alike, however they
 * were granted it. An audience naming nobody narrows nothing: the witnesses then reach every current member
 * and trial user of the station.
 */
@Singleton
public class WitnessReachSettings {
    private static final Logger log = LoggerFactory.getLogger(WitnessReachSettings.class);

    private final RestrictionService restrictions;

    @Inject
    public WitnessReachSettings(RestrictionService restrictions) {
        this.restrictions = restrictions;
    }

    /**
     * @param session somebody of the station
     * @return the audience the station's witnesses are narrowed by, naming nobody where none is set
     */
    public RestrictionAudience audience(StationSession session) {
        return restrictions.audienceOf(RestrictionType.WITNESS_REACH, session.stationId());
    }

    /**
     * Replaces the audience the station's witnesses are narrowed by.
     *
     * @param session  who changes it, already allowed to by the route
     * @param audience the new audience; one naming nobody lets the witnesses reach everybody again
     * @return the audience as it now stands
     */
    public RestrictionAudience setAudience(StationSession session, RestrictionAudience audience) {
        restrictions.setAudience(RestrictionType.WITNESS_REACH, session.stationId(), audience);
        log.info(
                "Member {} changed whom the witnesses of station {} reach",
                session.member().id(),
                session.stationId());
        return audience(session);
    }
}
