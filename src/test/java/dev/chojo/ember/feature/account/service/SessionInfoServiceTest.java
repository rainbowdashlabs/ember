/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.File;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserSettings;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserSettingsRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionInfoServiceTest {
    private static final UUID ACCOUNT_UID = UUID.randomUUID();
    private static final UUID STATION_UID = UUID.randomUUID();
    private static final UUID MEMBER_UID = UUID.randomUUID();

    private StationService stationService;
    private StationMemberService memberService;
    private MemberGroupService groupService;
    private ProfileFieldService profileFieldService;
    private AccountRepository accountRepository;
    private StationMemberRepository stationMemberRepository;
    private UserSettingsRepository userSettingsRepository;
    private UserTagRepository userTagRepository;
    private SessionInfoService service;

    private static Account account(int id, String first, String last) {
        return new Account(
                id, ACCOUNT_UID, "user@ember.local", first, last, true, InstanceUserType.USER, "full", null, null);
    }

    private static StationMember member(int id, int stationId, Integer accountId, String displayName) {
        return new StationMember(
                id, stationId, MEMBER_UID, accountId, false, null, displayName, StationUserType.MEMBER, null);
    }

    @BeforeEach
    void setup() {
        stationService = mock(StationService.class);
        memberService = mock(StationMemberService.class);
        groupService = mock(MemberGroupService.class);
        profileFieldService = mock(ProfileFieldService.class);
        accountRepository = mock(AccountRepository.class);
        stationMemberRepository = mock(StationMemberRepository.class);
        userSettingsRepository = mock(UserSettingsRepository.class);
        userTagRepository = mock(UserTagRepository.class);
        service = new SessionInfoService(
                stationService,
                memberService,
                groupService,
                profileFieldService,
                accountRepository,
                stationMemberRepository,
                userSettingsRepository,
                userTagRepository,
                new File());
    }

    @Test
    void describe_withoutMember_fallsBackToInstanceTheme() {
        var session = new UserSession(account(1, "Max", "Meier"), 1, null, null, null, Set.of(), Set.of(), null);

        var info = service.describe(session);

        assertNull(info.member());
        assertNull(info.stationId());
        assertTrue(info.managedMembers().isEmpty());
        assertTrue(info.groups().isEmpty());
        assertTrue(info.tags().isEmpty());
        assertTrue(info.disabledModules().isEmpty());
        assertTrue(info.profileComplete());
        assertNull(info.publicKbMode());
        assertNull(info.setupCompletedAt());
        assertEquals("ember", info.theme().defaultTheme());
        assertEquals(ThemeFeel.ROUNDED, info.theme().defaultFeel());
        assertEquals(ACCOUNT_UID.toString(), info.account().uid());
    }

    @Test
    void describe_withMemberAndStation_resolvesEverything() {
        var member = member(5, 7, 1, null);
        var session = new UserSession(
                account(1, "Max", "Meier"), 1, 7, STATION_UID, member, Set.of(StationPermission.LOGIN), Set.of(), null);
        var station = mock(Station.class);
        when(station.defaultTheme()).thenReturn("forest");
        when(station.defaultFeel()).thenReturn(ThemeFeel.CORNERS);
        when(station.allowUserTheme()).thenReturn(false);
        when(station.allowUserFeel()).thenReturn(false);
        when(station.customThemeColors()).thenReturn("{}");
        when(station.uid()).thenReturn(STATION_UID);
        when(stationService.findById(7)).thenReturn(Optional.of(station));
        when(stationService.findDisabledModules(7)).thenReturn(Set.of(StationModule.EVENTS));
        when(memberService.findManaged(5)).thenReturn(List.of(member(6, 7, 2, null), member(8, 7, null, "Kid")));
        when(groupService.findGroupsForMember(5)).thenReturn(List.of(new MemberGroup(3, 7, "Group", "#fff", 0)));
        when(userTagRepository.findTagsForMember(5)).thenReturn(List.of(new UserTag(4, 7, "Tag", "#000", true, 0)));
        when(stationMemberRepository.findPermissions(5))
                .thenReturn(List.of(new Permission(9, StationPermission.LOGIN)));
        when(profileFieldService.isProfileComplete(anyInt(), anyInt(), anyList()))
                .thenReturn(false);
        when(userSettingsRepository.findOrCreate(5)).thenReturn(new UserSettings(5, true, "ocean", "dark", "ROUNDED"));
        when(accountRepository.findById(2)).thenReturn(Optional.of(account(2, "Erika", "Musterfrau")));

        var info = service.describe(session);

        assertEquals(STATION_UID.toString(), info.stationId());
        assertEquals(5, info.member().id());
        assertEquals(List.of("LOGIN"), info.permissions());
        assertEquals(List.of(9), info.roleIds());
        assertEquals(List.of(3), info.groupIds());
        assertEquals(List.of(4), info.tagIds());
        assertEquals(Set.of(StationModule.EVENTS), info.disabledModules());
        assertEquals(2, info.managedMembers().size());
        assertEquals("Erika Musterfrau", info.managedMembers().getFirst().name());
        assertEquals("Kid", info.managedMembers().get(1).name());
        assertEquals("", info.managedMembers().get(1).email());
        assertEquals(0, info.managedMembers().get(1).accountId());
        assertEquals("forest", info.theme().defaultTheme());
        assertEquals("ocean", info.theme().userTheme());
        assertEquals("dark", info.theme().userDarkMode());
        assertEquals(ThemeFeel.CORNERS, info.theme().defaultFeel());
    }

    @Test
    void describe_withMemberButMissingStation_fallsBackToInstanceTheme() {
        var session = new UserSession(
                account(1, "Max", "Meier"), 1, 7, STATION_UID, member(5, 7, 1, null), Set.of(), Set.of(), null);
        when(stationService.findById(7)).thenReturn(Optional.empty());
        when(memberService.findManaged(5)).thenReturn(List.of());
        when(groupService.findGroupsForMember(5)).thenReturn(List.of());
        when(userTagRepository.findTagsForMember(5)).thenReturn(List.of());
        when(stationMemberRepository.findPermissions(5)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(anyInt(), anyInt(), anyList()))
                .thenReturn(true);
        when(userSettingsRepository.findOrCreate(5)).thenReturn(new UserSettings(5, true, null, null, null));

        var info = service.describe(session);

        assertEquals("ember", info.theme().defaultTheme());
        assertNull(info.publicKbMode());
        assertTrue(info.profileComplete());
    }
}
