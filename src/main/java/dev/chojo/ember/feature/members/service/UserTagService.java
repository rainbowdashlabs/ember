/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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

    @Inject
    public UserTagService(UserTagRepository tagRepository, MemberGroupRepository groupRepository) {
        this.tagRepository = tagRepository;
        this.groupRepository = groupRepository;
    }

    public UserTag create(int stationId, String name) {
        var tag = tagRepository.create(stationId, name);
        log.info("User tag created: id={}, station={}, name='{}'", tag.id(), stationId, name);
        return tag;
    }

    public Optional<UserTag> findById(int id) {
        return tagRepository.findById(id);
    }

    public List<UserTag> findByStation(int stationId) {
        return tagRepository.findByStation(stationId);
    }

    public boolean update(int id, String name, String color, boolean visible, int position) {
        boolean updated = tagRepository.update(id, name, color, visible, position);
        if (updated) {
            log.info("User tag updated: id={}, name='{}', visible={}", id, name, visible);
        } else {
            log.warn("User tag update affected no rows: id={}", id);
        }
        return updated;
    }

    public boolean delete(int id) {
        boolean deleted = tagRepository.delete(id);
        if (deleted) {
            log.info("User tag deleted: id={}", id);
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

    public void setMembers(int tagId, List<Integer> memberIds) {
        tagRepository.setMembers(tagId, memberIds);
        log.info("User tag members set: tag={}, count={}", tagId, memberIds.size());
    }

    public void convertToGroup(int tagId) {
        var tag = tagRepository.findById(tagId).orElseThrow();
        var members = tagRepository.findMembers(tagId);
        var group = groupRepository.create(tag.stationId(), tag.name());
        for (var member : members) {
            groupRepository.addMember(group.id(), member.id());
        }
        tagRepository.delete(tagId);
        log.info("User tag {} converted to group {} in station {}", tagId, group.id(), tag.stationId());
    }
}
