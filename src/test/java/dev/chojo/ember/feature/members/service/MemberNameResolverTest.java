/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
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
    void called_withAccount_returnsFullName() {
        var member = new StationMember(1, 1, UUID.randomUUID(), 10, false, null, null, StationUserType.MEMBER, null);
        var account = new Account(
                10,
                UUID.randomUUID(),
                "test@test.com",
                null,
                "Max",
                "Meier",
                true,
                InstanceUserType.USER,
                "Max Meier",
                null,
                null);
        when(memberService.findById(1)).thenReturn(Optional.of(member));
        when(accountRepository.findById(10)).thenReturn(Optional.of(account));

        assertEquals("Max Meier", resolver.called(1));
    }

    @Test
    void called_withDisplayName_returnsDisplayName() {
        var member = new StationMember(
                2, 1, UUID.randomUUID(), null, false, null, "Firefighter Joe", StationUserType.MEMBER, null);
        when(memberService.findById(2)).thenReturn(Optional.of(member));

        assertEquals("Firefighter Joe", resolver.called(2));
    }

    @Test
    void called_notFound_returnsNull() {
        when(memberService.findById(99)).thenReturn(Optional.empty());

        assertNull(resolver.called(99));
    }

    @Test
    void called_accountPreferred_overDisplayName() {
        var member =
                new StationMember(3, 1, UUID.randomUUID(), 20, false, null, "Old Name", StationUserType.MEMBER, null);
        var account = new Account(
                20,
                UUID.randomUUID(),
                "test@test.com",
                null,
                "New",
                "Name",
                true,
                InstanceUserType.USER,
                "New Name",
                null,
                null);
        when(memberService.findById(3)).thenReturn(Optional.of(member));
        when(accountRepository.findById(20)).thenReturn(Optional.of(account));

        assertEquals("New Name", resolver.called(3));
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
        var member = new StationMember(1, 1, UUID.randomUUID(), 10, false, null, null, StationUserType.MEMBER, null);
        var account = new Account(
                10,
                UUID.randomUUID(),
                "test@test.com",
                null,
                "Max",
                "Meier",
                true,
                InstanceUserType.USER,
                "Max Meier",
                null,
                null);
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

    private void memberWithAccount(int memberId, int accountId, String firstName, String lastName) {
        var member = new StationMember(
                memberId, 1, UUID.randomUUID(), accountId, false, null, null, StationUserType.MEMBER, null);
        var account = new Account(
                accountId,
                UUID.randomUUID(),
                "test@test.com",
                null,
                firstName,
                lastName,
                true,
                InstanceUserType.USER,
                (firstName + " " + lastName).trim(),
                null,
                null);
        when(memberService.findById(memberId)).thenReturn(Optional.of(member));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    }

    /**
     * The four ways of writing one person, which are the same name until there is a second one to
     * put beside it.
     */
    @Test
    void aMemberIsWrittenFourWays() {
        memberWithAccount(40, 400, "Maximilian", "Hoffmann");

        assertEquals("Maximilian Hoffmann", resolver.called(40), "what the station reads");
        assertEquals("Maximilian Hoffmann", resolver.identified(40), "what a list of people reads");
        assertEquals("Maximilian Hoffmann", resolver.official(40), "what a document carries");
        assertEquals("Maximilian", resolver.greeting(40), "what a mail says hello to");
    }

    /**
     * Somebody who has left has one frozen name and no account behind it, so there are no halves to
     * take apart and every form gives that name back whole.
     */
    @Test
    void aFormerMemberIsWrittenTheSameWayFourTimes() {
        var former = new StationMember(
                41, 1, UUID.randomUUID(), null, true, null, "Maximilian Hoffmann", StationUserType.MEMBER, null);
        when(memberService.findById(41)).thenReturn(Optional.of(former));

        assertEquals("Maximilian Hoffmann", resolver.called(41));
        assertEquals("Maximilian Hoffmann", resolver.identified(41));
        assertEquals("Maximilian Hoffmann", resolver.official(41));
        assertEquals("Maximilian Hoffmann", resolver.greeting(41), "there is no first name left to greet");
    }

    /** A member nothing is known about is written as nothing rather than as a broken name. */
    @Test
    void aMemberNobodyKnowsIsWrittenAsNothing() {
        when(memberService.findById(42)).thenReturn(Optional.empty());

        assertNull(resolver.called(42));
        assertNull(resolver.identified(42));
        assertNull(resolver.official(42));
        assertNull(resolver.greeting(42));
    }

    /**
     * A name changed is a name read.
     *
     * <p>What is kept is kept until somebody says it is stale. The cache it replaced expired five
     * minutes after the last read rather than after the write, so a member whose name was read
     * every few minutes, which is anybody at a station people are working in, would have kept an
     * old name for as long as people kept looking at it.
     */
    @Test
    void aNameIsReadAgainOnceItIsForgotten() {
        memberWithAccount(43, 430, "Maximilian", "Hoffmann");
        assertEquals("Maximilian Hoffmann", resolver.called(43));

        memberWithAccount(43, 430, "Max", "Hoffmann");
        assertEquals("Maximilian Hoffmann", resolver.called(43), "what was read is kept");

        resolver.forget(43);
        assertEquals("Max Hoffmann", resolver.called(43), "and read again once it is dropped");
    }

    /** Nothing is kept about a member nobody knows, so naming them later works. */
    @Test
    void aMemberNobodyKnowsIsNotKept() {
        when(memberService.findById(44)).thenReturn(Optional.empty());
        assertNull(resolver.called(44));

        memberWithAccount(44, 440, "Maximilian", "Hoffmann");
        assertEquals("Maximilian Hoffmann", resolver.called(44));
    }
}
