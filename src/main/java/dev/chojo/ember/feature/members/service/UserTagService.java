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

import java.util.List;
import java.util.Optional;

/**
 * Service for managing user tags including CRUD on tag definitions
 * and member-tag assignments within a station.
 */
@Singleton
public class UserTagService {
    private final UserTagRepository tagRepository;
    private final MemberGroupRepository groupRepository;

    @Inject
    public UserTagService(UserTagRepository tagRepository, MemberGroupRepository groupRepository) {
        this.tagRepository = tagRepository;
        this.groupRepository = groupRepository;
    }

    public UserTag create(int stationId, String name) {
        return tagRepository.create(stationId, name);
    }

    public Optional<UserTag> findById(int id) {
        return tagRepository.findById(id);
    }

    public List<UserTag> findByStation(int stationId) {
        return tagRepository.findByStation(stationId);
    }

    public boolean update(int id, String name) {
        return tagRepository.update(id, name);
    }

    public boolean delete(int id) {
        return tagRepository.delete(id);
    }

    public List<StationMember> findMembers(int tagId) {
        return tagRepository.findMembers(tagId);
    }

    public List<UserTag> findTagsForMember(int memberId) {
        return tagRepository.findTagsForMember(memberId);
    }

    public void setMembers(int tagId, List<Integer> memberIds) {
        tagRepository.setMembers(tagId, memberIds);
    }

    public void convertToGroup(int tagId) {
        var tag = tagRepository.findById(tagId).orElseThrow();
        var members = tagRepository.findMembers(tagId);
        var group = groupRepository.create(tag.stationId(), tag.name());
        for (var member : members) {
            groupRepository.addMember(group.id(), member.id());
        }
        tagRepository.delete(tagId);
    }
}
