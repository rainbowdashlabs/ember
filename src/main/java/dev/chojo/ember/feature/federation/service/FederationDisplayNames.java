/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;

import java.util.Optional;

/**
 * Single source of truth for "what name do we display for a federation partner station". The
 * preferred name is whatever the local {@code station} table holds for that UID (fresh and
 * authoritative when the partner lives on this instance); when the partner is remote we fall
 * back to {@link FederationPartner#partnerStationName()} which was captured at pairing or
 * carried in via a cross-instance transfer; ultimately a caller-supplied placeholder is used.
 *
 * <p>Centralised here so the dozens of feature surfaces that show a partner-station label
 * (news, KB, events, protocols, lending, exchanges, lost-and-found, boards, quiz) all share
 * the same fallback chain instead of each one re-implementing it.
 */
public final class FederationDisplayNames {

    private FederationDisplayNames() {}

    /**
     * Resolves the partner station's display name using the (local → persisted → fallback) chain.
     */
    public static String partnerName(StationRepository stationRepository, FederationPartner partner, String fallback) {
        if (partner == null) return fallback;
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::name)
                .or(() -> Optional.ofNullable(partner.partnerStationName()))
                .filter(s -> !s.isBlank())
                .orElse(fallback);
    }
}
