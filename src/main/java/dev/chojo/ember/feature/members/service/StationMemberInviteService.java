/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.accountlink.service.AccountLinkService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
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
 * <p>An email that already belongs to an account is not attached: the person behind it never agreed
 * to join this station. The member is created without an account and the person is asked to link
 * theirs ({@link AccountLinkService}); until they accept, the station cannot reach the account. An
 * account that is already a member here stays the member it is. Only the instance administration
 * naming a station's manager attaches an existing account directly. Synthetic addresses ending in
 * {@code .local} (members without login) never match existing accounts and never receive mail.
 *
 * <p>Guardian relations are wired at creation time - both accounts exist immediately, so the
 * manager link between guardian and member is set as part of the same request.
 */
@Singleton
public class StationMemberInviteService {

    private static final Logger log = LoggerFactory.getLogger(StationMemberInviteService.class);
    private final StationMemberRepository stationMemberRepository;
    private final GroupMembershipService groupMemberships;
    private final AccountInviteService accountInviteService;
    private final AccountLinkService linkService;

    @Inject
    public StationMemberInviteService(
            StationMemberRepository stationMemberRepository,
            GroupMembershipService groupMemberships,
            AccountInviteService accountInviteService,
            AccountLinkService linkService) {
        this.stationMemberRepository = stationMemberRepository;
        this.groupMemberships = groupMemberships;
        this.accountInviteService = accountInviteService;
        this.linkService = linkService;
    }

    /**
     * Provisions a single member: creates the account, or asks the owner of an account that already
     * carries the address, creates the station membership if absent, and sends the password-setup
     * email when a new account needs one and the mail was asked to go now. The user type and group are
     * only applied to memberships created by this call - existing members keep their configuration.
     *
     * <p>No address given means the member has none, and none is written down for them. Nobody is
     * given a made-up one: an address that looks real and can never be delivered to shows in every
     * list as though somebody could be written to there, and has to be explained to whoever reads
     * it. Such a member is reached through the guardians who answer for them, or not at all.
     *
     * @param invitedBy the member who invites, or null where nobody of the station does
     * @throws ProvisionException if a made-up {@code .local} address already belongs to somebody
     */
    public ProvisionedMember provision(
            int stationId,
            @Nullable String email,
            String firstName,
            String lastName,
            StationUserType userType,
            @Nullable Integer groupId,
            SetupMail setupMail,
            @Nullable Integer invitedBy) {
        if (email != null && !email.isBlank()) {
            Account existing = existingAccount(stationId, email);
            if (existing != null
                    && stationMemberRepository
                            .findByStationAndAccount(stationId, existing.id())
                            .isEmpty()) {
                return askToLink(stationId, existing, firstName, lastName, userType, groupId, invitedBy);
            }
        }
        return provisionAttached(stationId, email, firstName, lastName, userType, groupId, setupMail);
    }

    /**
     * Provisions a member the way the instance administration does when it names a station's manager:
     * an account that already carries the address is attached directly, because the administration of
     * the instance reaches every account anyway and a station without its manager is of no use.
     *
     * @throws ProvisionException if a made-up {@code .local} address already belongs to somebody
     */
    public ProvisionedMember provisionAttached(
            int stationId,
            @Nullable String email,
            String firstName,
            String lastName,
            StationUserType userType,
            @Nullable Integer groupId,
            SetupMail setupMail) {
        AccountInviteService.Invited invited;
        try {
            invited = email == null || email.isBlank()
                    ? accountInviteService.createWithoutAddress(stationId, firstName, lastName)
                    : accountInviteService.resolveOrCreate(stationId, email, firstName, lastName, setupMail);
        } catch (AccountInviteService.EmailInUseException e) {
            throw ProvisionException.emailInUse(email == null ? "" : email.trim());
        }
        Account account = invited.account();
        boolean accountCreated = invited.created();

        var member = stationMemberRepository
                .findByStationAndAccount(stationId, account.id())
                .orElse(null);
        boolean membershipCreated = member == null;
        if (member == null) {
            member = stationMemberRepository.create(stationId, account.id());
            stationMemberRepository.setUserType(member.id(), userType);
            if (groupId != null) {
                groupMemberships.joinAutomatically(groupId, member.id());
            }
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
                membershipCreated,
                false);
    }

    private @Nullable Account existingAccount(int stationId, String email) {
        try {
            return accountInviteService.existing(stationId, email).orElse(null);
        } catch (AccountInviteService.EmailInUseException e) {
            throw ProvisionException.emailInUse(email.trim());
        }
    }

    /**
     * Creates the member without the account the address belongs to and asks its owner to link it.
     * Nothing of the account is read into the member beyond the address the station typed itself.
     */
    private ProvisionedMember askToLink(
            int stationId,
            Account existing,
            String firstName,
            String lastName,
            StationUserType userType,
            @Nullable Integer groupId,
            @Nullable Integer invitedBy) {
        var member = waitingMember(stationId, existing, firstName, lastName, invitedBy);
        stationMemberRepository.setUserType(member.id(), userType);
        if (groupId != null) {
            groupMemberships.joinAutomatically(groupId, member.id());
        }
        return new ProvisionedMember(
                member.id(), null, existing.email(), firstName, lastName, userType, false, true, true);
    }

    private StationMember waitingMember(
            int stationId, Account existing, String firstName, String lastName, @Nullable Integer invitedBy) {
        String name = (firstName.trim() + " " + lastName.trim()).trim();
        var member = stationMemberRepository.createWithoutAccount(stationId, name);
        linkService.ask(stationId, member.id(), existing.id(), LinkOrigin.INVITE, invitedBy);
        log.info(
                "Member {} at station {} waits for account {} to accept the link",
                member.id(),
                stationId,
                existing.id());
        return member;
    }

    /**
     * A new member for an address the station entered somewhere other than the invite form, such as a
     * row of a member list it reads in or a guardian a waiting list names. The same rule as
     * {@link #provision} applies: an account that already carries the address is not attached, its
     * owner is asked instead, and the member waits without it. The caller sets the rest of the member up.
     *
     * @param stationId the station
     * @param email     the address, or blank for somebody without one
     * @param firstName their first name
     * @param lastName  their last name
     * @param setupMail whether the setup mail of a new account leaves now
     * @param invitedBy the member who enters them, or null where nobody of the station does
     * @return the member, with or without an account
     * @throws AccountInviteService.EmailInUseException when a made-up address already belongs to somebody
     */
    public StationMember newMember(
            int stationId,
            String email,
            String firstName,
            String lastName,
            SetupMail setupMail,
            @Nullable Integer invitedBy) {
        if (email.isBlank()) {
            var invited = accountInviteService.createWithoutAddress(stationId, firstName, lastName);
            return stationMemberRepository.create(stationId, invited.account().id());
        }
        var existing = accountInviteService.existing(stationId, email).orElse(null);
        if (existing != null
                && stationMemberRepository
                        .findByStationAndAccount(stationId, existing.id())
                        .isEmpty()) {
            return waitingMember(stationId, existing, firstName, lastName, invitedBy);
        }
        var invited = accountInviteService.resolveOrCreate(stationId, email, firstName, lastName, setupMail);
        return stationMemberRepository.create(stationId, invited.account().id());
    }

    /**
     * Provisions a batch of invite entries, expanding nested guardian sub-lists. Guardians are
     * provisioned as {@link StationUserType#GUARDIAN} and linked as manager of the member they
     * belong to. Entries are processed independently - a failing entry does not affect the rest;
     * failed entries are reported in the result.
     *
     * <p>The groups chosen for the entries are checked before anybody is invited: each must be one of
     * the station's and grant nothing the inviting person does not hold, because putting somebody into
     * a group is granting them what it grants.
     */
    public BatchResult createBatch(int stationId, List<InviteRequest> requests, SetupMail setupMail, UserSession by) {
        requests.stream()
                .map(InviteRequest::groupId)
                .distinct()
                .forEach(groupId -> groupMemberships.requireInvitableInto(stationId, groupId, by));
        StationMember inviter = by.member();
        Integer invitedBy = inviter == null ? null : inviter.id();
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
                        req.groupId(),
                        setupMail,
                        invitedBy);
                provisioned.add(parent);
            } catch (ProvisionException e) {
                failed.add(new FailedInvite(req.email(), e.getMessage()));
                continue;
            }
            if (req.guardians() == null) continue;
            for (GuardianRequest g : req.guardians()) {
                try {
                    var guardian = provision(
                            stationId,
                            g.email(),
                            g.firstName(),
                            g.lastName(),
                            StationUserType.GUARDIAN,
                            null,
                            setupMail,
                            invitedBy);
                    provisioned.add(guardian);
                    stationMemberRepository.addManager(guardian.memberId(), parent.memberId());
                } catch (ProvisionException e) {
                    failed.add(new FailedInvite(g.email(), e.getMessage()));
                }
            }
        }
        log.info(
                "Batch invite for station {}: {} of {} request(s) took, {} entry(s) refused",
                stationId,
                provisioned.size(),
                requests.size(),
                failed.size());
        return new BatchResult(provisioned, failed);
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
     * A member that exists after provisioning - freshly created, found at the station already, or
     * waiting for the owner of an existing account to link it.
     *
     * @param accountId   the account, or null while the member waits for a link or has no account
     * @param email       the address the account carries, or the one the station typed for a member
     *                    that waits for a link
     * @param linkPending whether the member waits for the owner of an existing account to accept
     */
    public record ProvisionedMember(
            int memberId,
            @Nullable Integer accountId,
            @Nullable String email,
            String firstName,
            String lastName,
            StationUserType userType,
            boolean accountCreated,
            boolean membershipCreated,
            boolean linkPending) {}

    /**
     * An invite entry that could not be provisioned, with the reason.
     */
    public record FailedInvite(String email, String reason) {}

    /**
     * Outcome of {@link #createBatch(int, List, SetupMail, UserSession)}.
     */
    public record BatchResult(List<ProvisionedMember> provisioned, List<FailedInvite> failed) {}

    /**
     * Thrown when a member cannot be provisioned - mapped by the routes layer onto the
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
