/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunMember;
import dev.chojo.ember.feature.protocol.service.TestProtocolRunService.RunRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static dev.chojo.ember.feature.protocol.service.TestProtocolEvaluationServiceTest.DAY;
import static dev.chojo.ember.feature.protocol.service.TestProtocolEvaluationServiceTest.RUN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestProtocolRunServiceTest {
    private TestProtocolService protocols;
    private TestProtocolPdfService pdfs;
    private StationMemberRepository members;
    private MemberGroupRepository groups;
    private UserTagRepository tags;
    private AccountRepository accounts;
    private TestProtocolRunService service;

    private static StationMember member(int id, int stationId, String name, Integer accountId) {
        return new StationMember(id, stationId, null, accountId, false, null, name, StationUserType.MEMBER, null);
    }

    @BeforeEach
    void setup() {
        protocols = mock(TestProtocolService.class);
        pdfs = mock(TestProtocolPdfService.class);
        members = mock(StationMemberRepository.class);
        groups = mock(MemberGroupRepository.class);
        tags = mock(UserTagRepository.class);
        accounts = mock(AccountRepository.class);
        service = new TestProtocolRunService(protocols, pdfs, members, groups, tags, accounts);
        when(protocols.createRun(anyInt(), anyInt(), any(), any(), anyInt())).thenReturn(RUN);
    }

    @Test
    void aRunIsFilledFromTheNamedMembersKindsGroupsAndTagsOfTheStationOnly() {
        when(members.findByStation(3))
                .thenReturn(List.of(member(1, 3, "a", null), member(2, 3, "b", null), member(4, 3, "d", null)));
        when(members.findByStationAndUserType(3, StationUserType.MEMBER)).thenReturn(List.of(member(2, 3, "b", null)));
        when(groups.findMembers(7)).thenReturn(List.of(member(4, 3, "d", null)));
        when(tags.findMembers(8)).thenReturn(List.of(member(9, 6, "foreign", null)));

        var run = service.start(
                1, 3, 11, new RunRequest("Herbst", DAY, List.of(1), List.of("MEMBER"), List.of(7), List.of(8)));

        assertEquals(RUN, run);
        verify(protocols).createRun(1, 3, "Herbst", DAY, 11);
        verify(protocols).addRunMembers(5, List.of(1, 2, 4));
    }

    @Test
    void aRunNamingNobodyIsStartedTodayAndLeftEmpty() {
        service.start(1, 3, 11, new RunRequest("Herbst", null, null, null, null, null));

        verify(protocols).createRun(eq(1), eq(3), eq("Herbst"), eq(LocalDate.now()), eq(11));
        verify(protocols, never()).addRunMembers(anyInt(), any());
    }

    @Test
    void aSheetIsNamedAfterTheMemberOrTheirOfficialNameOrTheirNumber() {
        when(members.findById(1)).thenReturn(Optional.of(member(1, 3, "Ma/ra", null)));
        when(members.findById(2)).thenReturn(Optional.of(member(2, 3, " ", TestSessions.ACCOUNT_ID)));
        when(accounts.findById(TestSessions.ACCOUNT_ID)).thenReturn(Optional.of(TestSessions.account()));
        when(members.findById(3)).thenReturn(Optional.of(member(3, 3, null, null)));
        when(members.findById(4)).thenReturn(Optional.of(member(4, 3, "", 99)));

        assertEquals("Mara", service.memberFileName(1));
        assertEquals("Mara Nager", service.memberFileName(2));
        assertEquals("Member_3", service.memberFileName(3));
        assertEquals("Member_4", service.memberFileName(4));
        assertEquals("Member_5", service.memberFileName(5));
    }

    @Test
    void theArchiveHoldsTheTableAndEverySheet() throws IOException {
        when(protocols.findRunMembers(5)).thenReturn(List.of(new TestProtocolRunMember(1, 5, 1, null, null, true, 0)));
        when(members.findById(1)).thenReturn(Optional.of(member(1, 3, "Mara", null)));
        when(pdfs.exportEvaluationTable(5, "Knoten", DAY)).thenReturn(new byte[] {1});
        when(pdfs.exportRunMember(5, 1, "Knoten", DAY)).thenReturn(new byte[] {2});

        var names = new ArrayList<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(service.archive(RUN, "Knoten")))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) names.add(entry.getName());
        }

        assertEquals(List.of("Auswertung.pdf", "Mara.pdf"), names);
    }

    @Test
    void anArchiveThatCannotBeMadeIsRefused() {
        when(pdfs.exportEvaluationTable(anyInt(), any(), any())).thenThrow(new IllegalStateException("typst"));

        var refused = assertThrows(RefusalResponse.class, () -> service.archive(RUN, "Knoten"));

        assertEquals(Refusal.PROTOCOL_RUN_NOT_EXPORTED, refused.refusal());
    }
}
