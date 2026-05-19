/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Singleton
public class AccessManager {
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;

    @Inject
    public AccessManager(
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
    }

    public Optional<AccountSession> resolveSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<AccountSession> session = accountRepository.findSession(token);
        if (session.isPresent() && session.get().isExpired()) {
            accountRepository.deleteSession(token);
            return Optional.empty();
        }
        return session;
    }

    public Optional<UserSession> resolveUserSession(String token, Integer stationId) {
        Optional<AccountSession> sessionOpt = resolveSession(token);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        int accountId = sessionOpt.get().accountId();
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return Optional.empty();
        }

        Account account = accountOpt.get();

        // Resolve account-level roles (e.g. admin)
        Set<Roles> roles = resolveAccountRoles(accountId);

        if (stationId != null) {
            Optional<StationMember> memberOpt = stationMemberRepository.findByStationAndAccount(stationId, accountId);
            if (memberOpt.isPresent()) {
                StationMember member = memberOpt.get();
                // Direct member roles
                List<Role> dbRoles = stationMemberRepository.findRoles(member.id());
                dbRoles.stream().map(Role::role).forEach(roles::add);
                // Roles inherited from groups
                List<Role> groupRoles = memberGroupRepository.findRolesForMemberViaGroups(member.id());
                groupRoles.stream().map(Role::role).forEach(roles::add);
                roles = Roles.expand(roles);
                return Optional.of(new UserSession(account, stationId, member, roles));
            }
        }

        return Optional.of(new UserSession(account, stationId, null, roles));
    }

    /**
     * Resolves the expanded roles for a station member by their member ID.
     */
    public Set<Roles> resolveExpandedMemberRoles(int memberId) {
        Set<Roles> roles = EnumSet.noneOf(Roles.class);
        List<Role> dbRoles = stationMemberRepository.findRoles(memberId);
        dbRoles.stream().map(Role::role).forEach(roles::add);
        List<Role> groupRoles = memberGroupRepository.findRolesForMemberViaGroups(memberId);
        groupRoles.stream().map(Role::role).forEach(roles::add);
        return Roles.expand(roles);
    }

    private Set<Roles> resolveAccountRoles(int accountId) {
        Set<Roles> roles = EnumSet.noneOf(Roles.class);
        List<String> accountRoles = accountRepository.findAccountRoles(accountId);
        for (String role : accountRoles) {
            Roles mapped = Roles.fromDbName(role);
            if (mapped != null) {
                roles.add(mapped);
            }
        }
        return roles;
    }
}
