/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.PaperState;
import dev.chojo.ember.feature.generator.entity.PaperSubmission;
import dev.chojo.ember.feature.generator.repository.PaperSubmissionRepository;
import dev.chojo.ember.feature.generator.repository.PaperSubmissionRepository.Subject;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.util.sql.Transactions;
import io.javalin.http.UploadedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Scans of signed paper copies of the documents an appointment asks for: handed in by the participant
 * or a guardian who acts for them, and confirmed or turned down by whoever manages the registrations.
 *
 * <p>Handing a scan in needs no right to upload documents: whoever may fetch the participant's copy may
 * hand in its signed scan, which is filed in the participant's documents like the copy itself. A
 * manager of the registrations may hand one in for any participant, and theirs counts as confirmed at
 * once. While a scan waits, a new one replaces it and the waiting one is deleted, since it was filed
 * for nothing else, and it can be taken back the same way; once one is confirmed, no further scan is taken
 * until the agreement it was confirmed for is withdrawn. A scan turned down stays filed and
 * on record with its reason, the participant and their guardians are told, and the document is open
 * again.
 *
 * <p>The scan is reached here through the appointment, so a manager of the registrations can read it
 * without the right to read member documents.
 *
 * <p>While a scan waits, the signatures still open on the participant's copy are asked of nobody, and a scan
 * turned down asks for them again. Once a scan counts as confirmed, they are settled as signed on paper
 * ({@link ScanConfirmations}).
 */
@Singleton
public class PaperSubmissionService {
    private static final Logger log = LoggerFactory.getLogger(PaperSubmissionService.class);

    /** The longest reason a scan can be turned down with. */
    static final int MAX_REASON_LENGTH = 300;

    private final PaperSubmissionRepository submissions;
    private final AppointmentDocumentService appointments;
    private final DocumentTemplateService templates;
    private final DocumentService documents;
    private final DocumentCatalogService catalog;
    private final MemberNameResolver names;
    private final Notifier notifier;
    private final ScanConfirmations confirmations;

    @Inject
    public PaperSubmissionService(
            PaperSubmissionRepository submissions,
            AppointmentDocumentService appointments,
            DocumentTemplateService templates,
            DocumentService documents,
            DocumentCatalogService catalog,
            MemberNameResolver names,
            Notifier notifier,
            ScanConfirmations confirmations) {
        this.confirmations = confirmations;
        this.submissions = submissions;
        this.appointments = appointments;
        this.templates = templates;
        this.documents = documents;
        this.catalog = catalog;
        this.names = names;
        this.notifier = notifier;
    }

    /**
     * A scan as it is served: the file it was filed as and its bytes.
     *
     * @param document the filed scan
     * @param data     its bytes
     */
    public record Scan(Document document, byte[] data) {}

    /**
     * Hands in the scan of a signed paper copy for a participant and files it in their documents.
     *
     * @param session    the reader: the participant, their guardian or a manager of the registrations
     * @param event      the appointment, already checked to be one the reader may see
     * @param date       the date of the appointment
     * @param templateId the document asked for
     * @param memberId   the participant
     * @param title      what to call the filed scan, or null or blank to call it after the document
     * @param file       the uploaded scan, or null where the request carried none
     * @return the submission, confirmed already where a manager of the registrations handed it in
     */
    public PaperSubmission submit(
            StationSession session,
            StationEvent event,
            LocalDate date,
            int templateId,
            int memberId,
            @Nullable String title,
            @Nullable UploadedFile file) {
        boolean manages = session.hasPermission(StationPermission.EVENT_REGISTRATION);
        appointments.requireMayHandIn(session, event, date, memberId, manages);
        var template = appointments.requireAsked(session.stationId(), event.id(), templateId);
        var subject = new Subject(session.stationId(), event.id(), date, templateId, memberId);
        requireNotConfirmed(submissions.lockStanding(subject));
        String name = title == null || title.isBlank() ? template.name() : title.strip();
        int me = session.member().id();
        var scan = documents.file(
                session.stationId(),
                new DocumentService.Filing(
                        List.of(memberId), name, false, template.keepOnArchive(), Uploader.member(me), template.tags()),
                file,
                DocumentDoor.STATION);
        Handover handover;
        try {
            handover = Transactions.call(() -> {
                var standing = submissions.lockStanding(subject);
                requireNotConfirmed(standing);
                standing.ifPresent(replaced -> submissions.delete(replaced.id()));
                var created = submissions
                        .create(subject, scan.id(), me, manages)
                        .orElseThrow(DocumentRefusal.DOCUMENT_SCAN_HANDED_IN_AT_ONCE::raise);
                return new Handover(created, standing);
            });
        } catch (RuntimeException e) {
            documents.delete(scan);
            throw e;
        }
        handover.replaced()
                .flatMap(replaced -> catalog.find(replaced.documentId()))
                .ifPresent(documents::delete);
        log.info(
                "Scan {} handed in for template {} of member {} at appointment {} on {} ({})",
                scan.id(),
                templateId,
                memberId,
                event.id(),
                date,
                handover.submission().state());
        if (handover.submission().state() == PaperState.CONFIRMED) {
            confirmations.confirmed(session, handover.submission());
        } else {
            confirmations.waiting(handover.submission());
        }
        return handover.submission();
    }

    /** A submission recorded, and the one waiting before it that it replaced. */
    private record Handover(PaperSubmission submission, Optional<PaperSubmission> replaced) {}

    private static void requireNotConfirmed(Optional<PaperSubmission> standing) {
        if (standing.map(submission -> submission.state() == PaperState.CONFIRMED)
                .orElse(false)) {
            throw DocumentRefusal.DOCUMENT_SCAN_ALREADY_CONFIRMED.raise();
        }
    }

    /**
     * Confirms a scan that waits as the signed paper copy.
     *
     * @param session      the manager of the registrations
     * @param event        the appointment, already checked to be one the reader may see
     * @param submissionId the submission
     * @return the submission as it now stands
     */
    public PaperSubmission confirm(StationSession session, StationEvent event, int submissionId) {
        var submission = requireAt(session, event, submissionId);
        var confirmed = submissions
                .review(submission.id(), PaperState.CONFIRMED, session.member().id(), null)
                .orElseThrow(DocumentRefusal.DOCUMENT_SCAN_NOT_WAITING::raise);
        log.info(
                "Scan submission {} confirmed by member {}",
                submissionId,
                session.member().id());
        confirmations.confirmed(session, confirmed);
        return confirmed;
    }

    /**
     * Turns a scan that waits down, which opens the document again, and tells the participant and their
     * guardians why.
     *
     * @param session      the manager of the registrations
     * @param event        the appointment, already checked to be one the reader may see
     * @param submissionId the submission
     * @param reason       why, which is required
     * @return the submission as it now stands
     */
    public PaperSubmission reject(
            StationSession session, StationEvent event, int submissionId, @Nullable String reason) {
        String given = reason == null ? "" : reason.strip();
        if (given.isEmpty()) throw DocumentRefusal.DOCUMENT_SCAN_REASON_MISSING.raise();
        if (given.length() > MAX_REASON_LENGTH) throw DocumentRefusal.DOCUMENT_SCAN_REASON_TOO_LONG.raise();
        var submission = requireAt(session, event, submissionId);
        String documentName = templates
                .find(submission.templateId())
                .map(DocumentTemplate::name)
                .orElseThrow(DocumentRefusal.DOCUMENT_SCAN_NOT_FOUND::raise);
        var rejected = submissions
                .review(submission.id(), PaperState.REJECTED, session.member().id(), given)
                .orElseThrow(DocumentRefusal.DOCUMENT_SCAN_NOT_WAITING::raise);
        notifier.notify(
                StationAudience.household(List.of(rejected.memberId()))
                        .except(session.member().id()),
                NotificationType.DOCUMENT_SCAN_REJECTED,
                NotificationData.of(
                        new NotificationParams.DocumentScanRejected(
                                documentName,
                                names.identified(rejected.memberId()),
                                event.name(),
                                rejected.eventDate(),
                                given),
                        NotificationLinks.eventDate(event.id(), rejected.eventDate())),
                Delivery.EVERY_TIME);
        log.info(
                "Scan submission {} turned down by member {}",
                submissionId,
                session.member().id());
        return rejected;
    }

    /**
     * Takes back a scan that still waits, as whoever may hand one in for the participant: the submission and
     * the scan it filed are removed, as when a new scan replaces it, and the document is open again.
     *
     * @param session      the participant, their guardian or a manager of the registrations
     * @param event        the appointment, already checked to be one the reader may see
     * @param submissionId the submission
     */
    public void withdraw(StationSession session, StationEvent event, int submissionId) {
        var submission = requireAt(session, event, submissionId);
        boolean manages = session.hasPermission(StationPermission.EVENT_REGISTRATION);
        appointments.requireMayHandIn(session, event, submission.eventDate(), submission.memberId(), manages);
        var withdrawn = submissions
                .deleteWaiting(submission.id())
                .orElseThrow(DocumentRefusal.DOCUMENT_SCAN_NOT_WAITING::raise);
        catalog.find(withdrawn.documentId()).ifPresent(documents::delete);
        log.info(
                "Scan submission {} taken back by member {}",
                submissionId,
                session.member().id());
    }

    /**
     * The scan of a submission, for a manager of the registrations to check it.
     *
     * @param session      the manager of the registrations
     * @param event        the appointment, already checked to be one the reader may see
     * @param submissionId the submission
     * @return the filed scan and its bytes
     */
    public Scan scan(StationSession session, StationEvent event, int submissionId) {
        var submission = requireAt(session, event, submissionId);
        var document =
                catalog.find(submission.documentId()).orElseThrow(DocumentRefusal.DOCUMENT_CONTENT_NOT_HERE::raise);
        var data = documents
                .open(document, DocumentDoor.STATION)
                .orElseThrow(DocumentRefusal.DOCUMENT_CONTENT_NOT_HERE::raise);
        return new Scan(document, data);
    }

    private PaperSubmission requireAt(StationSession session, StationEvent event, int submissionId) {
        return submissions
                .find(session.stationId(), event.id(), submissionId)
                .orElseThrow(DocumentRefusal.DOCUMENT_SCAN_NOT_FOUND::raise);
    }
}
