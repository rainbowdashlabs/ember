/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.RegistrationCode;
import dev.chojo.ember.feature.members.repository.RegistrationCodeRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing station registration codes that allow self-registration
 * with automatic group assignment and configurable usage limits.
 */
@Singleton
public class RegistrationCodeService {
    private static final Logger log = LoggerFactory.getLogger(RegistrationCodeService.class);

    private final RegistrationCodeRepository codeRepository;
    private final GroupMembershipService groupMemberships;

    @Inject
    public RegistrationCodeService(RegistrationCodeRepository codeRepository, GroupMembershipService groupMemberships) {
        this.codeRepository = codeRepository;
        this.groupMemberships = groupMemberships;
    }

    public List<RegistrationCode> findByStation(int stationId) {
        return codeRepository.findByStation(stationId);
    }

    /**
     * A code of the station. A code of another station is not found, the same as one that is gone,
     * so its number says nothing about whether it exists elsewhere.
     *
     * @param stationId the station asking
     * @param id        the code
     * @return the code, where it is one of the station's
     */
    public Optional<RegistrationCode> findInStation(int stationId, int id) {
        return codeRepository.findById(id).filter(code -> code.stationId() == stationId);
    }

    private RegistrationCode requireInStation(int stationId, int id, Refusal notHere) {
        return findInStation(stationId, id).orElseThrow(notHere::raise);
    }

    public RegistrationCode create(int stationId, String code, int maxUses) {
        var created = codeRepository.create(stationId, code, maxUses);
        log.info("Registration code created: id={}, station={}, maxUses={}", created.id(), stationId, maxUses);
        return created;
    }

    /**
     * Deletes a code of the station.
     *
     * @param stationId the station asking
     * @param id        the code
     */
    public void delete(int stationId, int id) {
        requireInStation(stationId, id, Refusal.REGISTRATION_CODE_NOT_DELETED);
        if (!codeRepository.delete(id)) throw Refusal.REGISTRATION_CODE_NOT_DELETED.raise();
        log.info("Registration code deleted: id={}, station={}", id, stationId);
    }

    // -- Code-Group assignments --

    /**
     * The groups somebody registering with a code of the station is put into.
     *
     * @param stationId the station asking
     * @param codeId    the code
     * @return the ids of its groups
     */
    public List<Integer> findGroupIds(int stationId, int codeId) {
        requireInStation(stationId, codeId, Refusal.REGISTRATION_CODE_NOT_HERE_FOR_GROUPS);
        return codeRepository.findGroupIds(codeId);
    }

    /**
     * Replaces the groups somebody registering with the code is put into.
     *
     * <p>Every group has to be one of the code's station and take members, because that is what
     * somebody registering becomes. A group that does not is refused here, where the manager sets the
     * code up, rather than left out silently when somebody registers.
     *
     * @param stationId       the station asking, which has to own the code
     * @param codeId          the code
     * @param desiredGroupIds every group it should put people into
     * @return the ids of its groups afterwards
     */
    public List<Integer> setGroups(int stationId, int codeId, List<Integer> desiredGroupIds) {
        requireInStation(stationId, codeId, Refusal.REGISTRATION_CODE_NOT_HERE_TO_CHANGE_GROUPS);
        desiredGroupIds.forEach(groupId -> groupMemberships.requireAdmits(
                stationId,
                groupId,
                StationUserType.MEMBER,
                Refusal.REGISTRATION_CODE_GROUP_NOT_HERE,
                Refusal.REGISTRATION_CODE_GROUP_WRONG_USER_TYPE));
        List<Integer> currentGroupIds = codeRepository.findGroupIds(codeId);

        for (int groupId : currentGroupIds) {
            if (!desiredGroupIds.contains(groupId)) {
                codeRepository.removeGroup(codeId, groupId);
            }
        }
        for (int groupId : desiredGroupIds) {
            if (!currentGroupIds.contains(groupId)) {
                codeRepository.addGroup(codeId, groupId);
            }
        }

        log.info("Registration code groups updated: code={}, groups={}", codeId, desiredGroupIds);
        return codeRepository.findGroupIds(codeId);
    }
}
