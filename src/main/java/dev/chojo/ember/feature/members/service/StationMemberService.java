/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.util.RoleValidation;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for station member operations including membership queries, role management
 * with ownership protection, and member-account relationships.
 */
@Singleton
public class StationMemberService {
    private final StationMemberRepository memberRepository;
    private final StationRepository stationRepository;

    @Inject
    public StationMemberService(StationMemberRepository memberRepository, StationRepository stationRepository) {
        this.memberRepository = memberRepository;
        this.stationRepository = stationRepository;
    }

    public List<StationMember> findByStation(int stationId) {
        return memberRepository.findByStation(stationId);
    }

    public List<StationMember> findByStation(int stationId, boolean includeFormer) {
        return memberRepository.findByStation(stationId, includeFormer);
    }

    public Optional<StationMember> findById(int id) {
        return memberRepository.findById(id);
    }

    public List<StationMember> findByAccount(int accountId) {
        return memberRepository.findByAccount(accountId);
    }

    public StationMember create(int stationId, int accountId) {
        return memberRepository.create(stationId, accountId);
    }

    public boolean delete(int id) {
        return memberRepository.delete(id);
    }

    // -- Roles (batch) --

    public List<Role> findRoles(int memberId) {
        return memberRepository.findRoles(memberId);
    }

    public List<Role> setRoles(int memberId, List<Integer> desiredRoleIds, Set<Roles> callerRoles) {
        List<Role> allRoles = memberRepository.findAllRoles();
        List<Role> currentRoles = memberRepository.findRoles(memberId);
        var currentRoleIds = currentRoles.stream().map(Role::id).toList();

        var member = memberRepository.findById(memberId).orElse(null);
        boolean isOwner = false;
        if (member != null) {
            var station = stationRepository.findById(member.stationId()).orElse(null);
            isOwner = station != null && station.ownerMemberId() != null && station.ownerMemberId() == memberId;
        }
        RoleValidation.validateRoleChanges(currentRoles, desiredRoleIds, allRoles, callerRoles, isOwner);

        for (int roleId : currentRoleIds) {
            if (!desiredRoleIds.contains(roleId)) {
                memberRepository.removeRole(memberId, roleId);
            }
        }
        for (int roleId : desiredRoleIds) {
            if (!currentRoleIds.contains(roleId)) {
                memberRepository.addRole(memberId, roleId);
            }
        }

        return memberRepository.findRoles(memberId);
    }

    // -- Manager relations --

    public List<StationMember> findManaged(int managerId) {
        return memberRepository.findManaged(managerId);
    }

    public List<StationMember> findManagers(int managedId) {
        return memberRepository.findManagers(managedId);
    }

    public List<StationMember> setManagers(int managedId, List<Integer> desiredManagerIds) {
        List<StationMember> currentManagers = memberRepository.findManagers(managedId);
        var currentManagerIds = currentManagers.stream().map(StationMember::id).toList();

        for (int managerId : currentManagerIds) {
            if (!desiredManagerIds.contains(managerId)) {
                memberRepository.removeManager(managerId, managedId);
            }
        }
        for (int managerId : desiredManagerIds) {
            if (!currentManagerIds.contains(managerId)) {
                memberRepository.addManager(managerId, managedId);
            }
        }

        return memberRepository.findManagers(managedId);
    }
}
