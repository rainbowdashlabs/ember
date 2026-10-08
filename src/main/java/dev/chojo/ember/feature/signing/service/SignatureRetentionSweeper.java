/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.service.SealedDocumentService;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Deletes requests for signatures, their evidence and the sealed documents they kept, once the member is
 * gone and the retention their template set is over.
 *
 * <p>Signed documents and their evidence are kept past a deletion request under Art. 17(3)(e) GDPR, as
 * evidence for legal claims, and only for as long as that needs. Retention starts when the member leaves
 * or is deleted, however that happens, so the sweep finds those requests itself rather than being told by
 * every path that removes a member: a request whose member is former or gone gets its {@code retain_until},
 * and one whose member came back loses it again.
 *
 * <p>A request past its {@code retain_until} goes with its fields and evidence. Its document goes too
 * where it is sealed and nothing else keeps it: no other request on it is still kept, and no member it is
 * about is still at the station. A sealed document is locked against every other deletion while its
 * station exists, and the database lets this one through only because it checks the retention itself
 * ({@code member_document_retention_over}). A document that is not sealed is left to the member documents'
 * own rules.
 *
 * <p>Runs daily and asks only whether a retention is over, so an instance that was off catches up by
 * itself; one run is capped, since each removal is file work.
 */
@Singleton
public class SignatureRetentionSweeper implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(SignatureRetentionSweeper.class);
    private static final Duration START_DELAY = Duration.ofMinutes(10);
    private static final Duration INTERVAL = Duration.ofDays(1);

    /** How many requests one run deletes at most. */
    static final int MAX_PER_RUN = 200;

    private final SignatureRequestRepository requests;
    private final DocumentRepository documents;
    private final SealedDocumentService sealedDocuments;
    private final Clock clock;

    @Inject
    public SignatureRetentionSweeper(
            SignatureRequestRepository requests, DocumentRepository documents, SealedDocumentService sealedDocuments) {
        this(requests, documents, sealedDocuments, Clock.systemUTC());
    }

    SignatureRetentionSweeper(
            SignatureRequestRepository requests,
            DocumentRepository documents,
            SealedDocumentService sealedDocuments,
            Clock clock) {
        this.requests = requests;
        this.documents = documents;
        this.sealedDocuments = sealedDocuments;
        this.clock = clock;
    }

    /**
     * Body of the run, reachable by tests so they need not wait a day. A failure is logged and swallowed:
     * what was due stays due and goes on the next run.
     */
    void sweep() {
        try {
            int removed = sweep(clock.instant());
            if (removed > 0) log.info("Deleted {} signing requests whose retention is over", removed);
        } catch (Exception e) {
            log.warn("Sweeping signing requests whose retention is over failed", e);
        }
    }

    /**
     * Starts and stops retentions as members left or came back, then deletes what is no longer kept.
     *
     * @param now the time a retention is measured against
     * @return how many requests were deleted
     */
    int sweep(Instant now) {
        requests.stopRetention();
        requests.startRetention(now);
        int removed = 0;
        for (var request : requests.expired(now, MAX_PER_RUN)) {
            try {
                remove(request, now);
                removed++;
            } catch (RuntimeException e) {
                log.warn("Signing request {} could not be deleted after its retention", request.uid(), e);
            }
        }
        return removed;
    }

    private void remove(SignatureRequest request, Instant now) {
        Integer documentId = request.documentId();
        if (documentId != null && !requests.keptBesides(documentId, request.id(), now)) {
            documents.findById(documentId).filter(Document::sealed).ifPresent(sealedDocuments::removeExpired);
        }
        requests.delete(request.id());
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "signature-retention-sweep", Schedule.fixedDelay(START_DELAY, INTERVAL), this::sweep));
    }
}
