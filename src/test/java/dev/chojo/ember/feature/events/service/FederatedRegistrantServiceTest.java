/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The member behind a partner's registration is this instance's own member where the partner lives
 * here, and otherwise the partner's member under the name it last sent.
 */
class FederatedRegistrantServiceTest {
    private static final UUID PARTNER_STATION = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID REMOTE_MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000077");
    private static final EventFederationRegistration REGISTRATION = new EventFederationRegistration(
            1, 4, 7, REMOTE_MEMBER, LocalDate.of(2026, 5, 1), RegistrationStatus.PENDING, Instant.EPOCH);

    private FederationRepository partners;
    private StationRepository stations;
    private StationMemberRepository members;
    private MemberIdentityFactory identities;
    private EventFederationService events;
    private FederatedRegistrantService service;

    @BeforeEach
    void setup() {
        partners = mock(FederationRepository.class);
        stations = mock(StationRepository.class);
        members = mock(StationMemberRepository.class);
        identities = mock(MemberIdentityFactory.class);
        events = mock(EventFederationService.class);
        service = new FederatedRegistrantService(partners, stations, members, identities, events);
        when(partners.findPartnerById(7))
                .thenReturn(Optional.of(new FederationPartner(
                        7,
                        3,
                        PARTNER_STATION,
                        null,
                        null,
                        null,
                        FederationPartner.FederationStatus.ACTIVE,
                        null,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        null,
                        "Wache Nord")));
        when(events.getCachedName(7, REMOTE_MEMBER)).thenReturn(Optional.of("Kim"));
    }

    private static Station station() {
        var station = mock(Station.class);
        when(station.id()).thenReturn(8);
        when(station.name()).thenReturn("Wache Nord");
        return station;
    }

    @Test
    void aRegistrationWhosePartnershipIsGoneNamesNobody() {
        when(partners.findPartnerById(7)).thenReturn(Optional.empty());

        assertNull(service.identify(REGISTRATION));
    }

    @Test
    void aPartnerLivingHereNamesItsOwnMember() {
        var station = station();
        when(stations.findByUid(PARTNER_STATION)).thenReturn(Optional.of(station));
        when(members.findByUid(8, REMOTE_MEMBER))
                .thenReturn(Optional.of(new StationMember(
                        21, 8, REMOTE_MEMBER, null, false, null, "Kim", StationUserType.MEMBER, LocalDate.EPOCH)));
        var local = new MemberIdentity(PARTNER_STATION, REMOTE_MEMBER).withDisplay("Kim K.", "Wache Nord", null, null);
        when(identities.local(8, 21)).thenReturn(local);

        assertEquals(local, service.identify(REGISTRATION));
    }

    @Test
    void aMemberNotFoundHereIsNamedAsThePartnerSentIt() {
        var station = station();
        when(stations.findByUid(PARTNER_STATION)).thenReturn(Optional.of(station));
        when(members.findByUid(8, REMOTE_MEMBER)).thenReturn(Optional.empty());

        var identity = service.identify(REGISTRATION);

        assertEquals("Kim", identity.name());
        assertEquals("Wache Nord", identity.stationName());
    }

    @Test
    void aPartnerOnAnotherInstanceIsNamedWithoutAStation() {
        when(stations.findByUid(PARTNER_STATION)).thenReturn(Optional.empty());

        var identity = service.identify(REGISTRATION);

        assertEquals(PARTNER_STATION, identity.stationUid());
        assertEquals("Kim", identity.name());
        assertNull(identity.stationName());
    }
}
