/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Provisions station members from invite requests. Inviting someone creates the account and the
 * station membership immediately, so the member is usable in groups, events and attendance right
 * away; the invite email is a password-setup link that lets the recipient claim the account.
 * Used by the setup wizard's "invites" step and the members area.
 *
 * <p>Emails that already belong to an account attach that account to the station instead of
 * creating a duplicate: the membership is created if absent, and the account keeps its name and
 * existing memberships untouched. Synthetic addresses ending in {@code .local} (members without
 * login) never attach to existing accounts and never receive mail.
 *
 * <p>Guardian relations are wired at creation time — both accounts exist immediately, so the
 * manager link between guardian and member is set as part of the same request.
 */
@Singleton
public class StationMemberInviteService {

    private static final Logger log = LoggerFactory.getLogger(StationMemberInviteService.class);
    private static final String SYNTHETIC_EMAIL_SUFFIX = ".local";

    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final AccountRepository accountRepository;
    private final AuthService authService;

    @Inject
    public StationMemberInviteService(
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            AccountRepository accountRepository,
            AuthService authService) {
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.accountRepository = accountRepository;
        this.authService = authService;
    }

    /**
     * Provisions a single member: resolves or creates the account, creates the station membership
     * if absent, and sends the password-setup email when the account still needs one. The user
     * type and group are only applied to memberships created by this call — existing members keep
     * their configuration.
     *
     * @throws ProvisionException if the email belongs to an existing account and attaching is not
     *                            allowed (synthetic {@code .local} addresses)
     */
    public ProvisionedMember provision(
            int stationId, String email, String firstName, String lastName, StationUserType userType, Integer groupId) {
        String normalizedEmail = email.trim();
        boolean synthetic = normalizedEmail.endsWith(SYNTHETIC_EMAIL_SUFFIX);

        Account account = accountRepository.findByEmail(normalizedEmail).orElse(null);
        boolean accountCreated = account == null;
        if (account == null) {
            account = accountRepository.create(normalizedEmail, firstName, lastName, true, stationId);
        } else if (synthetic) {
            throw ProvisionException.emailInUse(normalizedEmail);
        }

        var member = stationMemberRepository
                .findByStationAndAccount(stationId, account.id())
                .orElse(null);
        boolean membershipCreated = member == null;
        if (member == null) {
            member = stationMemberRepository.create(stationId, account.id());
            stationMemberRepository.setUserType(member.id(), userType);
            if (groupId != null) {
                memberGroupRepository.addMember(groupId, member.id());
            }
        }

        if (!synthetic && needsSetup(account)) {
            authService.sendPasswordSetup(account.id(), account.email(), account.firstName());
        }

        log.info(
                "Member provisioned: member={}, account={}, station={}, accountCreated={}, membershipCreated={}",
                member.id(),
                account.id(),
                stationId,
                accountCreated,
                membershipCreated);
        return new ProvisionedMember(
                member.id(),
                account.id(),
                account.email(),
                account.firstName(),
                account.lastName(),
                membershipCreated ? userType : member.userType(),
                accountCreated,
                membershipCreated);
    }

    /**
     * Provisions a batch of invite entries, expanding nested guardian sub-lists. Guardians are
     * provisioned as {@link StationUserType#GUARDIAN} and linked as manager of the member they
     * belong to. Entries are processed independently — a failing entry does not affect the rest;
     * failed entries are reported in the result.
     */
    public BatchResult createBatch(int stationId, List<InviteRequest> requests) {
        var provisioned = new ArrayList<ProvisionedMember>();
        var failed = new ArrayList<FailedInvite>();
        for (InviteRequest req : requests) {
            ProvisionedMember parent;
            try {
                parent = provision(
                        stationId,
                        req.email(),
                        req.firstName(),
                        req.lastName(),
                        req.userType() != null ? req.userType() : StationUserType.MEMBER,
                        req.groupId());
                provisioned.add(parent);
            } catch (ProvisionException e) {
                failed.add(new FailedInvite(req.email(), e.getMessage()));
                continue;
            }
            if (req.guardians() == null) continue;
            for (GuardianRequest g : req.guardians()) {
                try {
                    var guardian = provision(
                            stationId, g.email(), g.firstName(), g.lastName(), StationUserType.GUARDIAN, null);
                    provisioned.add(guardian);
                    stationMemberRepository.addManager(guardian.memberId(), parent.memberId());
                } catch (ProvisionException e) {
                    failed.add(new FailedInvite(g.email(), e.getMessage()));
                }
            }
        }
        return new BatchResult(provisioned, failed);
    }

    private boolean needsSetup(Account account) {
        return account.setupCompletedAt() == null
                && accountRepository.findCredential(account.id()).isEmpty();
    }

    /**
     * One row of a batch invite request.
     */
    public record InviteRequest(
            String email,
            String firstName,
            String lastName,
            StationUserType userType,
            Integer groupId,
            List<GuardianRequest> guardians) {}

    /**
     * Guardian sub-row inside a parent {@link InviteRequest}.
     */
    public record GuardianRequest(String email, String firstName, String lastName) {}

    /**
     * A member that exists after provisioning — freshly created or attached from an existing
     * account/membership.
     */
    public record ProvisionedMember(
            int memberId,
            int accountId,
            String email,
            String firstName,
            String lastName,
            StationUserType userType,
            boolean accountCreated,
            boolean membershipCreated) {}

    /**
     * An invite entry that could not be provisioned, with the reason.
     */
    public record FailedInvite(String email, String reason) {}

    /**
     * Outcome of {@link #createBatch(int, List)}.
     */
    public record BatchResult(List<ProvisionedMember> provisioned, List<FailedInvite> failed) {}

    /**
     * Thrown when a member cannot be provisioned — mapped by the routes layer onto the
     * appropriate HTTP response.
     */
    public static class ProvisionException extends RuntimeException {

        private ProvisionException(String message) {
            super(message);
        }

        static ProvisionException emailInUse(String email) {
            return new ProvisionException("Email already registered: " + email);
        }
    }
}
