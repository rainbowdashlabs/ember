/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository.PickerMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberPickerServiceTest {
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private StationMemberRepository members;
    private AvatarService avatars;
    private MemberPickerService service;

    private static PickerMember picker(UUID accountUid, StationUserType type) {
        return new PickerMember(MEMBER, accountUid, "Mara", type, "#fff", "Jugend", "#000", null);
    }

    @BeforeEach
    void setup() {
        members = mock(StationMemberRepository.class);
        avatars = mock(AvatarService.class);
        service = new MemberPickerService(members, avatars);
    }

    @Test
    void aSearchIsHeldToTwentyAndInlinesTheAvatar() {
        when(members.searchForPicker(3, "ma", 20)).thenReturn(List.of(picker(ACCOUNT, StationUserType.MEMBER)));
        when(avatars.read(ACCOUNT, 64)).thenReturn(Optional.of(new MediaContent(new byte[] {1}, "image/png")));

        var found = service.search(3, "ma", null, 500);

        assertEquals("data:image/png;base64,AQ==", found.getFirst().avatarUrl());
        assertEquals("MEMBER", found.getFirst().userType());
        assertEquals("Jugend", found.getFirst().displayTag());
    }

    @Test
    void aMemberWithoutAccountOrTypeHasNeither() {
        when(members.searchForPicker(3, null, 1)).thenReturn(List.of(picker(null, null)));

        var found = service.search(3, null, " ", 0);

        assertNull(found.getFirst().avatarUrl());
        assertNull(found.getFirst().userType());
    }

    @Test
    void aUidWinsOverTheSearch() {
        when(members.findPickerByUid(3, MEMBER)).thenReturn(Optional.of(picker(ACCOUNT, StationUserType.TEAM)));

        assertEquals(
                MEMBER,
                service.search(3, "ma", MEMBER.toString(), 20).getFirst().memberUid());
        verify(members, never()).searchForPicker(anyInt(), any(), anyInt());
    }

    @Test
    void aUidThatIsNotOneOrNamesNobodyFindsNobody() {
        assertTrue(service.search(3, null, "not-a-uid", 20).isEmpty());
        when(members.findPickerByUid(3, MEMBER)).thenReturn(Optional.empty());
        assertTrue(service.search(3, null, MEMBER.toString(), 20).isEmpty());
    }
}
