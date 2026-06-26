/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

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

    @Inject
    public RegistrationCodeService(RegistrationCodeRepository codeRepository) {
        this.codeRepository = codeRepository;
    }

    public List<RegistrationCode> findByStation(int stationId) {
        return codeRepository.findByStation(stationId);
    }

    public Optional<RegistrationCode> findById(int id) {
        return codeRepository.findById(id);
    }

    public RegistrationCode create(int stationId, String code, int maxUses) {
        var created = codeRepository.create(stationId, code, maxUses);
        log.info("Registration code created: id={}, station={}, maxUses={}", created.id(), stationId, maxUses);
        return created;
    }

    public boolean delete(int id) {
        boolean deleted = codeRepository.delete(id);
        if (deleted) {
            log.info("Registration code deleted: id={}", id);
        } else {
            log.warn("Registration code delete affected no rows: id={}", id);
        }
        return deleted;
    }

    // -- Code-Group assignments --

    public List<Integer> findGroupIds(int codeId) {
        return codeRepository.findGroupIds(codeId);
    }

    public List<Integer> setGroups(int codeId, List<Integer> desiredGroupIds) {
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
