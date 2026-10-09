/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.signing.entity.AppointmentCopy;
import dev.chojo.ember.feature.signing.entity.DocumentAgreement;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignatureWithdrawal;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Withdrawing a signed agreement.
 *
 * <p>Whoever may ({@link WithdrawalRights}) withdraws an agreement as a whole: the fields still open on it
 * are withdrawn, the request turns revoked, and the withdrawal is recorded with who, when, from where and
 * why. What was signed stays: the versions of the document that carry the signatures stay filed and valid,
 * and the withdrawal is sealed into a version of its own after them ({@link SigningStateSealer}), whose record
 * page says the agreement was withdrawn. A version whose sealing fails is sealed by
 * {@link SigningStateSweeper}. An agreement only confirmed on paper has no sealed version to carry its
 * withdrawal, which then stands recorded with the request alone.
 *
 * <p>Where an appointment asked for the agreement, the requirement opens again, and a scan confirmed for it
 * is withdrawn with it, so a new one can be handed in. On an appointment that takes
 * registrations, the member's standing registration for the date is flagged and the agreement is asked for
 * anew on a fresh copy, so it is among the open signatures again; on one that takes none, the agreement is
 * offered again on the appointment's page. Whoever runs the appointment, or whoever asked for the signatures,
 * is told ({@link SignatureNotices}). An agreement signed here for a partner station's appointment reaches its
 * organiser with the sealed version that records the withdrawal, which travels like every sealed state
 * ({@link PartnerDeliveries}).
 */
@Singleton
public class SignatureWithdrawals {
    private static final Logger log = LoggerFactory.getLogger(SignatureWithdrawals.class);

    /** The longest reason a withdrawal takes. */
    public static final int MAX_REASON_LENGTH = 500;

    private final SignatureRequestRepository requests;
    private final SignatureRequestService requestService;
    private final WithdrawalRights rights;
    private final MemberNameResolver names;
    private final SigningStateSealer sealer;
    private final SignatureNotices notices;
    private final EventRepository events;
    private final EventRegistrationRepository registrations;
    private final AppointmentSignatures appointments;
    private final Clock clock;

    @Inject
    public SignatureWithdrawals(
            SignatureRequestRepository requests,
            SignatureRequestService requestService,
            WithdrawalRights rights,
            MemberNameResolver names,
            SigningStateSealer sealer,
            SignatureNotices notices,
            EventRepository events,
            EventRegistrationRepository registrations,
            AppointmentSignatures appointments) {
        this(
                requests,
                requestService,
                rights,
                names,
                sealer,
                notices,
                events,
                registrations,
                appointments,
                Clock.systemUTC());
    }

    /**
     * @param clock the clock that dates each withdrawal
     */
    SignatureWithdrawals(
            SignatureRequestRepository requests,
            SignatureRequestService requestService,
            WithdrawalRights rights,
            MemberNameResolver names,
            SigningStateSealer sealer,
            SignatureNotices notices,
            EventRepository events,
            EventRegistrationRepository registrations,
            AppointmentSignatures appointments,
            Clock clock) {
        this.requests = requests;
        this.requestService = requestService;
        this.rights = rights;
        this.names = names;
        this.sealer = sealer;
        this.notices = notices;
        this.events = events;
        this.registrations = registrations;
        this.appointments = appointments;
        this.clock = clock;
    }

    /**
     * Withdraws a signed agreement of a request at the reader's station.
     *
     * @param session      who withdraws it
     * @param requestUid   the request whose agreement it is
     * @param reason       why, or null; blank counts as none
     * @param circumstances where the withdrawal came from
     * @return the withdrawal as recorded
     */
    public SignatureWithdrawal requireOwnedThenWithdraw(
            StationSession session, UUID requestUid, @Nullable String reason, SigningCircumstances circumstances) {
        String why = reasonOf(reason);
        var request = requestService.requestAt(session, requestUid);
        int me = session.member().id();
        var withdrawal = Transactions.call(() -> {
            var held = requests.lockRequest(request.id()).orElseThrow(DocumentRefusal.SIGNING_REQUEST_NOT_FOUND::raise);
            var fields = requests.fieldsOf(held.id());
            rights.requireMayWithdraw(session, held, fields);
            requests.withdrawOpen(held.id(), me, names.official(me));
            var revoked = requests.revoke(new SignatureWithdrawal.Draft(
                    held.id(),
                    held.memberId(),
                    me,
                    names.official(me),
                    rights.capacityOf(session, held),
                    why,
                    clock.instant(),
                    truncated(circumstances.clientIp()),
                    circumstances.userAgent()));
            requests.appointmentOf(held.id()).ifPresent(appointments::withdrawPaper);
            return revoked;
        });
        log.info("Agreement of signing request {} withdrawn by member {}", request.uid(), me);
        notices.settled(withdrawnFieldsOf(request.id()));
        seal(request);
        var copy = requests.appointmentOf(request.id()).orElse(null);
        if (copy != null) reopen(copy, withdrawal);
        // TODO ask a partner appointment's agreement anew at home once it was withdrawn here
        var revoked = requests.findById(request.id()).orElse(request);
        notices.withdrawn(revoked, withdrawal, copy == null ? null : copy.eventId());
        return withdrawal;
    }

    /**
     * The agreements signed on a member document that the reader signed or may act for: each with where it
     * stands and whether the reader may withdraw it now.
     *
     * @param session    the reader
     * @param documentId the member document
     * @return the agreements, newest first; none where the reader is no party to any
     */
    public List<DocumentAgreement> requireOwnedAgreements(StationSession session, int documentId) {
        return requests.onDocument(session.stationId(), documentId).stream()
                .map(request -> agreement(session, request, requests.fieldsOf(request.id())))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<DocumentAgreement> agreement(
            StationSession session, SignatureRequest request, List<RequestedSignature> fields) {
        boolean withdrawable = rights.mayWithdraw(session, request, fields);
        var withdrawal = request.state() == RequestState.REVOKED
                ? requests.withdrawalOf(request.id()).orElse(null)
                : null;
        boolean party = withdrawable || (withdrawal != null && rights.isParty(session, request, fields));
        if (!party) return Optional.empty();
        return Optional.of(new DocumentAgreement(
                request.uid(),
                request.state(),
                withdrawable,
                withdrawal == null ? null : withdrawal.withdrawnAt(),
                withdrawal == null ? null : withdrawal.withdrawnByName()));
    }

    private void seal(SignatureRequest request) {
        try {
            sealer.sealLatest(request.id());
        } catch (RuntimeException e) {
            log.warn("Could not seal the withdrawal of signing request {}; the sweep tries again", request.uid(), e);
        }
    }

    /**
     * Opens the requirement of an appointment again: flags the standing registration and asks anew on an
     * appointment that takes registrations. One that takes none offers the agreement on its page again.
     */
    private void reopen(AppointmentCopy copy, SignatureWithdrawal withdrawal) {
        var event = events.findById(copy.eventId()).orElse(null);
        if (event == null || !event.requiresRegistration()) return;
        try {
            registrations.flagAgreementWithdrawn(
                    copy.eventId(), copy.eventDate(), copy.memberId(), withdrawal.withdrawnAt());
            appointments.askAnew(copy.eventId(), copy.eventDate(), copy.memberId(), copy.templateId());
        } catch (RuntimeException e) {
            log.warn(
                    "Could not open the agreement of member {} for appointment {} on {} again",
                    copy.memberId(),
                    copy.eventId(),
                    copy.eventDate(),
                    e);
        }
    }

    private List<Integer> withdrawnFieldsOf(int requestId) {
        return requests.fieldsOf(requestId).stream()
                .filter(field -> field.state() == FieldState.WITHDRAWN)
                .map(RequestedSignature::id)
                .toList();
    }

    private static @Nullable String reasonOf(@Nullable String reason) {
        if (reason == null || reason.isBlank()) return null;
        String trimmed = reason.strip();
        if (trimmed.length() > MAX_REASON_LENGTH) throw DocumentRefusal.SIGNATURE_WITHDRAWAL_REASON_TOO_LONG.raise();
        return trimmed;
    }

    private static @Nullable String truncated(@Nullable String clientIp) {
        if (clientIp == null) return null;
        try {
            return ConsentService.anonymizeIp(InetAddress.ofLiteral(clientIp));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
