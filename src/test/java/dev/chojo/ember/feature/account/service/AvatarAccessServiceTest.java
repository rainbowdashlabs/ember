/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvatarAccessServiceTest {
    private static final UUID CALLER_UID = UUID.randomUUID();
    private static final UUID TARGET_UID = UUID.randomUUID();
    private static final UUID STATION_UID = UUID.randomUUID();
    private static final UUID MEMBER_UID = UUID.randomUUID();

    private AccountRepository accountRepository;
    private StationMemberRepository stationMemberRepository;
    private StationRepository stationRepository;
    private FederationRepository federationRepository;
    private AvatarAccessService service;

    private static Account account(int id, UUID uid, InstanceUserType type) {
        return new Account(
                id, uid, "user" + id + "@ember.local", null, "First", "Last", true, type, "First Last", null, null);
    }

    private static StationMember member(int id, int stationId, Integer accountId) {
        return new StationMember(id, stationId, MEMBER_UID, accountId, false, null, null, StationUserType.MEMBER, null);
    }

    private static UserSession session(Account account, Integer stationId) {
        return new UserSession(account, 1, stationId, null, null, Set.of(), Set.of(), null);
    }

    private static FederationPartner partner(FederationPartner.FederationStatus status) {
        return new FederationPartner(
                1, 1, TARGET_UID, "code", "pub", "partnerPub", status, null, Instant.now(), Instant.now(), null, "P");
    }

    @BeforeEach
    void setup() {
        accountRepository = mock(AccountRepository.class);
        stationMemberRepository = mock(StationMemberRepository.class);
        stationRepository = mock(StationRepository.class);
        federationRepository = mock(FederationRepository.class);
        service = new AvatarAccessService(
                accountRepository, stationMemberRepository, stationRepository, federationRepository);
    }

    @Test
    void ownAvatarUid_withoutAccount_isEmpty() {
        assertTrue(service.ownAvatarUid(session(null, 1)).isEmpty());
    }

    @Test
    void ownAvatarUid_resolvesAccountUid() {
        when(accountRepository.resolveUid(1)).thenReturn(CALLER_UID);

        assertEquals(
                Optional.of(CALLER_UID),
                service.ownAvatarUid(session(account(1, CALLER_UID, InstanceUserType.USER), 1)));
    }

    @Test
    void accountAvatarUid_unknownAccount_isEmpty() {
        when(accountRepository.findByUid(TARGET_UID)).thenReturn(Optional.empty());

        assertTrue(service.accountAvatarUid(session(account(1, CALLER_UID, InstanceUserType.USER), 1), TARGET_UID)
                .isEmpty());
    }

    @Test
    void accountAvatarUid_ownAccount_isVisible() {
        when(accountRepository.findByUid(TARGET_UID))
                .thenReturn(Optional.of(account(1, TARGET_UID, InstanceUserType.USER)));

        assertEquals(
                Optional.of(TARGET_UID),
                service.accountAvatarUid(session(account(1, CALLER_UID, InstanceUserType.USER), 1), TARGET_UID));
    }

    @Test
    void accountAvatarUid_instanceAdministrator_isVisible() {
        when(accountRepository.findByUid(TARGET_UID))
                .thenReturn(Optional.of(account(2, TARGET_UID, InstanceUserType.USER)));

        assertEquals(
                Optional.of(TARGET_UID),
                service.accountAvatarUid(
                        session(account(1, CALLER_UID, InstanceUserType.ADMINISTRATOR), 1), TARGET_UID));
    }

    @Test
    void accountAvatarUid_targetWithoutMemberships_isEmpty() {
        when(accountRepository.findByUid(TARGET_UID))
                .thenReturn(Optional.of(account(2, TARGET_UID, InstanceUserType.USER)));
        when(stationMemberRepository.findByAccount(2)).thenReturn(List.of());

        assertTrue(service.accountAvatarUid(session(account(1, CALLER_UID, InstanceUserType.USER), 1), TARGET_UID)
                .isEmpty());
    }

    @Test
    void accountAvatarUid_sharedStation_isVisible() {
        when(accountRepository.findByUid(TARGET_UID))
                .thenReturn(Optional.of(account(2, TARGET_UID, InstanceUserType.USER)));
        when(stationMemberRepository.findByAccount(2)).thenReturn(List.of(member(20, 7, 2)));
        when(stationMemberRepository.findByAccount(1)).thenReturn(List.of(member(10, 7, 1)));

        assertEquals(
                Optional.of(TARGET_UID),
                service.accountAvatarUid(session(account(1, CALLER_UID, InstanceUserType.USER), 1), TARGET_UID));
    }

    @Test
    void accountAvatarUid_withoutSelectedStation_isEmpty() {
        when(accountRepository.findByUid(TARGET_UID))
                .thenReturn(Optional.of(account(2, TARGET_UID, InstanceUserType.USER)));
        when(stationMemberRepository.findByAccount(2)).thenReturn(List.of(member(20, 7, 2)));
        when(stationMemberRepository.findByAccount(1)).thenReturn(List.of(member(10, 8, 1)));

        assertTrue(service.accountAvatarUid(session(account(1, CALLER_UID, InstanceUserType.USER), null), TARGET_UID)
                .isEmpty());
    }

    @Test
    void accountAvatarUid_activeFederationPartner_isVisible() {
        when(accountRepository.findByUid(TARGET_UID))
                .thenReturn(Optional.of(account(2, TARGET_UID, InstanceUserType.USER)));
        when(stationMemberRepository.findByAccount(2)).thenReturn(List.of(member(20, 7, 2)));
        when(stationMemberRepository.findByAccount(1)).thenReturn(List.of(member(10, 8, 1)));
        when(stationRepository.resolveUid(7)).thenReturn(TARGET_UID);
        when(federationRepository.findPartnerByStationAndRemoteUid(8, TARGET_UID))
                .thenReturn(Optional.of(partner(FederationPartner.FederationStatus.ACTIVE)));

        assertEquals(
                Optional.of(TARGET_UID),
                service.accountAvatarUid(session(account(1, CALLER_UID, InstanceUserType.USER), 8), TARGET_UID));
    }

    @Test
    void accountAvatarUid_unresolvableStationUid_isEmpty() {
        when(accountRepository.findByUid(TARGET_UID))
                .thenReturn(Optional.of(account(2, TARGET_UID, InstanceUserType.USER)));
        when(stationMemberRepository.findByAccount(2)).thenReturn(List.of(member(20, 7, 2)));
        when(stationMemberRepository.findByAccount(1)).thenReturn(List.of(member(10, 8, 1)));
        when(stationRepository.resolveUid(7)).thenReturn(null);

        assertTrue(service.accountAvatarUid(session(account(1, CALLER_UID, InstanceUserType.USER), 8), TARGET_UID)
                .isEmpty());
    }

    @Test
    void accountAvatarUid_suspendedFederationPartner_isEmpty() {
        when(accountRepository.findByUid(TARGET_UID))
                .thenReturn(Optional.of(account(2, TARGET_UID, InstanceUserType.USER)));
        when(stationMemberRepository.findByAccount(2)).thenReturn(List.of(member(20, 7, 2)));
        when(stationMemberRepository.findByAccount(1)).thenReturn(List.of(member(10, 8, 1)));
        when(stationRepository.resolveUid(7)).thenReturn(TARGET_UID);
        when(federationRepository.findPartnerByStationAndRemoteUid(8, TARGET_UID))
                .thenReturn(Optional.of(partner(FederationPartner.FederationStatus.SUSPENDED)));

        assertTrue(service.accountAvatarUid(session(account(1, CALLER_UID, InstanceUserType.USER), 8), TARGET_UID)
                .isEmpty());
    }

    @Test
    void memberAvatarUid_unknownStation_isEmpty() {
        when(stationRepository.findByUid(STATION_UID)).thenReturn(Optional.empty());

        assertTrue(service.memberAvatarUid(
                        session(account(1, CALLER_UID, InstanceUserType.USER), 1), STATION_UID, MEMBER_UID)
                .isEmpty());
    }

    @Test
    void memberAvatarUid_unknownMember_isEmpty() {
        var station = mock(Station.class);
        when(station.id()).thenReturn(7);
        when(stationRepository.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(stationMemberRepository.findByUid(7, MEMBER_UID)).thenReturn(Optional.empty());

        assertTrue(service.memberAvatarUid(
                        session(account(1, CALLER_UID, InstanceUserType.USER), 1), STATION_UID, MEMBER_UID)
                .isEmpty());
    }

    @Test
    void memberAvatarUid_callerIsMemberOfStation_resolvesAccountUid() {
        var station = mock(Station.class);
        when(station.id()).thenReturn(7);
        when(stationRepository.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(stationMemberRepository.findByUid(7, MEMBER_UID)).thenReturn(Optional.of(member(20, 7, 2)));
        when(stationMemberRepository.findByStationAndAccount(7, 1)).thenReturn(Optional.of(member(10, 7, 1)));
        when(accountRepository.resolveUid(2)).thenReturn(TARGET_UID);

        assertEquals(
                Optional.of(TARGET_UID),
                service.memberAvatarUid(
                        session(account(1, CALLER_UID, InstanceUserType.USER), 1), STATION_UID, MEMBER_UID));
    }

    @Test
    void memberAvatarUid_memberWithoutAccount_isEmpty() {
        var station = mock(Station.class);
        when(station.id()).thenReturn(7);
        when(stationRepository.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(stationMemberRepository.findByUid(7, MEMBER_UID)).thenReturn(Optional.of(member(20, 7, null)));
        when(stationMemberRepository.findByStationAndAccount(7, 1)).thenReturn(Optional.of(member(10, 7, 1)));

        assertTrue(service.memberAvatarUid(
                        session(account(1, CALLER_UID, InstanceUserType.USER), 1), STATION_UID, MEMBER_UID)
                .isEmpty());
    }

    @Test
    void memberAvatarUid_instanceAdministrator_resolvesAccountUid() {
        var station = mock(Station.class);
        when(station.id()).thenReturn(7);
        when(stationRepository.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(stationMemberRepository.findByUid(7, MEMBER_UID)).thenReturn(Optional.of(member(20, 7, 2)));
        when(accountRepository.resolveUid(2)).thenReturn(TARGET_UID);

        assertEquals(
                Optional.of(TARGET_UID),
                service.memberAvatarUid(
                        session(account(1, CALLER_UID, InstanceUserType.ADMINISTRATOR), 1), STATION_UID, MEMBER_UID));
    }

    @Test
    void memberAvatarUid_foreignStationWithoutSession_isEmpty() {
        var station = mock(Station.class);
        when(station.id()).thenReturn(7);
        when(stationRepository.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(stationMemberRepository.findByUid(7, MEMBER_UID)).thenReturn(Optional.of(member(20, 7, 2)));
        when(stationMemberRepository.findByStationAndAccount(7, 1)).thenReturn(Optional.empty());

        assertTrue(service.memberAvatarUid(
                        session(account(1, CALLER_UID, InstanceUserType.USER), null), STATION_UID, MEMBER_UID)
                .isEmpty());
    }

    @Test
    void memberAvatarUid_activeFederationPartner_resolvesAccountUid() {
        var station = mock(Station.class);
        when(station.id()).thenReturn(7);
        when(stationRepository.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(stationMemberRepository.findByUid(7, MEMBER_UID)).thenReturn(Optional.of(member(20, 7, 2)));
        when(stationMemberRepository.findByStationAndAccount(7, 1)).thenReturn(Optional.empty());
        when(stationRepository.resolveUid(7)).thenReturn(TARGET_UID);
        when(federationRepository.findPartnerByStationAndRemoteUid(8, TARGET_UID))
                .thenReturn(Optional.of(partner(FederationPartner.FederationStatus.ACTIVE)));
        when(accountRepository.resolveUid(2)).thenReturn(TARGET_UID);

        assertEquals(
                Optional.of(TARGET_UID),
                service.memberAvatarUid(
                        session(account(1, CALLER_UID, InstanceUserType.USER), 8), STATION_UID, MEMBER_UID));
    }

    @Test
    void memberAvatarUid_unknownTargetUid_isEmpty() {
        var station = mock(Station.class);
        when(station.id()).thenReturn(7);
        when(stationRepository.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(stationMemberRepository.findByUid(7, MEMBER_UID)).thenReturn(Optional.of(member(20, 7, 2)));
        when(stationMemberRepository.findByStationAndAccount(7, 1)).thenReturn(Optional.empty());
        when(stationRepository.resolveUid(7)).thenReturn(null);

        assertTrue(service.memberAvatarUid(
                        session(account(1, CALLER_UID, InstanceUserType.USER), 8), STATION_UID, MEMBER_UID)
                .isEmpty());
    }

    @Test
    void memberAvatarUid_withoutAccountInSession_isEmpty() {
        var station = mock(Station.class);
        when(station.id()).thenReturn(7);
        when(stationRepository.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(stationMemberRepository.findByUid(7, MEMBER_UID)).thenReturn(Optional.of(member(20, 7, 2)));

        assertTrue(service.memberAvatarUid(session(null, 1), STATION_UID, MEMBER_UID)
                .isEmpty());
    }
}
