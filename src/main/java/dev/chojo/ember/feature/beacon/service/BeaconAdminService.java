/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.api.refusal.BeaconRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.beacon.entity.BeaconFault;
import dev.chojo.ember.feature.beacon.entity.BeaconMetricsRow;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.beacon.entity.BeaconReport;
import dev.chojo.ember.feature.beacon.repository.BeaconReadRepository;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.service.ProblemLogAppender;
import dev.chojo.ember.feature.system.service.ProblemReportScreenshotService;
import dev.chojo.ember.feature.system.service.ProblemReportService;
import dev.chojo.ember.feature.system.service.UpdateCheckService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * What an operator does with their own beacon: look at what would be sent and send it, and, where
 * this instance is a beacon itself, go through what other instances sent it.
 *
 * <p>The preview is not a nicety. An exception message quotes what failed, and what failed is
 * sometimes a mail address or a row somebody can be recognised by. Asking an operator to agree to
 * forwarding without showing them the bytes is asking them to agree to something nobody has read.
 */
@Singleton
public class BeaconAdminService {
    private final BeaconSettings config;
    private final BeaconReportService reports;
    private final BeaconMetricsService metrics;
    private final UpdateCheckService updates;
    private final BeaconReadRepository collected;
    private final ProblemReportService problemReports;
    private final ProblemReportScreenshotService pictures;
    private final Supplier<ProblemLogAppender> problemLog;

    @Inject
    public BeaconAdminService(
            BeaconSettings config,
            BeaconReportService reports,
            BeaconMetricsService metrics,
            UpdateCheckService updates,
            BeaconReadRepository collected,
            ProblemReportService problemReports,
            ProblemReportScreenshotService pictures) {
        this(config, reports, metrics, updates, collected, problemReports, pictures, ProblemLogAppender::instance);
    }

    /**
     * The same, reading the fault log from the given source.
     *
     * @param problemLog the fault log, answering null where it is not running
     */
    BeaconAdminService(
            BeaconSettings config,
            BeaconReportService reports,
            BeaconMetricsService metrics,
            UpdateCheckService updates,
            BeaconReadRepository collected,
            ProblemReportService problemReports,
            ProblemReportScreenshotService pictures,
            Supplier<ProblemLogAppender> problemLog) {
        this.config = config;
        this.reports = reports;
        this.metrics = metrics;
        this.updates = updates;
        this.collected = collected;
        this.problemReports = problemReports;
        this.pictures = pictures;
        this.problemLog = problemLog;
    }

    /** What a beacon has gathered is only worth asking for when this instance is one. */
    private void requireBeacon() {
        if (!config.receiving()) throw BeaconRefusal.BEACON_NOT_RECEIVING.raise();
    }

    private void requireEnabled() {
        if (!config.enabled()) throw BeaconRefusal.BEACON_NOT_SET_UP.raise();
    }

    public List<BeaconFault> faults(boolean includeAcknowledged) {
        requireBeacon();
        return collected.faults(includeAcknowledged);
    }

    /** Marks a fault as seen, or names the version that put it right. */
    public void resolveFault(int id, boolean acknowledged, @Nullable String resolvedIn) {
        requireBeacon();
        if (!collected.resolveFault(id, acknowledged, resolvedIn)) {
            throw BeaconRefusal.BEACON_FAULT_NOT_HERE.raise();
        }
    }

    public List<BeaconReport> collectedReports(boolean includeAcknowledged) {
        requireBeacon();
        return collected.reports(includeAcknowledged);
    }

    public void acknowledgeReport(int id) {
        requireBeacon();
        if (!collected.acknowledgeReport(id)) throw BeaconRefusal.BEACON_REPORT_NOT_ACKNOWLEDGED.raise();
    }

    /**
     * The picture that came with a report this beacon was sent.
     *
     * <p>Served from the report rather than from the file library: a picture forwarded here is of a
     * page of somebody else's installation, and it has no business appearing in a list of files
     * anybody browses.
     */
    public MediaContent collectedPicture(int id) {
        requireBeacon();
        var report = collected.reports(true).stream()
                .filter(row -> row.id() == id)
                .findFirst()
                .orElseThrow(BeaconRefusal.BEACON_REPORT_NOT_HERE_FOR_PICTURE::raise);
        var screenshotFileId = report.screenshotFileId();
        if (screenshotFileId == null) throw BeaconRefusal.BEACON_REPORT_HAS_NO_PICTURE.raise();
        return pictures.read(screenshotFileId).orElseThrow(BeaconRefusal.BEACON_REPORT_PICTURE_NOT_HERE::raise);
    }

    /**
     * @param days how many days back, held between 1 and 365
     */
    public List<BeaconMetricsRow> collectedMetrics(int days) {
        requireBeacon();
        return collected.metrics(Math.clamp(days, 1, 365));
    }

    public BeaconStatus status() {
        return new BeaconStatus(
                config.enabled(),
                config.url(),
                config.forwardProblems(),
                config.forwardReports(),
                config.reviewReportPictures(),
                config.metricsEnabled(),
                config.receiving(),
                config.contactName(),
                config.contactMail());
    }

    /**
     * What an operator chose, written down.
     *
     * <p>Stored rather than configured, so a change takes effect on the next entry rather than on the
     * next restart. Everything that reads these asks at the moment it matters.
     */
    public BeaconStatus update(BeaconSettingsRequest request) {
        config.update(
                request.enabled(),
                request.url(),
                request.forwardProblems(),
                request.forwardReports(),
                request.reviewReportPictures(),
                request.metricsEnabled(),
                request.receiving(),
                request.contactName(),
                request.contactMail());
        return status();
    }

    private ProblemLogAppender runningLog(Refusal whenNotRunning) {
        var appender = problemLog.get();
        if (appender == null) throw whenNotRunning.raise();
        return appender;
    }

    private ProblemLogAppender.ProblemSnapshot problem(long id) {
        return runningLog(BeaconRefusal.PROBLEM_LOG_NOT_RUNNING).getProblems(true).stream()
                .filter(problem -> problem.id() == id)
                .map(ProblemLogAppender.ProblemEntry::snapshot)
                .findFirst()
                .orElseThrow(BeaconRefusal.BEACON_PROBLEM_NOT_HERE::raise);
    }

    /** The exact payload one problem would travel as, shown before anything is sent. */
    public BeaconPayloads.ProblemPayload previewProblem(long id) {
        return reports.payloadFor(problem(id), updates.currentVersion());
    }

    public SendResult sendProblem(long id) {
        requireEnabled();
        return new SendResult(reports.send(problem(id), updates.currentVersion()) ? 1 : 0);
    }

    /** What the list's own action sends: the ticked entries, in one go. */
    public SendResult sendProblems(List<Long> ids) {
        requireEnabled();
        if (ids == null || ids.isEmpty()) {
            throw BeaconRefusal.BEACON_NOTHING_CHOSEN_TO_SEND.raise();
        }
        var chosen = runningLog(BeaconRefusal.PROBLEM_LOG_NOT_RUNNING_ON_SEND).getProblems(true).stream()
                .filter(problem -> ids.contains(problem.id()))
                .map(ProblemLogAppender.ProblemEntry::snapshot)
                .toList();
        return new SendResult(reports.sendAll(chosen, updates.currentVersion()));
    }

    private ProblemReport report(int id) {
        return problemReports.find(id).orElseThrow(BeaconRefusal.BEACON_REPORT_NOT_HERE::raise);
    }

    /**
     * What one report would be sent as.
     *
     * <p>A report is a person talking, so the bytes matter more here than anywhere else: the message
     * is theirs, and the only honest way to ask an operator to pass it on is to show what goes.
     */
    public BeaconPayloads.ReportPayload previewReport(int id) {
        return reports.reportPayloadFor(report(id), updates.currentVersion());
    }

    /**
     * Passes one report on now, whatever the automatic switch says.
     *
     * <p>The switch governs what leaves on its own; a button is an operator deciding about this one
     * report in front of them. That is also the only way a report written before the switch was
     * turned on can ever reach a beacon.
     */
    public SendResult sendReport(int id, SendReportRequest decision) {
        requireEnabled();
        var report = report(id);
        Integer pictureId = report.screenshotFileId();
        boolean temporary = false;
        if (decision.dropScreenshot()) {
            pictureId = null;
        } else if (decision.screenshot() instanceof String screenshot && !screenshot.isBlank()) {
            var covered = pictures.store(screenshot, null);
            if (covered.isPresent()) {
                pictureId = covered.get();
                temporary = true;
            }
        }
        return new SendResult(problemReports.forward(report, pictureId, temporary) ? 1 : 0);
    }

    /** The day's numbers as they would go, so an operator can see what "how much" means. */
    public BeaconPayloads.MetricsBatch previewMetrics() {
        return metrics.batch(updates.currentVersion(), Instant.now());
    }

    /**
     * What this instance is set up to do, so the screens can say so rather than guess.
     *
     * @param enabled     whether anything is sent at all
     * @param url         the beacon being reported to
     * @param receiving   whether this instance is itself a beacon
     * @param contactName a name a beacon may answer on, empty where none was given
     */
    public record BeaconStatus(
            boolean enabled,
            String url,
            boolean forwardProblems,
            boolean forwardReports,
            boolean reviewReportPictures,
            boolean metricsEnabled,
            boolean receiving,
            String contactName,
            String contactMail) {}

    /**
     * The switches and the contact, as the screen sends them back.
     *
     * @param reviewReportPictures whether a report carrying a picture waits for somebody here before
     *     it is passed on. On unless an operator says otherwise
     */
    public record BeaconSettingsRequest(
            boolean enabled,
            String url,
            boolean forwardProblems,
            boolean forwardReports,
            boolean reviewReportPictures,
            boolean metricsEnabled,
            boolean receiving,
            String contactName,
            String contactMail) {}

    /**
     * What an operator decided about the picture of the report they are passing on.
     *
     * <p>Absent altogether where they simply pressed send, which is the case this had before pictures
     * existed and still the common one.
     *
     * @param screenshot     a further covered copy to send in place of the one the reporter covered,
     *                       or null to send theirs as it stands
     * @param dropScreenshot whether the report goes without any picture, which is final: a picture
     *                       left out of a report is never sent after it
     */
    public record SendReportRequest(@Nullable String screenshot, boolean dropScreenshot) {
        /** Simply pressing send: the reporter's own picture, as it stands. */
        public static final SendReportRequest AS_IT_STANDS = new SendReportRequest(null, false);
    }

    /** How many were handed to the sender. */
    public record SendResult(int queued) {}
}
