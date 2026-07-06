/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.auth.PasswordPolicy;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMemberInvite;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberInviteRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Coordinates the per-recipient email invite flow used by the setup wizard's "invites" step and
 * the regular members screen. The administrator submits a list of recipients; the service
 * generates tokens, persists invite rows, and sends one email per recipient via the instance
 * mail relay so the flow works on stations that have not configured outbound mail yet.
 *
 * <p>Guardian relations are wired at acceptance time. The order in which the parent and guardian
 * accept does not matter — whichever side accepts last triggers the linking.
 */
@Singleton
public class StationMemberInviteService {

    private static final Logger log = LoggerFactory.getLogger(StationMemberInviteService.class);
    private static final Duration DEFAULT_EXPIRY = Duration.ofDays(14);
    private static final int TOKEN_BYTES = 32;
    private static final DateTimeFormatter HUMAN_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));

    private final StationMemberInviteRepository inviteRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final EmailService emailService;
    private final PasswordHasher passwordHasher;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public StationMemberInviteService(
            StationMemberInviteRepository inviteRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            EmailService emailService,
            PasswordHasher passwordHasher) {
        this.inviteRepository = inviteRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.emailService = emailService;
        this.passwordHasher = passwordHasher;
    }

    /**
     * Persists a single member invite row and emails the recipient. Used directly by the routes
     * layer for both flat lists of invites and for the {@link #createBatch} expansion of nested
     * guardians.
     */
    public StationMemberInvite invite(
            int stationId,
            int invitedByMemberId,
            String email,
            String firstName,
            String lastName,
            StationUserType userType,
            Integer groupId,
            Integer guardianOfInviteId) {
        Instant expiresAt = Instant.now().plus(DEFAULT_EXPIRY);
        String token = generateToken();
        var invite = inviteRepository.create(
                stationId,
                token,
                email.trim(),
                firstName,
                lastName,
                userType,
                groupId,
                guardianOfInviteId,
                invitedByMemberId,
                expiresAt);
        log.info(
                "Station member invite created: invite={}, station={}, userType={}, invitedBy={}",
                invite.id(),
                stationId,
                userType,
                invitedByMemberId);
        sendInviteEmail(invite, invitedByMemberId);
        return invite;
    }

    /**
     * Persists a batch of invites, expanding nested guardian sub-lists into separate guardian
     * invites that point back at the member invite. Each entry is processed independently —
     * failures inside one entry do not affect the rest, but each guardian sub-invite is created
     * after its parent so the {@code guardian_of_invite_id} pointer is valid.
     */
    public List<StationMemberInvite> createBatch(int stationId, int invitedByMemberId, List<InviteRequest> requests) {
        var created = new ArrayList<StationMemberInvite>();
        for (InviteRequest req : requests) {
            var parent = invite(
                    stationId,
                    invitedByMemberId,
                    req.email(),
                    req.firstName(),
                    req.lastName(),
                    req.userType() != null ? req.userType() : StationUserType.MEMBER,
                    req.groupId(),
                    null);
            created.add(parent);
            if (req.guardians() != null) {
                for (GuardianRequest g : req.guardians()) {
                    created.add(invite(
                            stationId,
                            invitedByMemberId,
                            g.email(),
                            g.firstName(),
                            g.lastName(),
                            StationUserType.GUARDIAN,
                            null,
                            parent.id()));
                }
            }
        }
        return created;
    }

    /**
     * Lists every invite for the station, regardless of status.
     */
    public List<StationMemberInvite> listForStation(int stationId) {
        return inviteRepository.findByStation(stationId);
    }

    /**
     * Revokes a pending invite. Accepted invites are kept for audit purposes.
     */
    public boolean revoke(int stationId, int inviteId) {
        var invite = inviteRepository.findById(inviteId).orElse(null);
        if (invite == null || invite.stationId() != stationId) {
            log.warn("Station member invite revoke missed: invite={}, station={}", inviteId, stationId);
            return false;
        }
        if (invite.isAccepted()) {
            log.warn(
                    "Station member invite revoke rejected for accepted invite: invite={}, station={}",
                    inviteId,
                    stationId);
            return false;
        }
        boolean deleted = inviteRepository.delete(inviteId);
        if (deleted) {
            log.info("Station member invite revoked: invite={}, station={}", inviteId, stationId);
        } else {
            log.warn("Station member invite revoke affected no rows: invite={}, station={}", inviteId, stationId);
        }
        return deleted;
    }

    /**
     * Returns the invite with the given token. Callers must check {@link StationMemberInvite#isExpired()}
     * and {@link StationMemberInvite#isAccepted()} before allowing acceptance.
     */
    public Optional<StationMemberInvite> findByToken(String token) {
        return inviteRepository.findByToken(token);
    }

    /**
     * Realises a pending invite by creating the account, the station membership, the optional
     * group membership, and any guardian-of relation implied by the invite tree. The recipient
     * supplies their password as part of this call.
     *
     * @return the new account identifier
     * @throws AcceptException if the invite is missing/expired/accepted, the password is too
     *                         weak, or the recipient's email already belongs to another account
     */
    public AcceptResult accept(String token, String password) {
        var invite = inviteRepository.findByToken(token).orElseThrow(AcceptException::notFound);
        if (invite.isAccepted()) throw AcceptException.alreadyAccepted();
        if (invite.isExpired()) throw AcceptException.expired();
        if (PasswordPolicy.validate(password) != PasswordPolicy.Result.OK) {
            throw AcceptException.weakPassword();
        }
        if (accountRepository.findByEmail(invite.email()).isPresent()) {
            throw AcceptException.emailInUse();
        }

        var account = accountRepository.create(
                invite.email(), invite.firstName(), invite.lastName(), true, invite.stationId());
        accountRepository.createCredential(account.id(), passwordHasher.hash(password));

        var newMember = stationMemberRepository.create(invite.stationId(), account.id());
        stationMemberRepository.setUserType(newMember.id(), invite.userType());

        if (invite.groupId() != null) {
            memberGroupRepository.addMember(invite.groupId(), newMember.id());
        }

        if (invite.guardianOfInviteId() != null) {
            tryLinkGuardian(invite, newMember.id());
        } else {
            for (var pending : inviteRepository.findGuardianInvitesFor(invite.id())) {
                if (pending.isAccepted() && pending.acceptedAccountId() != null) {
                    var guardianMember = stationMemberRepository
                            .findByStationAndAccount(invite.stationId(), pending.acceptedAccountId())
                            .orElse(null);
                    if (guardianMember != null) {
                        stationMemberRepository.addManager(guardianMember.id(), newMember.id());
                    }
                }
            }
        }

        inviteRepository.markAccepted(invite.id(), account.id());
        log.info("Station member invite {} accepted by account {}", invite.id(), account.id());
        return new AcceptResult(account.id(), newMember.id(), invite.stationId());
    }

    private void tryLinkGuardian(StationMemberInvite guardianInvite, int guardianMemberId) {
        var parent =
                inviteRepository.findById(guardianInvite.guardianOfInviteId()).orElse(null);
        if (parent == null || !parent.isAccepted() || parent.acceptedAccountId() == null) return;
        var managedMember = stationMemberRepository
                .findByStationAndAccount(parent.stationId(), parent.acceptedAccountId())
                .orElse(null);
        if (managedMember == null) return;
        stationMemberRepository.addManager(guardianMemberId, managedMember.id());
    }

    private void sendInviteEmail(StationMemberInvite invite, int invitedByMemberId) {
        var station = stationRepository.findById(invite.stationId()).orElse(null);
        var inviterMember = stationMemberRepository.findById(invitedByMemberId).orElse(null);
        var inviterAccount = inviterMember != null && inviterMember.accountId() != null
                ? accountRepository.findById(inviterMember.accountId()).orElse(null)
                : null;
        String stationName = station != null ? station.name() : "";
        String inviterName =
                inviterAccount != null ? (inviterAccount.firstName() + " " + inviterAccount.lastName()).trim() : "";
        String locale =
                station != null && station.locale() != null && station.locale().startsWith("de") ? "de" : "en";
        String fullName = (invite.firstName() + " " + invite.lastName()).trim();
        emailService.sendStationMemberInviteEmail(
                invite.email(),
                fullName,
                inviterName,
                stationName,
                invite.token(),
                HUMAN_DATE.format(invite.expiresAt()),
                locale,
                invite.stationId());
    }

    private String generateToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
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
     * Outcome of {@link #accept(String, String)}.
     */
    public record AcceptResult(int accountId, int memberId, int stationId) {}

    /**
     * Thrown when an invite cannot be realised — wrapped by the routes layer into the appropriate
     * HTTP response.
     */
    public static class AcceptException extends RuntimeException {
        /** Discriminator for the routes layer to map onto the right HTTP status. */
        public enum Reason {
            NOT_FOUND,
            ALREADY_ACCEPTED,
            EXPIRED,
            WEAK_PASSWORD,
            EMAIL_IN_USE
        }

        private final Reason reason;

        private AcceptException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }

        static AcceptException notFound() {
            return new AcceptException(Reason.NOT_FOUND, "Invite not found");
        }

        static AcceptException alreadyAccepted() {
            return new AcceptException(Reason.ALREADY_ACCEPTED, "Invite already accepted");
        }

        static AcceptException expired() {
            return new AcceptException(Reason.EXPIRED, "Invite has expired");
        }

        static AcceptException weakPassword() {
            return new AcceptException(Reason.WEAK_PASSWORD, "Password does not meet policy");
        }

        static AcceptException emailInUse() {
            return new AcceptException(Reason.EMAIL_IN_USE, "Email already registered");
        }
    }
}
