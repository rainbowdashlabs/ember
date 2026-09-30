/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * Finds the partner row a station holds for another station before anything is asked of that
 * partner, and insists the partnership is active.
 */
@Singleton
public class FederationEntityResolver {
    private final FederationRepository federationRepository;

    @Inject
    public FederationEntityResolver(FederationRepository federationRepository) {
        this.federationRepository = federationRepository;
    }

    /**
     * The active partner row a station holds for another station.
     *
     * @param localStationId    the asking station
     * @param partnerStationUid the partner station
     * @return the partner row
     * @throws IllegalArgumentException when the station holds no row for that partner
     * @throws BadRequestResponse       when the partnership is not active
     */
    public FederationPartner requireActivePartner(int localStationId, UUID partnerStationUid) {
        var partner = federationRepository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner"));
        if (partner.status() != FederationStatus.ACTIVE) {
            throw new BadRequestResponse("Partner is not active");
        }
        return partner;
    }
}
