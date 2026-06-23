/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorPolicy;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes {@code two_factor_policy} rows and assembles the per-station member status
 * list rendered in the admin / station-admin Security panels.
 *
 * <p>The "is this account mandated?" derivation is intentionally simple here: an account is
 * mandated when (a) the account holds at least one elevated role / permission, or (b) at
 * least one matching {@code required = true} policy row exists for one of the account's
 * memberships. The richer freshness / grace-window resolver lives in the upcoming policy
 * engine work (concept §9).
 */
@Singleton
public class TwoFactorPolicyService {
    private static final List<StationUserType> ASSIGNABLE_USER_TYPES =
            List.of(StationUserType.MEMBER, StationUserType.GUARDIAN, StationUserType.TEAM, StationUserType.MANAGER);

    private final TwoFactorRepository repository;
    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public TwoFactorPolicyService(
            TwoFactorRepository repository,
            StationMemberRepository memberRepository,
            AccountRepository accountRepository) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
    }

    private static boolean containsElevatedInstance(InstancePermission[] perms) {
        for (InstancePermission p : perms) {
            if (p == InstancePermission.ADMINISTRATOR) return true;
        }
        return false;
    }

    private static short clampGrace(short graceDays) {
        if (graceDays < 0) return 0;
        if (graceDays > 7) return 7;
        return graceDays;
    }

    public List<StationUserType> assignableUserTypes() {
        return ASSIGNABLE_USER_TYPES;
    }

    public List<TwoFactorPolicy> listInstancePolicies() {
        return repository.findInstancePolicies();
    }

    public List<TwoFactorPolicy> listStationPolicies(int stationId) {
        return repository.findStationPolicies(stationId);
    }

    public TwoFactorPolicy setInstancePolicy(
            StationUserType userType, boolean required, short graceDays, Integer createdBy) {
        return repository.upsertPolicy(
                TwoFactorPolicy.Scope.INSTANCE, null, userType, required, clampGrace(graceDays), createdBy);
    }

    public TwoFactorPolicy setStationPolicy(
            int stationId, StationUserType userType, boolean required, short graceDays, Integer createdBy) {
        return repository.upsertPolicy(
                TwoFactorPolicy.Scope.STATION, stationId, userType, required, clampGrace(graceDays), createdBy);
    }

    public boolean deletePolicy(int policyId) {
        return repository.deletePolicy(policyId);
    }

    /**
     * Returns the per-member 2FA-status rows shown in the station-admin Security panel.
     * Enrolment is checked through {@link TwoFactorRepository#isEnrolled(int)}; mandate is
     * derived from elevated role / permission membership plus matching policy rows.
     */
    public List<MemberStatus> listStationMemberStatus(int stationId) {
        List<StationMember> members = memberRepository.findByStation(stationId);
        List<TwoFactorPolicy> instancePolicies = repository.findInstancePolicies();
        List<TwoFactorPolicy> stationPolicies = repository.findStationPolicies(stationId);
        Map<Integer, Account> accountById = new HashMap<>();
        for (var m : members) {
            if (m.accountId() == null) continue;
            accountRepository.findById(m.accountId()).ifPresent(a -> accountById.put(a.id(), a));
        }

        return members.stream()
                .filter(m -> m.accountId() != null)
                .map(m -> {
                    Account account = accountById.get(m.accountId());
                    if (account == null) return null;
                    boolean enrolled = repository.isEnrolled(account.id());
                    boolean mandated = isMandated(account, m, stationId, instancePolicies, stationPolicies);
                    return new MemberStatus(
                            m.id(),
                            account.id(),
                            account.firstName(),
                            account.lastName(),
                            account.email(),
                            m.userType(),
                            enrolled,
                            mandated);
                })
                .filter(s -> s != null)
                .toList();
    }

    private boolean isMandated(
            Account account,
            StationMember member,
            int stationId,
            List<TwoFactorPolicy> instancePolicies,
            List<TwoFactorPolicy> stationPolicies) {
        if (hasElevatedAccountPermission(account)) return true;
        if (hasElevatedMemberPermission(member)) return true;
        StationUserType userType = member.userType();
        for (TwoFactorPolicy p : instancePolicies) {
            if (!p.required()) continue;
            if (p.userType() == null || p.userType() == userType) return true;
        }
        for (TwoFactorPolicy p : stationPolicies) {
            if (!p.required()) continue;
            if (!Integer.valueOf(stationId).equals(p.stationId())) continue;
            if (p.userType() == null || p.userType() == userType) return true;
        }
        return false;
    }

    private boolean hasElevatedAccountPermission(Account account) {
        return account.instanceUserType() != null
                && account.instanceUserType().defaultPermissions() != null
                && containsElevatedInstance(account.instanceUserType().defaultPermissions());
    }

    private boolean hasElevatedMemberPermission(StationMember member) {
        var permissions = memberRepository.findPermissions(member.id());
        for (var perm : permissions) {
            StationPermission p = perm.permission();
            if (p == StationPermission.STATION_ADMINISTRATOR || p == StationPermission.STATION_MANAGER) {
                return true;
            }
        }
        // Default permissions for the user type
        if (member.userType() != null) {
            for (StationPermission p : member.userType().defaultPermissions()) {
                if (p == StationPermission.STATION_ADMINISTRATOR || p == StationPermission.STATION_MANAGER) {
                    return true;
                }
            }
        }
        return false;
    }

    public record MemberStatus(
            int memberId,
            int accountId,
            String firstName,
            String lastName,
            String email,
            StationUserType userType,
            boolean enrolled,
            boolean mandated) {}
}
