/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.StationAudienceChanged;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AccountReach;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The writers that change who belongs to a station's people say so, so the station's chats follow.
 * Each test watches the bus one writer publishes on.
 */
class StationAudienceEventsTest extends RepositoryTestBase {
    private DomainEventBus bus;
    private Station station;
    private StationMember member;
    private Account account;

    @BeforeEach
    void setUp() {
        bus = mock(DomainEventBus.class);
        station = stationRepo.create("Audience " + System.nanoTime());
        account = accountRepo.create("audience-" + System.nanoTime() + "@test.com", "Au", "Dience");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @Test
    void settingTheMembersOfATagSaysSo() {
        var tag = userTagRepo.create(station.id(), "Atemschutz");

        new UserTagService(userTagRepo, memberGroupRepo, bus).setMembers(tag.id(), List.of(member.id()));

        verify(bus).publish(StationAudienceChanged.at(station.id()));
    }

    @Test
    void deletingATagSaysSo() {
        var tag = userTagRepo.create(station.id(), "Atemschutz");

        new UserTagService(userTagRepo, memberGroupRepo, bus).delete(tag.id());

        verify(bus).publish(StationAudienceChanged.at(station.id()));
    }

    @Test
    void puttingSomebodyIntoAGroupOnTheirOwnSaysSo() {
        var group = memberGroupRepo.create(station.id(), "Jugend");
        var memberships = new GroupMembershipService(
                memberGroupRepo, stationMemberRepo, mock(StepUpGuard.class), () -> mock(MemberNameResolver.class), bus);

        memberships.joinAutomatically(group.id(), member.id());
        memberships.leaveAutomatically(group.id(), member.id());

        verify(bus, atLeastOnce()).publish(StationAudienceChanged.at(station.id()));
    }

    @Test
    void deletingAGroupSaysSo() {
        var group = memberGroupRepo.create(station.id(), "Jugend");
        var groups =
                new MemberGroupService(memberGroupRepo, stationMemberRepo, userTagRepo, newGroupMemberships(), bus);

        groups.delete(group, mock(UserSession.class));

        verify(bus).publish(StationAudienceChanged.at(station.id()));
    }

    @Test
    void changingTheTypeOfAMemberSaysSo() {
        new UserTypeChangeService(stationMemberRepo, newGroupMemberships(), bus)
                .change(member.id(), StationUserType.TRIAL);

        verify(bus).publish(StationAudienceChanged.at(station.id()));
    }

    @Test
    void creatingAndDeletingAMemberSaysSo() {
        var service = memberService();
        var other = accountRepo.create("audience-other-" + System.nanoTime() + "@test.com", "Ot", "Her");

        var created = service.create(station.id(), other.id());
        service.delete(created.id());

        verify(bus, times(2)).publish(StationAudienceChanged.at(station.id()));
    }

    @Test
    void linkingAGuardianSaysSoForTheStationAndTheMembersAccount() {
        var guardianAccount = accountRepo.create("audience-guardian-" + System.nanoTime() + "@test.com", "Gu", "Ard");
        var guardian = stationMemberRepo.create(station.id(), guardianAccount.id());

        memberService().setManagers(member.id(), List.of(guardian.id()), null);

        verify(bus).publish(StationAudienceChanged.at(station.id()));
        verify(bus).publish(StationAudienceChanged.ofAccount(account.id()));
    }

    @Test
    void archivingAMemberSaysSoForTheStationAndEveryLinkedAccount() {
        var guardianAccount = accountRepo.create("audience-g-" + System.nanoTime() + "@test.com", "Gu", "Ard");
        var guardian = stationMemberRepo.create(station.id(), guardianAccount.id());
        stationMemberRepo.addManager(guardian.id(), member.id());

        new FormerMemberService(
                        stationMemberRepo,
                        accountRepo,
                        inventoryRepo,
                        itemMovementService,
                        memberGroupRepo,
                        userTagRepo,
                        attendanceRepo,
                        profileFieldCore,
                        mock(DocumentService.class),
                        selfCheckService,
                        bus)
                .markFormer(guardian.id());

        verify(bus).publish(StationAudienceChanged.at(station.id()));
        verify(bus).publish(StationAudienceChanged.ofAccount(account.id()));
        verify(bus).publish(StationAudienceChanged.ofAccount(guardianAccount.id()));
    }

    @Test
    void replacingTheGroupsOfAMemberSaysSoOnlyWhenTheyChange() {
        var group = memberGroupRepo.create(station.id(), "Jugend");
        var memberships = membershipsOnBus();
        var by = stationSession(member, StationPermission.MEMBER_MANAGER);

        memberships.replaceGroupsOfMember(member, List.of(group.id()), by);
        memberships.replaceGroupsOfMember(member, List.of(group.id()), by);

        verify(bus, times(1)).publish(StationAudienceChanged.at(station.id()));
    }

    @Test
    void settingTheMembersOfAGroupSaysSoOnlyWhenTheyChange() {
        var group = memberGroupRepo.create(station.id(), "Jugend");
        var memberships = membershipsOnBus();
        var by = stationSession(member, StationPermission.MEMBER_MANAGER);

        memberships.setMembers(group, List.of(member.id()), false, by);
        memberships.setMembers(group, List.of(member.id()), false, by);

        verify(bus, times(1)).publish(StationAudienceChanged.at(station.id()));
    }

    private GroupMembershipService membershipsOnBus() {
        return new GroupMembershipService(
                memberGroupRepo, stationMemberRepo, mock(StepUpGuard.class), () -> mock(MemberNameResolver.class), bus);
    }

    private StationMemberService memberService() {
        return new StationMemberService(
                stationMemberRepo,
                stationRepo,
                accountRepo,
                mock(AuthService.class),
                memberLookupService,
                mock(DocumentService.class),
                new AccountReach(accountRepo),
                new ModuleAvailability(stationRepo, clusterRepo),
                bus);
    }
}
