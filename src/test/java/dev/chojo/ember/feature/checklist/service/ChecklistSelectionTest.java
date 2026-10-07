/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.checklist.entity.ChecklistEntry;
import dev.chojo.ember.feature.checklist.repository.ChecklistRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.PrivateTags;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which of the members and rows a request names a checklist change may act on.
 */
class ChecklistSelectionTest {
    private final ChecklistRepository repository = mock(ChecklistRepository.class);
    private final StationMemberRepository members = mock(StationMemberRepository.class);
    private final ChecklistService service = new ChecklistService(
            repository,
            members,
            mock(MemberGroupRepository.class),
            mock(UserTagRepository.class),
            mock(EventRegistrationRepository.class),
            mock(PrivateTags.class));

    private static StationMember member(int id) {
        return new StationMember(id, 3, null, null, false, null, "M" + id, StationUserType.MEMBER, LocalDate.EPOCH);
    }

    @Test
    void onlyMembersOfTheStationAreKeptEachOnceInTheOrderNamed() {
        when(members.findByStation(3)).thenReturn(List.of(member(1), member(2), member(3)));

        assertEquals(List.of(3, 1), service.membersOfStation(List.of(3, 9, 1, 3), 3));
    }

    @Test
    void onlyRowsOfTheChecklistAreKeptRemovedOnesIncluded() {
        when(repository.findEntries(5, true))
                .thenReturn(
                        List.of(new ChecklistEntry(10, 5, 1, null, null), new ChecklistEntry(11, 5, 2, null, null)));

        assertEquals(List.of(11, 10), service.rowsOfChecklist(List.of(11, 12, 10, 11), 5));
    }
}
