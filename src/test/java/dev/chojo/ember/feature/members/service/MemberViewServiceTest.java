/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.RichMember;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberViewServiceTest {
    private static final int STATION_ID = 3;

    private AccountRepository accounts;
    private StationMemberRepository members;
    private ProfileFieldService profileFields;
    private MemberViewService service;

    private static StationMember member(Integer accountId) {
        return new StationMember(
                7, STATION_ID, null, accountId, false, null, "Mara", StationUserType.MEMBER, LocalDate.of(2020, 1, 1));
    }

    private static RichMember richMember() {
        return new RichMember(
                7,
                STATION_ID,
                null,
                1,
                "Mara Nager",
                "Mara",
                "Nager",
                null,
                "mara@test.com",
                false,
                null,
                null,
                false,
                StationUserType.MEMBER,
                null,
                List.of(),
                List.of(),
                List.of(),
                Map.of("5", "readable", "6", "hidden"),
                true,
                null);
    }

    @BeforeEach
    void setup() {
        accounts = mock(AccountRepository.class);
        members = mock(StationMemberRepository.class);
        profileFields = mock(ProfileFieldService.class);
        var names = mock(MemberNameResolver.class);
        when(names.identified(anyInt())).thenReturn("Mara Nager");
        when(accounts.findById(1)).thenReturn(Optional.of(TestSessions.account()));
        service = new MemberViewService(accounts, members, mock(MemberIdentityFactory.class), names, profileFields);
    }

    @Test
    void aNamedMemberCarriesItsAccountAndCountsAsComplete() {
        var named = service.named(member(1));

        assertEquals("Mara Nager", named.name());
        assertEquals("mara@test.com", named.email());
        assertTrue(named.profileComplete());
    }

    @Test
    void completenessIsAskedOfTheProfileQuestions() {
        when(profileFields.isProfileComplete(7)).thenReturn(false);

        var named = service.withCompleteness(List.of(member(null)));

        assertFalse(named.getFirst().profileComplete());
        assertEquals("Mara", named.getFirst().name());
    }

    @Test
    void theRegisterOnlyCarriesTheAnswersTheReaderMayRead() {
        when(members.findRichMembers(STATION_ID, true)).thenReturn(List.of(richMember()));
        when(profileFields.findReadableBy(any(Integer.class), any()))
                .thenReturn(List.of(
                        new ProfileField(5, STATION_ID, "Größe", FieldType.TEXT, null, false, false, null, false)));

        var rows = service.richMembers(STATION_ID, true, Set.of(StationPermission.MEMBER_READ));

        assertEquals(Map.of("5", "readable"), rows.getFirst().profileValues());
    }
}
