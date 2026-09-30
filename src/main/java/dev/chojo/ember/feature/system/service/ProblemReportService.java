/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.beacon.service.BeaconReportService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * The problems members report from inside the application: taking them in, with the picture of the
 * page they were written on, passing them to a beacon where the instance forwards them, and the
 * administrator's list of them.
 */
@Singleton
public class ProblemReportService {
    private final ProblemReportRepository reports;
    private final BeaconReportService beacon;
    private final UpdateCheckService updates;
    private final ProblemReportScreenshotService screenshots;

    @Inject
    public ProblemReportService(
            ProblemReportRepository reports,
            BeaconReportService beacon,
            UpdateCheckService updates,
            ProblemReportScreenshotService screenshots) {
        this.reports = reports;
        this.beacon = beacon;
        this.updates = updates;
        this.screenshots = screenshots;
    }

    /**
     * Takes a report in, and passes it on at once where it needs nobody's review first.
     *
     * <p>The picture is kept before the report is written, so a picture that cannot be stored fails
     * the whole report rather than leaving one that claims a picture nobody can fetch.
     *
     * @param stationId    the station the reporter was in
     * @param memberId     the reporter's membership there, or null
     * @param reporterName what the reporter is called
     * @param request      the report as the dialog sent it
     * @return the report as stored
     */
    public ProblemReport submit(int stationId, Integer memberId, String reporterName, ReportRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw Refusal.PROBLEM_REPORT_NEEDS_A_MESSAGE.raise();
        }
        var picture = screenshots.store(request.screenshot(), memberId);
        var report = reports.create(
                stationId,
                memberId,
                reporterName,
                request.message(),
                request.pageUrl(),
                request.userRoles(),
                request.recentRequests(),
                request.browserInfo(),
                request.screenSize(),
                picture.orElse(null));
        if (beacon.goesByItself(report)) forward(report, report.screenshotFileId(), false);
        return report;
    }

    /**
     * Passes a report to a beacon, with the picture it is to go with.
     *
     * <p>The picture goes first and the report second, which is the beacon's business; what the
     * caller decides is which picture, because whoever reviewed it may have covered more of it and
     * may have decided it should not go at all. A report and its picture go together or the picture
     * does not go: nothing adds one to a report that has already left.
     *
     * @param pictureFileId the picture to send with it, or null to send the report on its own
     * @param temporary     whether that picture was made for this delivery alone and is to be let go
     *                      of once it has gone, which is what a covered copy is
     * @return whether the report was queued, false when the queue is full
     */
    public boolean forward(ProblemReport report, Integer pictureFileId, boolean temporary) {
        Optional<MediaContent> picture = pictureFileId == null ? Optional.empty() : screenshots.read(pictureFileId);
        boolean queued = beacon.sendReportNow(
                report,
                updates.currentVersion(),
                picture.map(MediaContent::data).orElse(null),
                picture.map(MediaContent::contentType).orElse(null));
        if (queued) reports.markForwarded(report.id());
        if (temporary) screenshots.forget(pictureFileId);
        return queued;
    }

    /**
     * @return the report, or empty when there is none by that id
     */
    public Optional<ProblemReport> find(int id) {
        return reports.findById(id);
    }

    public List<ProblemReport> list(boolean includeAcknowledged) {
        return reports.findAll(includeAcknowledged);
    }

    public void acknowledge(int id) {
        reports.acknowledge(id);
    }

    /**
     * @return how many reports were acknowledged
     */
    public int acknowledgeAll() {
        return reports.acknowledgeAll();
    }

    /**
     * The picture a report was written with.
     */
    public MediaContent picture(int id) {
        var report = reports.findById(id).orElseThrow(Refusal.PROBLEM_REPORT_NOT_HERE::raise);
        if (!report.hasScreenshot()) throw Refusal.PROBLEM_REPORT_HAS_NO_PICTURE.raise();
        return screenshots.read(report.screenshotFileId()).orElseThrow(Refusal.PROBLEM_REPORT_PICTURE_NOT_HERE::raise);
    }

    /**
     * Deletes the report and the picture with it, because the picture has nowhere else to belong.
     */
    public void delete(int id) {
        var report = reports.findById(id).orElse(null);
        reports.delete(id);
        if (report != null) screenshots.forget(report.screenshotFileId());
    }

    /**
     * A report as the dialog sends it.
     *
     * @param screenshot the picture of the page as base64, already covered where the reporter covered
     *     it, or null where they attached none. Never taken without being asked for.
     */
    public record ReportRequest(
            String message,
            String pageUrl,
            String userRoles,
            String recentRequests,
            String browserInfo,
            String screenSize,
            String screenshot) {}
}
