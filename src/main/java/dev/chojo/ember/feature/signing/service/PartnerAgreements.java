/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.FederatedRegistrantService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService;
import dev.chojo.ember.feature.generator.service.DocumentGeneratorService;
import dev.chojo.ember.feature.generator.service.MemberNeutralTemplates;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.signing.entity.AgreementNoticeKind;
import dev.chojo.ember.feature.signing.entity.HandedOutAgreement;
import dev.chojo.ember.feature.signing.entity.PartnerAgreement;
import dev.chojo.ember.feature.signing.entity.PartnerAgreementState;
import dev.chojo.ember.feature.signing.entity.PartnerSigner;
import dev.chojo.ember.feature.signing.entity.PartnerSignerDocument;
import dev.chojo.ember.feature.signing.entity.RemoteAgreement;
import dev.chojo.ember.feature.signing.entity.RemoteAgreementNotice;
import dev.chojo.ember.feature.signing.repository.PartnerAgreementRepository;
import dev.chojo.ember.feature.signing.route.RemoteSigningRoutes;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.Json;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The documents a shared appointment asks the members of partner stations to sign, at the station holding the
 * appointment.
 *
 * <p><b>Handed out once per date.</b> A partner asks for the documents of a date when one of its members
 * registers. Each document the appointment asks for that asks the member side to sign and is about nobody in
 * particular ({@link MemberNeutralTemplates}) is drawn for that date the first time anybody asks, kept, and
 * handed to every partner alike; a new version of the template is drawn anew. A document that names a person
 * is never handed out, whatever was shared since it was checked, and its partners' members count as signature
 * missing.
 *
 * <p><b>What comes back.</b> The member signs at home. The home installation reports that it took the document
 * on, and sends every sealed state back, each in a request signed with its federation key. A sealed copy is
 * taken only when {@link PartnerSealValidator} accepts it as sealed by that partner, against the partner's
 * pinned authorities alone; it is kept locked beside the copies before. The station holding the appointment
 * never holds evidence about the partner's member beyond the copy itself.
 *
 * <p><b>Partners that cannot sign.</b> A partner whose installation never reports anything, because it is older,
 * cannot seal, or failed to take the document on, still registers its members. Each of them counts as
 * signature missing, and whoever manages the registrations can confirm a signed paper copy for them.
 *
 * <p><b>Retention.</b> A row is kept until the date of the appointment plus the months its template keeps signed
 * documents, or twelve where it sets none, whatever happens to the member at the partner; a daily sweep deletes
 * it with its copies afterwards.
 */
@Singleton
public class PartnerAgreements implements FederationServer, TaskSource {
    /** How long the copies of a template that sets no retention are kept after the appointment. */
    static final int DEFAULT_RETENTION_MONTHS = 12;

    private static final Duration START_DELAY = Duration.ofMinutes(25);
    private static final Duration INTERVAL = Duration.ofDays(1);
    private static final int SWEEP_LIMIT = 200;
    private static final Logger log = LoggerFactory.getLogger(PartnerAgreements.class);

    private final EventCrudService events;
    private final EventFederationService federation;
    private final FederatedRegistrantService registrants;
    private final OccurrenceCalendar calendar;
    private final AppointmentDocumentService appointments;
    private final MemberNeutralTemplates neutral;
    private final PartnerAgreementRepository agreements;
    private final PartnerSealValidator validator;
    private final FederationRepository partners;
    private final MemberNameResolver names;
    private final Clock clock;

    @Inject
    public PartnerAgreements(
            EventCrudService events,
            EventFederationService federation,
            FederatedRegistrantService registrants,
            OccurrenceCalendar calendar,
            AppointmentDocumentService appointments,
            MemberNeutralTemplates neutral,
            PartnerAgreementRepository agreements,
            PartnerSealValidator validator,
            FederationRepository partners,
            MemberNameResolver names) {
        this(
                events,
                federation,
                registrants,
                calendar,
                appointments,
                neutral,
                agreements,
                validator,
                partners,
                names,
                Clock.systemUTC());
    }

    /** Builds the service with its own clock, so a test moves past a retention. */
    PartnerAgreements(
            EventCrudService events,
            EventFederationService federation,
            FederatedRegistrantService registrants,
            OccurrenceCalendar calendar,
            AppointmentDocumentService appointments,
            MemberNeutralTemplates neutral,
            PartnerAgreementRepository agreements,
            PartnerSealValidator validator,
            FederationRepository partners,
            MemberNameResolver names,
            Clock clock) {
        this.events = events;
        this.federation = federation;
        this.registrants = registrants;
        this.calendar = calendar;
        this.appointments = appointments;
        this.neutral = neutral;
        this.agreements = agreements;
        this.validator = validator;
        this.partners = partners;
        this.names = names;
        this.clock = clock;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteSigningRoutes.AGREEMENTS,
                (partner, params, body) ->
                        handOut(partner, params.integer("eventId"), LocalDate.parse(params.text("date"))));
        endpoints.<RemoteAgreementNotice, Void>serve(RemoteSigningRoutes.AGREEMENT_NOTICE, (partner, params, body) -> {
            take(partner, params.integer("eventId"), body);
            return null;
        });
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "partner-agreement-retention-sweep", Schedule.fixedDelay(START_DELAY, INTERVAL), this::sweep));
    }

    /**
     * The documents a shared appointment asks the asking partner's members to sign on a date, each drawn once
     * for that date.
     *
     * @param partner the partnership the request arrived on
     * @param eventId the appointment
     * @param date    the date
     * @return the documents with their bytes, none where the appointment asks partners to sign nothing
     */
    List<RemoteAgreement> handOut(ServingPartner partner, int eventId, LocalDate date) {
        var event = sharedEvent(partner, eventId, EventRefusal.PARTNER_AGREEMENTS_NOT_SHARED);
        var day = calendar.dateToAnswerFor(event, date);
        return signable(event).stream()
                .map(template -> remote(template, handedOut(event, day, template)))
                .toList();
    }

    /**
     * Takes what a partner reports about a document for one of its members: taken on, or signed with a sealed
     * copy, which is kept only when its seal is the partner's.
     *
     * @param partner the partnership the request arrived on
     * @param eventId the appointment
     * @param notice  the report
     */
    void take(ServingPartner partner, int eventId, RemoteAgreementNotice notice) {
        var event = sharedEvent(partner, eventId, EventRefusal.PARTNER_AGREEMENT_NOTICE_NOT_SHARED);
        if (event.requiresRegistration()
                && registrationOf(partner.row(), event, notice).isEmpty()) {
            throw EventRefusal.PARTNER_AGREEMENT_NOTICE_NOT_REGISTERED.raise();
        }
        var handedOut = agreements
                .handedOut(eventId, notice.eventDate(), notice.templateId(), notice.contentSha256())
                .orElseThrow(EventRefusal.PARTNER_AGREEMENT_NOTICE_UNKNOWN_DOCUMENT::raise);
        var template = appointments.templatesAskedFor(eventId).stream()
                .filter(asked -> asked.id() == handedOut.templateId())
                .findFirst()
                .orElseThrow(EventRefusal.PARTNER_AGREEMENT_NOTICE_UNKNOWN_DOCUMENT::raise);
        var key = keyOf(event, notice.eventDate(), template, partner.row(), notice.memberUid());
        if (notice.kind() == AgreementNoticeKind.ASKED) {
            agreements.report(key, PartnerAgreementState.ASKED, handedOut.sha256(), false);
            log.info("Partner {} took document {} of appointment {} on", partner.partnerId(), template.id(), eventId);
            return;
        }
        byte[] sealed = sealedCopyOf(notice);
        var check = validator.validate(partner.row(), sealed);
        if (!check.accepted()) {
            log.warn(
                    "Refused a signed copy of document {} from partner {}: {}",
                    template.id(),
                    partner.partnerId(),
                    check.verdict());
            throw EventRefusal.PARTNER_AGREEMENT_SEAL_REFUSED.raise(
                    RefusalDetail.text(check.verdict().name()));
        }
        var state = notice.kind() == AgreementNoticeKind.WITHDRAWN
                ? PartnerAgreementState.WITHDRAWN
                : PartnerAgreementState.SIGNED;
        boolean kept = Transactions.call(() -> {
            int id = agreements.report(key, state, handedOut.sha256(), notice.complete());
            return agreements.addCopy(
                    id, Sha256.hex(sealed), sealed, Json.MAPPER.writeValueAsString(notice.fields()), notice.complete());
        });
        log.info(
                "Took a {} copy of document {} from partner {} for appointment {} on {} ({})",
                state,
                template.id(),
                partner.partnerId(),
                eventId,
                notice.eventDate(),
                kept ? "new" : "already kept");
    }

    /**
     * Where every member a partner registered for the date stands with every document the appointment asks
     * partners to sign.
     *
     * @param event the appointment, already checked to be the reader's station's
     * @param date  the date
     * @return each standing registration of a partner's member, the oldest first
     */
    public List<PartnerSigner> signers(StationEvent event, LocalDate date) {
        var asked = signable(event);
        if (asked.isEmpty()) return List.of();
        var recorded = agreements.forDate(event.id(), date);
        return federation.findRegistrations(event.id(), date).stream()
                .filter(EventFederationRegistration::isStanding)
                .map(registration -> signer(registration, asked, recorded))
                .toList();
    }

    /**
     * Confirms a signed paper copy of a document for a member a partner registered, where no sealed copy came
     * back from the partner.
     *
     * @param session        the reader, who manages the registrations of the appointment's station
     * @param event          the appointment, already checked to be the reader's station's
     * @param registrationId the partner's registration
     * @param templateId     the document
     * @return where the member stands with the document now
     */
    public PartnerSignerDocument confirmPaper(
            StationSession session, StationEvent event, int registrationId, int templateId) {
        int eventId = event.id();
        var registration = federation
                .findRegistrationById(registrationId)
                .filter(found -> found.eventId() == eventId && found.isStanding())
                .orElseThrow(DocumentRefusal.PARTNER_AGREEMENT_REGISTRATION_NOT_HERE::raise);
        var template = signable(event).stream()
                .filter(asked -> asked.id() == templateId)
                .findFirst()
                .orElseThrow(DocumentRefusal.PARTNER_AGREEMENT_NOT_ASKED::raise);
        var partner = partners.findPartnerById(registration.partnerId())
                .orElseThrow(DocumentRefusal.PARTNER_AGREEMENT_REGISTRATION_NOT_HERE::raise);
        var before = agreements.find(
                eventId,
                registration.eventDate(),
                templateId,
                partner.partnerStationId(),
                registration.remoteMemberId());
        if (before.filter(found -> found.state() == PartnerAgreementState.SIGNED)
                .isPresent()) {
            throw DocumentRefusal.PARTNER_AGREEMENT_ALREADY_SIGNED.raise();
        }
        int by = session.member().id();
        var key = keyOf(event, registration.eventDate(), template, partner, registration.remoteMemberId());
        int id = agreements.confirmPaper(key, by, names.official(by));
        log.info(
                "Member {} confirmed a paper copy of document {} for partner registration {}",
                by,
                templateId,
                registrationId);
        return documentOf(template, agreements.find(session.stationId(), id));
    }

    /**
     * The newest sealed copy of a document that came back from a partner.
     *
     * @param session     the reader, who manages the registrations of the appointment's station
     * @param agreementId the record of the document for the member
     * @return the sealed PDF, named after the document
     */
    public SealedPartnerCopy latestCopy(StationSession session, int agreementId) {
        var agreement = agreements
                .find(session.stationId(), agreementId)
                .orElseThrow(DocumentRefusal.PARTNER_AGREEMENT_COPY_NOT_HERE::raise);
        var pdf =
                agreements.latestCopy(agreementId).orElseThrow(DocumentRefusal.PARTNER_AGREEMENT_COPY_NOT_HERE::raise);
        return new SealedPartnerCopy(agreement.templateName() + ".pdf", pdf);
    }

    /**
     * A sealed copy that came back from a partner, for a download.
     *
     * <p>The array is handed over as it is, without a copy.
     *
     * @param fileName what the download is called
     * @param pdf      the sealed PDF
     */
    public record SealedPartnerCopy(String fileName, byte[] pdf) {}

    /**
     * Deletes the rows whose retention is over, with their copies.
     *
     * @return how many were deleted
     */
    int sweep() {
        int deleted = 0;
        for (int id : agreements.expired(clock.instant(), SWEEP_LIMIT)) {
            try {
                agreements.delete(id);
                deleted++;
            } catch (RuntimeException e) {
                log.warn("Could not delete partner agreement {} past its retention", id, e);
            }
        }
        if (deleted > 0) log.info("Deleted {} partner agreements past their retention", deleted);
        return deleted;
    }

    private PartnerSigner signer(
            EventFederationRegistration registration, List<DocumentTemplate> asked, List<PartnerAgreement> recorded) {
        var partnerUid = partners.findPartnerById(registration.partnerId())
                .map(FederationPartner::partnerStationId)
                .orElse(null);
        var documents = asked.stream()
                .map(template -> documentOf(
                        template,
                        recorded.stream()
                                .filter(found -> Objects.equals(found.templateId(), template.id())
                                        && found.partnerStationUid().equals(partnerUid)
                                        && found.remoteMemberId().equals(registration.remoteMemberId()))
                                .findFirst()))
                .toList();
        return new PartnerSigner(registration.id(), registrants.identify(registration), documents);
    }

    private static PartnerSignerDocument documentOf(DocumentTemplate template, Optional<PartnerAgreement> recorded) {
        if (recorded.isEmpty()) {
            return new PartnerSignerDocument(
                    template.id(), template.name(), PartnerAgreementState.MISSING, false, null, 0, null);
        }
        var found = recorded.get();
        return new PartnerSignerDocument(
                template.id(),
                template.name(),
                found.state(),
                found.complete(),
                found.id(),
                found.copies(),
                found.confirmedByName());
    }

    private List<DocumentTemplate> signable(StationEvent event) {
        return appointments.templatesAskedFor(event.id()).stream()
                .filter(neutral::signableByPartners)
                .toList();
    }

    private HandedOutAgreement handedOut(StationEvent event, LocalDate date, DocumentTemplate template) {
        var kept = agreements.handedOut(event.id(), date, template.id(), template.version());
        if (kept.isPresent()) return kept.get();
        DocumentGeneratorService.Rendered drawn = appointments.drawForPartners(event, date, template);
        agreements.handOut(
                event.stationId(),
                new HandedOutAgreement(
                        0,
                        event.id(),
                        date,
                        template.id(),
                        template.version(),
                        drawn.title(),
                        drawn.fileName(),
                        drawn.pdf(),
                        Sha256.hex(drawn.pdf())));
        log.info("Drew document {} of appointment {} on {} for partners", template.id(), event.id(), date);
        return agreements
                .handedOut(event.id(), date, template.id(), template.version())
                .orElseThrow();
    }

    private RemoteAgreement remote(DocumentTemplate template, HandedOutAgreement handedOut) {
        var signing = template.signing();
        return new RemoteAgreement(
                template.id(),
                handedOut.templateVersion(),
                handedOut.title(),
                handedOut.fileName(),
                template.language(),
                appointments.statementsForPartners(template).byField(),
                signing.retentionMonths(),
                signing.copyAttached(),
                handedOut.sha256(),
                Base64.getEncoder().encodeToString(handedOut.content()));
    }

    private Optional<EventFederationRegistration> registrationOf(
            FederationPartner partner, StationEvent event, RemoteAgreementNotice notice) {
        return federation
                .findRegistration(event.id(), partner.id(), notice.memberUid(), notice.eventDate())
                .filter(EventFederationRegistration::isStanding);
    }

    private PartnerAgreement.Key keyOf(
            StationEvent event, LocalDate date, DocumentTemplate template, FederationPartner partner, UUID member) {
        Integer months = template.signing().retentionMonths();
        int kept = months == null ? DEFAULT_RETENTION_MONTHS : months;
        Instant retainUntil =
                date.plusMonths(kept).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new PartnerAgreement.Key(
                event.stationId(),
                event.id(),
                date,
                template.id(),
                template.name(),
                partner.id(),
                partner.partnerStationId(),
                partner.partnerStationName(),
                member,
                months,
                retainUntil);
    }

    private static byte[] sealedCopyOf(RemoteAgreementNotice notice) {
        String encoded = notice.sealedPdf();
        if (encoded == null || encoded.isBlank()) throw EventRefusal.PARTNER_AGREEMENT_COPY_UNREADABLE.raise();
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw EventRefusal.PARTNER_AGREEMENT_COPY_UNREADABLE.raise();
        }
    }

    private StationEvent sharedEvent(ServingPartner partner, int eventId, Refusal notShared) {
        if (!federation
                .findSharedEventIds(partner.partnerId(), partner.servingStationId())
                .contains(eventId)) {
            throw notShared.raise();
        }
        return events.findById(eventId).orElseThrow(notShared::raise);
    }
}
