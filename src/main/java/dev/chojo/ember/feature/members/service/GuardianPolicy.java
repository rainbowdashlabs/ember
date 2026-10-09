/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Whom a reader acts for: themselves, and every member they look after.
 *
 * <p>Being put in charge of a member is what makes somebody their guardian, and that relation alone
 * decides. Registering a ward, declining for them, taking their answer back, answering their
 * questions, reading those answers, reading their documents and filling in their forms all ask it
 * here, so a guardian can do on one screen what they can do on the next. The guardian permission is
 * not asked on top: it follows from the relation anyway, and asking both let the places that
 * remembered one of them and forgot the other disagree.
 *
 * <p>A member who has left the station is nobody's ward any more.
 */
@Singleton
public class GuardianPolicy {
    private final StationMemberRepository memberRepository;

    @Inject
    public GuardianPolicy(StationMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * The members the reader looks after, without the reader.
     *
     * @param session the reader
     * @return their wards, none for a reader who is no member of the station
     */
    public List<StationMember> wards(UserSession session) {
        StationMember member = session.member();
        if (member == null) return List.of();
        return wardsOf(member.id());
    }

    /**
     * The members a member looks after, without them, for a caller that knows the member but holds no
     * session of theirs.
     *
     * @param memberId the guardian
     * @return their wards
     */
    public List<StationMember> wardsOf(int memberId) {
        return memberRepository.findManaged(memberId);
    }

    /**
     * The reader and everybody they look after, the reader first.
     *
     * @param session the reader
     * @return the member ids they act for, none for a reader who is no member of the station
     */
    public List<Integer> household(UserSession session) {
        StationMember member = session.member();
        if (member == null) return List.of();
        var ids = new ArrayList<Integer>();
        ids.add(member.id());
        wards(session).forEach(ward -> ids.add(ward.id()));
        return ids;
    }

    /**
     * Whether the reader acts for this member: it is themselves, or somebody in their care.
     *
     * @param session  the reader
     * @param memberId the member acted for
     * @return whether they may
     */
    public boolean mayActFor(UserSession session, int memberId) {
        StationMember member = session.member();
        if (member == null) return false;
        if (member.id() == memberId) return true;
        return wards(session).stream().anyMatch(ward -> ward.id() == memberId);
    }
}
