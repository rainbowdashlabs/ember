/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.conf.file.elements.Signing;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.SealedVersion;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.documents.service.SealedDocumentService;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.signing.entity.SealedDocument;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Adds later timestamps to the sealed versions of signed documents: the first one to a version sealed while
 * no timestamp service answered, and, where the operator switched it on, a renewal to every version whose
 * newest timestamp is about to stop proving its time.
 *
 * <p><b>Lifting.</b> Hourly, while timestamps are on, the current versions sealed without a timestamp are
 * given a document timestamp and their validation material ({@link PdfSealer#lift}), oldest first. This runs
 * whatever the archive switch says: the record page of such a version already says the time can be added
 * later, and the first timestamp is what makes the seal provable against the operator at all.
 *
 * <p><b>Renewing</b> ({@link Signing#archiveTimestamps()}, off by default). Daily, the current versions whose
 * newest timestamp rests on a certificate ending within {@link #RENEWAL_MARGIN} get the current validation
 * material and a document timestamp over all of it ({@link PdfSealer#renew}), the ones ending soonest first.
 * The margin leaves half a year of daily attempts before a certificate ends. A renewal whose own timestamp
 * does not outlast the one it covers gains nothing and is not filed.
 *
 * <p>Either way the result is a new file and so a new version of its own, filed over the version it was
 * built from ({@link SealedDocumentService#fileOnto}), which stays stored. A version filed in the meantime,
 * by a later signature, carries something the result does not, so the result is dropped. Signers are not
 * mailed the new version: content, signatures and record are the ones they received, the verification page
 * finds every version by its hash, and the copy they hold keeps validating on its own.
 *
 * <p>Each run handles at most {@link #MAX_PER_RUN} versions, each with its own {@link TimestampServices#BUDGET},
 * and is safe to stop and run again at any time, since every version is read, extended and filed on its own.
 * A run stops at the first version no service gave a useful timestamp for, since the next one would fare
 * no better. A version that fails otherwise, such as one whose file is missing from the store, is logged,
 * noted as failed ({@link SealedVersionRepository#markTimestampsFailed}) and the run goes on; later runs take
 * it only after every version that never failed, so a version that keeps failing holds back no other.
 */
@Singleton
public class SealedVersionTimestamps implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(SealedVersionTimestamps.class);
    private static final Duration LIFT_START_DELAY = Duration.ofMinutes(30);
    private static final Duration LIFT_INTERVAL = Duration.ofHours(1);
    private static final Duration RENEWAL_START_DELAY = Duration.ofMinutes(45);
    private static final Duration RENEWAL_INTERVAL = Duration.ofDays(1);

    /** How long before the certificates of a version's newest timestamp end its timestamps are renewed. */
    static final Duration RENEWAL_MARGIN = Duration.ofDays(180);

    /** How many versions one run handles at most, since each may wait for the timestamp services. */
    static final int MAX_PER_RUN = 20;

    private final SealedVersionRepository versions;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final SealedDocumentService sealedDocuments;
    private final PdfSealer sealer;
    private final TimestampServices timestamps;
    private final boolean archiveTimestamps;
    private final Clock clock;

    @Inject
    public SealedVersionTimestamps(
            SealedVersionRepository versions,
            DocumentRepository documents,
            DocumentService documentService,
            SealedDocumentService sealedDocuments,
            PdfSealer sealer,
            TimestampServices timestamps,
            Signing config) {
        this(
                versions,
                documents,
                documentService,
                sealedDocuments,
                sealer,
                timestamps,
                config.archiveTimestamps(),
                Clock.systemUTC());
    }

    /**
     * @param archiveTimestamps whether timestamps are renewed
     * @param clock             what "about to end" is measured from, which a test moves
     */
    SealedVersionTimestamps(
            SealedVersionRepository versions,
            DocumentRepository documents,
            DocumentService documentService,
            SealedDocumentService sealedDocuments,
            PdfSealer sealer,
            TimestampServices timestamps,
            boolean archiveTimestamps,
            Clock clock) {
        this.versions = versions;
        this.documents = documents;
        this.documentService = documentService;
        this.sealedDocuments = sealedDocuments;
        this.sealer = sealer;
        this.timestamps = timestamps;
        this.archiveTimestamps = archiveTimestamps;
        this.clock = clock;
    }

    // TODO lift and renew issued letters too; they are filed as plain documents, not as sealed versions
    /**
     * Gives the current versions sealed without a timestamp their first one.
     *
     * @return how many versions were filed
     */
    int lift() {
        if (!timestamps.enabled()) return 0;
        return each(versions.currentWithoutTimestamp(MAX_PER_RUN), this::lifted, "lift");
    }

    /**
     * Renews the timestamps of the current versions whose newest timestamp is about to stop proving its time.
     *
     * @return how many versions were filed
     */
    int renew() {
        if (!archiveTimestamps) return 0;
        if (!timestamps.enabled()) {
            log.warn("signing.archiveTimestamps is on, but timestamps are off, so no timestamp is renewed");
            return 0;
        }
        var due = clock.instant().plus(RENEWAL_MARGIN);
        return each(versions.currentWithTimestampEndingBefore(due, MAX_PER_RUN), this::renewed, "renew");
    }

    private Optional<SealedDocument> lifted(Sealed sealed) {
        var lifted = sealer.lift(sealed.pdf());
        return lifted.level() == SealLevel.BASELINE_B ? Optional.empty() : Optional.of(lifted);
    }

    private Optional<SealedDocument> renewed(Sealed sealed) {
        var renewed = sealer.renew(sealed.pdf(), sealed.version().sealLevel());
        if (renewed.isEmpty()) return Optional.empty();
        var before = sealed.version().timestampValidUntil();
        var after = renewed.get().timestampValidUntil();
        if (before != null && (after == null || !after.isAfter(before))) {
            log.warn(
                    "The renewed timestamp of document {} would end no later than the one it covers; not filed",
                    sealed.document().id());
            return Optional.empty();
        }
        return renewed;
    }

    private int each(List<SealedVersion> due, Function<Sealed, Optional<SealedDocument>> extend, String what) {
        int filed = 0;
        for (var version : due) {
            try {
                var sealed = sealedFileOf(version);
                if (sealed.isEmpty()) {
                    versions.markTimestampsFailed(version.id(), clock.instant());
                    continue;
                }
                var extended = extend.apply(sealed.get());
                if (extended.isEmpty()) {
                    log.warn("Stopped this run ({}): no timestamp service gave a timestamp that helps", what);
                    break;
                }
                if (sealedDocuments.fileOnto(sealed.get().document(), version, extended.get())) filed++;
            } catch (RuntimeException e) {
                log.warn(
                        "Could not {} the timestamps of sealed version {}; a later run tries again",
                        what,
                        version.id(),
                        e);
                versions.markTimestampsFailed(version.id(), clock.instant());
            }
        }
        return filed;
    }

    private Optional<Sealed> sealedFileOf(SealedVersion version) {
        var document = documents.findById(version.documentId());
        if (document.isEmpty()) return Optional.empty();
        var pdf = documentService.read(document.get(), version);
        if (pdf.isEmpty()) {
            log.warn(
                    "The file of sealed version {} of document {} is not in the store",
                    version.id(),
                    version.documentId());
            return Optional.empty();
        }
        return Optional.of(new Sealed(document.get(), version, pdf.get()));
    }

    private void run(String what, IntSupplier body) {
        try {
            int filed = body.getAsInt();
            if (filed > 0) log.info("Filed {} sealed versions with a later timestamp ({})", filed, what);
        } catch (Exception e) {
            log.warn("Adding later timestamps to sealed documents failed ({})", what, e);
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(
                new ScheduledTask(
                        "sealed-version-lift",
                        Schedule.fixedDelay(LIFT_START_DELAY, LIFT_INTERVAL),
                        () -> run("lift", this::lift)),
                new ScheduledTask(
                        "sealed-version-renewal",
                        Schedule.fixedDelay(RENEWAL_START_DELAY, RENEWAL_INTERVAL),
                        () -> run("renew", this::renew)));
    }

    /**
     * A sealed version with its document and its file, as read for a later timestamp.
     *
     * <p>The array is handed over as it is, without a copy.
     */
    private record Sealed(Document document, SealedVersion version, byte[] pdf) {}
}
