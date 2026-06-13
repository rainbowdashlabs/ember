/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberNameResolverTest {
    private StationMemberService memberService;
    private AccountRepository accountRepository;
    private EventFederationRepository eventFederationRepository;
    private FederationRepository federationRepository;
    private StationRepository stationRepository;
    private MemberNameResolver resolver;

    @BeforeEach
    void setup() {
        memberService = mock(StationMemberService.class);
        accountRepository = mock(AccountRepository.class);
        eventFederationRepository = mock(EventFederationRepository.class);
        federationRepository = mock(FederationRepository.class);
        stationRepository = mock(StationRepository.class);
        resolver = new MemberNameResolver(
                memberService,
                accountRepository,
                eventFederationRepository,
                federationRepository,
                stationRepository,
                mock(MemberGroupService.class),
                mock(UserTagService.class));
    }

    @Test
    void resolveLocal_withAccount_returnsFullName() {
        var member = new StationMember(1, 1, UUID.randomUUID(), 10, false, null, null, StationUserType.MEMBER);
        var account = new Account(10, "test@test.com", "Max", "Meier", true, InstanceUserType.USER, "Max Meier");
        when(memberService.findById(1)).thenReturn(Optional.of(member));
        when(accountRepository.findById(10)).thenReturn(Optional.of(account));

        assertEquals("Max Meier", resolver.resolveLocal(1));
    }

    @Test
    void resolveLocal_withDisplayName_returnsDisplayName() {
        var member = new StationMember(
                2, 1, UUID.randomUUID(), null, false, null, "Firefighter Joe", StationUserType.MEMBER);
        when(memberService.findById(2)).thenReturn(Optional.of(member));

        assertEquals("Firefighter Joe", resolver.resolveLocal(2));
    }

    @Test
    void resolveLocal_notFound_returnsNull() {
        when(memberService.findById(99)).thenReturn(Optional.empty());

        assertNull(resolver.resolveLocal(99));
    }

    @Test
    void resolveLocal_accountPreferred_overDisplayName() {
        var member = new StationMember(3, 1, UUID.randomUUID(), 20, false, null, "Old Name", StationUserType.MEMBER);
        var account = new Account(20, "test@test.com", "New", "Name", true, InstanceUserType.USER, "New Name");
        when(memberService.findById(3)).thenReturn(Optional.of(member));
        when(accountRepository.findById(20)).thenReturn(Optional.of(account));

        assertEquals("New Name", resolver.resolveLocal(3));
    }

    @Test
    void resolveFederated_cached_returnsCachedName() {
        UUID memberUid = UUID.randomUUID();
        when(eventFederationRepository.getCachedName(5, memberUid)).thenReturn(Optional.of("Lisa Brandmeister"));

        assertEquals("Lisa Brandmeister", resolver.resolveFederated(5, memberUid));
    }

    @Test
    void resolveFederated_notCached_returnsStationName() {
        UUID memberUid = UUID.randomUUID();
        UUID partnerStationUid = UUID.randomUUID();
        var partner = mock(FederationPartner.class);
        var station = mock(Station.class);
        when(partner.partnerStationId()).thenReturn(partnerStationUid);
        when(station.name()).thenReturn("Partnerwache");
        when(eventFederationRepository.getCachedName(5, memberUid)).thenReturn(Optional.empty());
        when(federationRepository.findPartnerById(5)).thenReturn(Optional.of(partner));
        when(stationRepository.findByUid(partnerStationUid)).thenReturn(Optional.of(station));

        assertEquals("Partnerwache", resolver.resolveFederated(5, memberUid));
    }

    @Test
    void resolveFederated_nothingFound_returnsNull() {
        UUID memberUid = UUID.randomUUID();
        when(eventFederationRepository.getCachedName(5, memberUid)).thenReturn(Optional.empty());
        when(federationRepository.findPartnerById(5)).thenReturn(Optional.empty());

        assertNull(resolver.resolveFederated(5, memberUid));
    }

    @Test
    void resolve_localTakesPriority() {
        var member = new StationMember(1, 1, UUID.randomUUID(), 10, false, null, null, StationUserType.MEMBER);
        var account = new Account(10, "test@test.com", "Max", "Meier", true, InstanceUserType.USER, "Max Meier");
        when(memberService.findById(1)).thenReturn(Optional.of(member));
        when(accountRepository.findById(10)).thenReturn(Optional.of(account));

        assertEquals("Max Meier", resolver.resolve(1, 5, UUID.randomUUID()));
        verifyNoInteractions(eventFederationRepository);
    }

    @Test
    void resolve_fallsThrough_toFederated() {
        UUID memberUid = UUID.randomUUID();
        when(memberService.findById(99)).thenReturn(Optional.empty());
        when(eventFederationRepository.getCachedName(5, memberUid)).thenReturn(Optional.of("Remote User"));

        assertEquals("Remote User", resolver.resolve(99, 5, memberUid));
    }

    @Test
    void resolve_nullActorMemberId_resolvesFederated() {
        UUID memberUid = UUID.randomUUID();
        when(eventFederationRepository.getCachedName(5, memberUid)).thenReturn(Optional.of("Remote User"));

        assertEquals("Remote User", resolver.resolve(null, 5, memberUid));
        verifyNoInteractions(memberService);
    }

    @Test
    void resolve_allNull_returnsNull() {
        assertNull(resolver.resolve(null, null, null));
    }
}
