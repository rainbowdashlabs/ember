/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who may decide what somebody is called.
 */
class NicknameServiceTest {

    private static final int MEMBER = 10;
    private static final int GUARDIAN = 11;
    private static final int STRANGER = 12;

    private StationMemberRepository memberRepository;
    private StationMemberService memberService;
    private MemberNameResolver nameResolver;
    private NicknameService service;

    @BeforeEach
    void setup() {
        memberRepository = mock(StationMemberRepository.class);
        memberService = mock(StationMemberService.class);
        nameResolver = mock(MemberNameResolver.class);
        service = new NicknameService(memberRepository, memberService, nameResolver);

        when(memberRepository.findById(anyInt())).thenReturn(Optional.of(member(MEMBER)));
        when(memberService.findManaged(GUARDIAN)).thenReturn(List.of(member(MEMBER)));
        when(memberService.findManaged(STRANGER)).thenReturn(List.of());
    }

    private static StationMember member(int id) {
        return new StationMember(id, 1, UUID.randomUUID(), id * 10, false, null, null, StationUserType.MEMBER, null);
    }

    /** A member decides what they are called. */
    @Test
    void aMemberMaySetTheirOwn() {
        service.set(MEMBER, "Max", MEMBER);

        verify(memberRepository).setNickname(MEMBER, "Max", MEMBER);
        verify(nameResolver).forget(MEMBER);
    }

    /** So does whoever looks after them, which is how a child's guardian keeps their profile. */
    @Test
    void whoeverLooksAfterThemMayToo() {
        service.set(MEMBER, "Max", GUARDIAN);

        verify(memberRepository).setNickname(MEMBER, "Max", GUARDIAN);
    }

    /**
     * Nobody else may, however senior.
     *
     * <p>A nickname somebody else can impose is how this turns into a way to label people, so the
     * refusal does not depend on what rights the person holds over members in general.
     */
    @Test
    void nobodyElseMay() {
        assertThrows(ForbiddenResponse.class, () -> service.set(MEMBER, "Kleiner", STRANGER));
        verify(memberRepository, never()).setNickname(anyInt(), org.mockito.ArgumentMatchers.any(), anyInt());
        assertFalse(service.mayWrite(MEMBER, STRANGER));
        assertTrue(service.mayWrite(MEMBER, GUARDIAN));
    }

    /** Clearing it gives somebody their register name back. */
    @Test
    void aBlankNameClearsIt() {
        service.set(MEMBER, "   ", MEMBER);

        verify(memberRepository).setNickname(eq(MEMBER), isNull(), eq(MEMBER));
    }

    /** A name is a name, not a paragraph and not two lines. */
    @Test
    void aNameIsBoundedAndIsOneLine() {
        assertThrows(BadRequestResponse.class, () -> service.set(MEMBER, "x".repeat(61), MEMBER));
        assertThrows(BadRequestResponse.class, () -> service.set(MEMBER, "Max\nMustermann", MEMBER));
    }
}
