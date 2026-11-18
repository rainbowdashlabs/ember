/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.MemberDisplayChanged;
import dev.chojo.ember.event.events.StationAudienceChanged;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.MemberRuleRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing user tags including CRUD on tag definitions
 * and member-tag assignments within a station.
 */
@Singleton
public class UserTagService {
    private static final Logger log = LoggerFactory.getLogger(UserTagService.class);

    private final UserTagRepository tagRepository;
    private final MemberGroupRepository groupRepository;
    private final MemberRuleRepository ruleRepository;
    private final GroupMembershipService memberships;

    private final DomainEventBus eventBus;

    @Inject
    public UserTagService(
            UserTagRepository tagRepository,
            MemberGroupRepository groupRepository,
            MemberRuleRepository ruleRepository,
            GroupMembershipService memberships,
            DomainEventBus eventBus) {
        this.eventBus = eventBus;
        this.tagRepository = tagRepository;
        this.groupRepository = groupRepository;
        this.ruleRepository = ruleRepository;
        this.memberships = memberships;
    }

    public UserTag create(int stationId, String name) {
        return create(stationId, name, null, TagVisibility.PLAIN);
    }

    /**
     * Creates a tag with its colour and visibility, so a tag meant to be private is never briefly
     * visible to everybody.
     */
    public UserTag create(int stationId, String name, @Nullable String color, TagVisibility visibility) {
        var tag = tagRepository.create(stationId, name, color, visibility);
        log.info(
                "User tag created: id={}, station={}, name='{}', visibility={}", tag.id(), stationId, name, visibility);
        return tag;
    }

    public Optional<UserTag> findById(int id) {
        return tagRepository.findById(id);
    }

    public List<UserTag> findByStation(int stationId) {
        return tagRepository.findByStation(stationId);
    }

    /**
     * Changes a tag. Making a tag private is refused while audiences, restrictions or access rules
     * still choose people by it, so nothing that relies on it changes silently.
     */
    public boolean update(int id, String name, @Nullable String color, TagVisibility visibility, int position) {
        if (visibility.restricted()) {
            long uses = tagRepository.countSelectingUses(id);
            if (uses > 0) {
                throw MemberRefusal.TAG_PRIVATE_STILL_CHOOSES.raise(RefusalDetail.count(uses));
            }
        }
        boolean updated = tagRepository.update(id, name, color, visibility, position);
        if (updated) {
            log.info("User tag updated: id={}, name='{}', visibility={}", id, name, visibility);
            tagRepository.findById(id).ifPresent(tag -> eventBus.publish(MemberDisplayChanged.at(tag.stationId())));
        } else {
            log.warn("User tag update affected no rows: id={}", id);
        }
        return updated;
    }

    /**
     * Deletes a tag, unless a rule of another group or tag still asks about it.
     *
     * @param id the tag
     * @return whether a tag was deleted
     * @throws dev.chojo.ember.api.refusal.RefusalResponse {@link MemberRefusal#USED_BY_A_RULE} naming the groups
     *         and tags whose rules ask about it
     */
    public boolean delete(int id) {
        requireNotAskedAbout(id);
        var tag = tagRepository.findById(id);
        boolean deleted = tagRepository.delete(id);
        if (deleted) {
            log.info("User tag deleted: id={}", id);
            tag.ifPresent(gone -> eventBus.publish(StationAudienceChanged.at(gone.stationId())));
        } else {
            log.warn("User tag delete affected no rows: id={}", id);
        }
        return deleted;
    }

    public List<StationMember> findMembers(int tagId) {
        return tagRepository.findMembers(tagId);
    }

    public List<UserTag> findTagsForMember(int memberId) {
        return tagRepository.findTagsForMember(memberId);
    }

    /**
     * Replaces the members of a tag, unless the tag takes its members from a rule. A group whose rule asks
     * about the tag is filled anew, so its permissions count as granted by the caller.
     *
     * @param tagId     the tag
     * @param memberIds every member it should hold afterwards
     * @param by        who is asking
     * @throws dev.chojo.ember.api.refusal.RefusalResponse {@link MemberRefusal#MEMBERS_KEPT_BY_RULE} for a tag
     *         with a rule, {@link MemberRefusal#GROUP_GRANTS_MORE_THAN_YOURS_THROUGH_RULE} naming a group the
     *         change reaches through rules that grants more than the caller holds
     */
    public void setMembers(int tagId, List<Integer> memberIds, UserSession by) {
        var tag = tagRepository.findById(tagId);
        if (ruleRepository.hasRule(RuleOwner.tag(tagId))) {
            String name = tag.map(UserTag::name).orElse("");
            throw MemberRefusal.MEMBERS_KEPT_BY_RULE.raise(RefusalDetail.text(name));
        }
        tag.ifPresent(found -> memberships.requireMayChangeThroughRules(found.stationId(), RuleOwner.tag(tagId), by));
        Transactions.run(() -> tagRepository.setMembers(tagId, memberIds));
        log.info("User tag members set: tag={}, count={}", tagId, memberIds.size());
        tag.ifPresent(found -> eventBus.publish(StationAudienceChanged.at(found.stationId())));
    }

    /**
     * Turns a tag into a group with the same members. A private tag is refused, because a group is
     * seen by everybody, and so is a tag a rule of another group or tag asks about. A rule that fills the
     * tag goes on to fill the group.
     */
    public void convertToGroup(int tagId) {
        var tag = tagRepository.findById(tagId).orElseThrow();
        if (tag.visibility().restricted()) {
            throw MemberRefusal.TAG_PRIVATE_NOT_A_GROUP.raise();
        }
        requireNotAskedAbout(tagId);
        var group = Transactions.call(() -> {
            var created = groupRepository.create(tag.stationId(), tag.name());
            for (var member : tagRepository.findMembers(tagId)) {
                groupRepository.addMember(created.id(), member.id());
            }
            ruleRepository.moveRule(RuleOwner.tag(tagId), RuleOwner.group(created.id()));
            tagRepository.delete(tagId);
            return created;
        });
        log.info("User tag {} converted to group {} in station {}", tagId, group.id(), tag.stationId());
        eventBus.publish(StationAudienceChanged.at(tag.stationId()));
    }

    private void requireNotAskedAbout(int tagId) {
        List<String> askers = ruleRepository.askingAboutTag(tagId);
        if (!askers.isEmpty()) {
            throw MemberRefusal.USED_BY_A_RULE.raise(RefusalDetail.text(String.join(", ", askers)));
        }
    }
}
