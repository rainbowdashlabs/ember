/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository.StationLogo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class StationService {
    private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024; // 2 MB

    private final StationRepository stationRepository;
    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final AuthService authService;
    private final Map<Integer, Optional<StationLogo>> logoCache = new ConcurrentHashMap<>();

    @Inject
    public StationService(
            StationRepository stationRepository,
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            AuthService authService) {
        this.stationRepository = stationRepository;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.authService = authService;
    }

    public List<Station> findAll() {
        return stationRepository.findAll();
    }

    public Optional<Station> findById(int id) {
        return stationRepository.findById(id);
    }

    public Station create(String name) {
        return stationRepository.create(name);
    }

    public Station createWithManager(String name, String managerEmail) {
        var station = stationRepository.create(name);
        assignManager(station.id(), managerEmail);
        return station;
    }

    public Optional<Station> update(int id, String name) {
        if (stationRepository.update(id, name)) {
            return stationRepository.findById(id);
        }
        return Optional.empty();
    }

    public Optional<Station> updateTimezone(int id, String timezone) {
        if (stationRepository.updateTimezone(id, timezone)) {
            return stationRepository.findById(id);
        }
        return Optional.empty();
    }

    public Optional<Station> updateLocale(int id, String locale) {
        if (stationRepository.updateLocale(id, locale)) {
            return stationRepository.findById(id);
        }
        return Optional.empty();
    }

    public Optional<Station> updateWithManager(int id, String name, String managerEmail) {
        if (!stationRepository.update(id, name)) {
            return Optional.empty();
        }
        assignManager(id, managerEmail);
        return stationRepository.findById(id);
    }

    public boolean delete(int id) {
        return stationRepository.delete(id);
    }

    public Optional<Account> findManager(int stationId) {
        Role managerRole = memberRepository.findRoleByName(Roles.MANAGER).orElse(null);
        if (managerRole == null) return Optional.empty();

        for (StationMember member : memberRepository.findByStation(stationId)) {
            List<Role> roles = memberRepository.findRoles(member.id());
            if (roles.stream().anyMatch(r -> r.id() == managerRole.id())) {
                return accountRepository.findById(member.accountId());
            }
        }
        return Optional.empty();
    }

    public Optional<ManagerInfo> findManagerInfo(int stationId) {
        Role managerRole = memberRepository.findRoleByName(Roles.MANAGER).orElse(null);
        if (managerRole == null) return Optional.empty();

        for (StationMember member : memberRepository.findByStation(stationId)) {
            List<Role> roles = memberRepository.findRoles(member.id());
            if (roles.stream().anyMatch(r -> r.id() == managerRole.id())) {
                Account account = accountRepository.findById(member.accountId()).orElse(null);
                if (account == null) continue;
                Optional<AccountCredential> credential = accountRepository.findCredential(account.id());
                boolean hasPassword = credential.isPresent();
                boolean accountReady =
                        hasPassword && !credential.get().forcePasswordChange() && account.emailVerified();
                return Optional.of(
                        new ManagerInfo(account.email(), account.firstName(), account.lastName(), accountReady));
            }
        }
        return Optional.empty();
    }

    public Optional<StationLogo> getLogo(int stationId) {
        return logoCache.computeIfAbsent(stationId, stationRepository::findLogo);
    }

    public void setLogo(int stationId, byte[] data, String contentType) {
        if (data.length > MAX_LOGO_SIZE) {
            throw new IllegalArgumentException("Logo exceeds maximum size of 2 MB");
        }
        stationRepository.updateLogo(stationId, data, contentType);
        logoCache.put(stationId, Optional.of(new StationLogo(data, contentType)));
    }

    // -- Logo --

    public void deleteLogo(int stationId) {
        stationRepository.deleteLogo(stationId);
        logoCache.put(stationId, Optional.empty());
    }

    /**
     * Assigns the MANAGER role to the given email and sets them as station owner if no owner exists yet.
     */
    private void assignManager(int stationId, String managerEmail) {
        Role managerRole = memberRepository
                .findRoleByName(Roles.MANAGER)
                .orElseThrow(() -> new IllegalStateException("manager role not found"));

        // Find or invite account
        Optional<Account> accountOpt = accountRepository.findByEmail(managerEmail);
        Account account;
        if (accountOpt.isPresent()) {
            account = accountOpt.get();
        } else {
            var result = authService.createInvitedAccount(managerEmail, "", "", stationId);
            if (!result.success()) {
                throw new IllegalStateException("Failed to invite account: " + result.message());
            }
            account = result.account();
        }

        // Ensure membership
        StationMember member = memberRepository
                .findByStationAndAccount(stationId, account.id())
                .orElseGet(() -> memberRepository.create(stationId, account.id()));

        // Assign manager role
        List<Role> currentRoles = memberRepository.findRoles(member.id());
        if (currentRoles.stream().noneMatch(r -> r.id() == managerRole.id())) {
            memberRepository.addRole(member.id(), managerRole.id());
        }

        // Set as owner if no owner exists yet
        var station = stationRepository.findById(stationId).orElse(null);
        if (station != null && station.ownerMemberId() == null) {
            stationRepository.setOwner(stationId, member.id());
        }
    }

    /**
     * Transfers station ownership to another member who already has the MANAGER role.
     * Only the current owner can call this.
     */
    public boolean transferOwnership(int stationId, int currentMemberId, int newOwnerMemberId) {
        var station = stationRepository.findById(stationId).orElse(null);
        if (station == null) return false;
        if (station.ownerMemberId() == null || station.ownerMemberId() != currentMemberId) return false;

        // Verify the target has the MANAGER role
        Role managerRole = memberRepository.findRoleByName(Roles.MANAGER).orElse(null);
        if (managerRole == null) return false;
        var targetRoles = memberRepository.findRoles(newOwnerMemberId);
        if (targetRoles.stream().noneMatch(r -> r.id() == managerRole.id())) return false;

        stationRepository.setOwner(stationId, newOwnerMemberId);
        return true;
    }

    public boolean isOwner(int stationId, int memberId) {
        var station = stationRepository.findById(stationId).orElse(null);
        return station != null && station.ownerMemberId() != null && station.ownerMemberId() == memberId;
    }

    // -- Modules --

    public Set<String> findDisabledModules(int stationId) {
        return stationRepository.findDisabledModules(stationId);
    }

    public void setDisabledModules(int stationId, Set<String> modules) {
        stationRepository.setDisabledModules(stationId, modules);
    }

    public boolean isModuleEnabled(int stationId, String module) {
        return !stationRepository.findDisabledModules(stationId).contains(module);
    }

    public record ManagerInfo(String email, String firstName, String lastName, boolean accountReady) {}
}
