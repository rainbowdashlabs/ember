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
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Settles the signatures still open on a participant's copy as signed on paper, once the scan of the signed
 * copy of that document for that date counts as confirmed. Every open field of every open request on the
 * participant's copies of that document for the date is settled, each through
 * {@link SignatureFieldService#confirmScanOnPaper}, so the next sealed version shows them so. A field that
 * cannot be settled is logged and left open; the scan stays confirmed.
 */
@Singleton
public class ScanSignatures implements ScanConfirmations {
    private static final Logger log = LoggerFactory.getLogger(ScanSignatures.class);

    private final SignatureRequestRepository requests;
    private final SignatureFieldService fields;

    @Inject
    public ScanSignatures(SignatureRequestRepository requests, SignatureFieldService fields) {
        this.requests = requests;
        this.fields = fields;
    }

    @Override
    public void confirmed(StationSession session, PaperSubmission submission) {
        var open =
                requests
                        .openForAppointment(submission.eventId(), submission.eventDate(), submission.memberId())
                        .stream()
                        .filter(found -> found.templateId() == submission.templateId())
                        .toList();
        for (var found : open) {
            var request = found.request();
            for (var field : requests.fieldsOf(request.id())) {
                if (field.state() != FieldState.OPEN) continue;
                try {
                    fields.confirmScanOnPaper(session, request.uid(), field.fieldName());
                } catch (RuntimeException e) {
                    log.warn(
                            "Could not confirm field {} of signing request {} on paper for scan submission {}",
                            field.fieldName(),
                            request.uid(),
                            submission.id(),
                            e);
                }
            }
        }
    }
}
