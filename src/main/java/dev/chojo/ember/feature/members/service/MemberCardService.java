/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.feature.members.entity.MemberCard;
import dev.chojo.ember.feature.members.entity.MemberCard.MemberCardLabel;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puts together the card shown when somebody looks at a member's name.
 *
 * <p>The card is read by anybody at the station, so it carries only what that audience already
 * knows: the name, the picture, who belongs to whom, and the tags and groups. It never reaches a
 * member of another station.
 */
@Singleton
public class MemberCardService {
    private final StationMemberService memberService;
    private final MemberNameResolver nameResolver;
    private final MemberIdentityFactory identityFactory;
    private final UserTagService tagService;
    private final MemberGroupService groupService;
    private final PrivateTags privateTags;

    @Inject
    public MemberCardService(
            StationMemberService memberService,
            MemberNameResolver nameResolver,
            MemberIdentityFactory identityFactory,
            UserTagService tagService,
            MemberGroupService groupService,
            PrivateTags privateTags) {
        this.memberService = memberService;
        this.nameResolver = nameResolver;
        this.identityFactory = identityFactory;
        this.tagService = tagService;
        this.groupService = groupService;
        this.privateTags = privateTags;
    }

    /**
     * The card of a member of the reader's station. Private tags are on it only for a reader allowed
     * to view members.
     *
     * @param reader    the reader, whose station the member has to be at
     * @param memberUid the member looked at
     * @return the card, or empty where the uid names nobody at this station
     */
    public Optional<MemberCard> find(StationSession reader, UUID memberUid) {
        int stationId = reader.stationId();
        return memberService
                .resolveId(stationId, memberUid)
                .flatMap(memberService::findById)
                .filter(member -> member.stationId() == stationId)
                .map(member -> cardOf(reader, member));
    }

    private MemberCard cardOf(StationSession reader, StationMember member) {
        UUID stationUid = reader.stationUid();
        var identity = identityOf(stationUid, member);
        var name = Objects.requireNonNullElse(nameResolver.identified(member.id()), member.displayName());
        if (member.former()) {
            return new MemberCard(identity, name, true, List.of(), List.of(), List.of(), List.of());
        }
        return new MemberCard(
                identity,
                name,
                false,
                identitiesOf(stationUid, memberService.findManagers(member.id())),
                identitiesOf(stationUid, memberService.findManaged(member.id())),
                tagsOf(reader, member.id()),
                groupsOf(member.id()));
    }

    private MemberIdentity identityOf(UUID stationUid, StationMember member) {
        return identityFactory.enrich(new MemberIdentity(stationUid, member.uid()));
    }

    private List<MemberIdentity> identitiesOf(UUID stationUid, List<StationMember> members) {
        return members.stream().map(member -> identityOf(stationUid, member)).toList();
    }

    private List<MemberCardLabel> tagsOf(StationSession reader, int memberId) {
        return privateTags.visibleTo(reader, tagService.findTagsForMember(memberId)).stream()
                .sorted(Comparator.comparingInt(UserTag::position).reversed())
                .map(tag -> new MemberCardLabel(tag.name(), tag.color()))
                .toList();
    }

    private List<MemberCardLabel> groupsOf(int memberId) {
        return groupService.findGroupsForMember(memberId).stream()
                .sorted(Comparator.comparingInt(MemberGroup::position).reversed())
                .map(group -> new MemberCardLabel(group.name(), group.color()))
                .toList();
    }
}
