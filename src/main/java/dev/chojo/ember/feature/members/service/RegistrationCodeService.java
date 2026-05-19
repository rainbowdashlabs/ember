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

import java.util.List;
import java.util.Optional;

@Singleton
public class RegistrationCodeService {
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
        return codeRepository.create(stationId, code, maxUses);
    }

    public boolean delete(int id) {
        return codeRepository.delete(id);
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

        return codeRepository.findGroupIds(codeId);
    }
}
