/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Names the member behind a registration a partner station sent for one of this station's events.
 */
@Singleton
public class FederatedRegistrantService {
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberIdentityFactory memberIdentityFactory;
    private final EventFederationService eventFederationService;

    @Inject
    public FederatedRegistrantService(
            FederationRepository federationRepository,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            MemberIdentityFactory memberIdentityFactory,
            EventFederationService eventFederationService) {
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberIdentityFactory = memberIdentityFactory;
        this.eventFederationService = eventFederationService;
    }

    /**
     * The member behind a federated registration: preferably as a real local member when the
     * partner lives on this instance, otherwise as a federated identity carrying the cached display
     * name and the partner station's name.
     *
     * @param registration the registration a partner sent
     * @return the member, or {@code null} when the partnership it came on is gone
     */
    public @Nullable MemberIdentity identify(EventFederationRegistration registration) {
        UUID partnerStationUid = federationRepository
                .findPartnerById(registration.partnerId())
                .map(FederationPartner::partnerStationId)
                .orElse(null);
        if (partnerStationUid == null) return null;

        var partnerStation = stationRepository.findHereByUid(partnerStationUid);
        if (partnerStation.isPresent()) {
            var localMember =
                    stationMemberRepository.findByUid(partnerStation.get().id(), registration.remoteMemberId());
            if (localMember.isPresent()) {
                return memberIdentityFactory.local(
                        localMember.get().stationId(), localMember.get().id());
            }
        }

        String cachedName = eventFederationService
                .getCachedName(registration.partnerId(), registration.remoteMemberId())
                .orElse(null);
        String stationName = partnerStation.map(Station::name).orElse(null);
        return new MemberIdentity(partnerStationUid, registration.remoteMemberId())
                .withDisplay(cachedName, stationName, null, null);
    }
}
