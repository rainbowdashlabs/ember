/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.feature.generator.entity.PaperSubmission;
import dev.chojo.ember.feature.generator.service.ScanConfirmations;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Has the signatures still open on a participant's copy follow the scan of the signed copy of that document
 * for that date: every open field of every open request on the participant's copies of that document for
 * the date.
 *
 * <p>While the scan waits for a manager, the fields are asked of nobody: the open tasks, the signing screens
 * and the reminders leave them out by the scan's state alone, and the unread requests and reminders already
 * out for them are taken back here. A scan turned down needs nothing from here, since the fields are then
 * asked for again by that same state.
 *
 * <p>Once the scan counts as confirmed, each field is settled through
 * {@link SignatureFieldService#confirmScanOnPaper}, so the next sealed version shows them so. A field that
 * cannot be settled is logged and left open; the scan stays confirmed.
 */
@Singleton
public class ScanSignatures implements ScanConfirmations {
    private static final Logger log = LoggerFactory.getLogger(ScanSignatures.class);

    private final SignatureRequestRepository requests;
    private final SignatureFieldService fields;
    private final SignatureNotices notices;

    @Inject
    public ScanSignatures(SignatureRequestRepository requests, SignatureFieldService fields, SignatureNotices notices) {
        this.requests = requests;
        this.fields = fields;
        this.notices = notices;
    }

    @Override
    public void waiting(PaperSubmission submission) {
        var waiting =
                openFieldsOf(submission).stream().map(open -> open.field().id()).toList();
        notices.settled(waiting);
    }

    @Override
    public void confirmed(StationSession session, PaperSubmission submission) {
        for (var open : openFieldsOf(submission)) {
            String fieldName = open.field().fieldName();
            try {
                fields.confirmScanOnPaper(session, open.request().uid(), fieldName);
            } catch (RuntimeException e) {
                log.warn(
                        "Could not confirm field {} of signing request {} on paper for scan submission {}",
                        fieldName,
                        open.request().uid(),
                        submission.id(),
                        e);
            }
        }
    }

    /** The open fields of the open requests on the participant's copies the scan stands in for. */
    private List<OpenField> openFieldsOf(PaperSubmission submission) {
        return requests.openForAppointment(submission.eventId(), submission.eventDate(), submission.memberId()).stream()
                .filter(found -> found.templateId() == submission.templateId())
                .flatMap(found -> requests.fieldsOf(found.request().id()).stream()
                        .filter(field -> field.state() == FieldState.OPEN)
                        .map(field -> new OpenField(found.request(), field)))
                .toList();
    }

    /** An open field and the request it belongs to. */
    private record OpenField(SignatureRequest request, RequestedSignature field) {}
}
