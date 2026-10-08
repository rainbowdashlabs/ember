/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.question.MemberEligibility;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Who of a station's members is in which group, of which user type and carrying which tag, as the
 * one check asks it of a field narrowed to one of them.
 */
@Singleton
public class StationMemberEligibility implements MemberEligibility {

    private final MemberGroupRepository groupRepository;
    private final StationMemberRepository memberRepository;
    private final UserTagRepository tagRepository;

    @Inject
    public StationMemberEligibility(
            MemberGroupRepository groupRepository,
            StationMemberRepository memberRepository,
            UserTagRepository tagRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.tagRepository = tagRepository;
    }

    @Override
    public boolean inGroup(int memberId, int groupId) {
        return groupRepository.findGroupsForMember(memberId).stream().anyMatch(group -> group.id() == groupId);
    }

    @Override
    public boolean ofType(int memberId, StationUserType userType) {
        return memberRepository
                .findById(memberId)
                .map(member -> member.userType() == userType)
                .orElse(false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A private tag lets nobody through. Answering for it would tell whoever fills in the field
     * who carries it, one member at a time.
     */
    @Override
    public boolean hasTag(int memberId, int tagId) {
        return tagRepository.findTagsForMember(memberId).stream()
                .anyMatch(tag -> tag.id() == tagId && !tag.visibility().restricted());
    }
}
