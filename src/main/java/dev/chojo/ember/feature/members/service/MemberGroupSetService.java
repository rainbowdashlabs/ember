/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.members.entity.MemberGroupSet;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The sets of groups a station keeps, each allowing a member in only one of its groups.
 *
 * <p>Which groups belong to a set is decided on the group, where the members that would break the
 * rule are checked. A set on its own is only a name, so creating, renaming and deleting one can
 * break nothing: deleting a set leaves its groups and their members as they are, in no set.
 */
@Singleton
public class MemberGroupSetService {
    private static final Logger log = LoggerFactory.getLogger(MemberGroupSetService.class);
    private final MemberGroupSetRepository setRepository;

    @Inject
    public MemberGroupSetService(MemberGroupSetRepository setRepository) {
        this.setRepository = setRepository;
    }

    /**
     * The sets of a station, by name.
     */
    public List<MemberGroupSet> findByStation(int stationId) {
        return setRepository.findByStation(stationId);
    }

    /**
     * Creates a set.
     *
     * @param stationId the station
     * @param name      its name, unique within the station
     * @return the new set
     */
    public MemberGroupSet create(int stationId, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw Refusal.GROUP_SET_NAME_MISSING_ON_CREATE.raise();
        if (setRepository.nameTaken(stationId, trimmed, null)) throw Refusal.GROUP_SET_NAME_TAKEN_ON_CREATE.raise();
        var set = setRepository.create(stationId, trimmed);
        log.info("Group set created: id={}, station={}", set.id(), stationId);
        return set;
    }

    /**
     * Renames a set of the station.
     *
     * @param stationId the station
     * @param id        the set
     * @param name      its new name
     * @return the renamed set
     */
    public MemberGroupSet rename(int stationId, int id, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw Refusal.GROUP_SET_NAME_MISSING_ON_CHANGE.raise();
        if (setRepository.nameTaken(stationId, trimmed, id)) throw Refusal.GROUP_SET_NAME_TAKEN_ON_CHANGE.raise();
        if (!setRepository.rename(id, stationId, trimmed)) throw Refusal.GROUP_SET_NOT_HERE_ON_CHANGE.raise();
        log.info("Group set renamed: id={}, station={}", id, stationId);
        return new MemberGroupSet(id, stationId, trimmed);
    }

    /**
     * Deletes a set of the station. Its groups stay, with their members, in no set.
     *
     * @param stationId the station
     * @param id        the set
     */
    public void delete(int stationId, int id) {
        if (!setRepository.delete(id, stationId)) throw Refusal.GROUP_SET_NOT_HERE_ON_DELETE.raise();
        log.info("Group set deleted: id={}, station={}", id, stationId);
    }
}
