/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.beacon.repository.BeaconReadRepository;
import dev.chojo.ember.feature.beacon.service.BeaconMetricsService;
import dev.chojo.ember.feature.beacon.service.BeaconReportService;
import dev.chojo.ember.feature.beacon.service.BeaconSettings;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import dev.chojo.ember.feature.system.service.ProblemLogAppender;
import dev.chojo.ember.feature.system.service.ProblemReportScreenshotService;
import dev.chojo.ember.feature.system.service.UpdateCheckService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

/**
 * What an operator does with their own beacon: look at what would be sent, and send it.
 *
 * <p>The preview is not a nicety. An exception message quotes what failed, and what failed is
 * sometimes a mail address or a row somebody can be recognised by. Asking an operator to agree to
 * forwarding without showing them the bytes is asking them to agree to something nobody has read.
 */
@Singleton
public class BeaconAdminRoutes implements Routes {

    private final BeaconSettings config;
    private final BeaconReportService reports;
    private final BeaconMetricsService metrics;
    private final UpdateCheckService updates;
    private final BeaconReadRepository collected;
    private final ProblemReportRepository problemReports;
    private final ProblemReportScreenshotService pictures;

    @Inject
    public BeaconAdminRoutes(
            BeaconSettings config,
            BeaconReportService reports,
            BeaconMetricsService metrics,
            UpdateCheckService updates,
            BeaconReadRepository collected,
            ProblemReportRepository problemReports,
            ProblemReportScreenshotService pictures) {
        this.config = config;
        this.reports = reports;
        this.metrics = metrics;
        this.updates = updates;
        this.collected = collected;
        this.problemReports = problemReports;
        this.pictures = pictures;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/admin/beacon";
        routes.get(base, this::status, InstancePermission.ADMINISTRATOR);
        routes.put(base, this::updateSettings, InstancePermission.ADMINISTRATOR);
        routes.get(base + "/problems/{id}/preview", this::previewProblem, InstancePermission.ADMINISTRATOR);
        routes.post(base + "/problems/{id}/send", this::sendProblem, InstancePermission.ADMINISTRATOR);
        routes.post(base + "/problems/send", this::sendProblems, InstancePermission.ADMINISTRATOR);
        routes.get(base + "/reports/{id}/preview", this::previewReport, InstancePermission.ADMINISTRATOR);
        routes.post(base + "/reports/{id}/send", this::sendReport, InstancePermission.ADMINISTRATOR);
        routes.get(base + "/figures/preview", this::previewMetrics, InstancePermission.ADMINISTRATOR);

        routes.get(base + "/collected/faults", this::faults, InstancePermission.ADMINISTRATOR);
        routes.put(base + "/collected/faults/{id}", this::resolveFault, InstancePermission.ADMINISTRATOR);
        routes.get(base + "/collected/reports", this::collectedReports, InstancePermission.ADMINISTRATOR);
        routes.post(
                base + "/collected/reports/{id}/acknowledge",
                this::acknowledgeReport,
                InstancePermission.ADMINISTRATOR);
        routes.get(
                base + "/collected/reports/{id}/screenshot",
                this::collectedReportScreenshot,
                InstancePermission.ADMINISTRATOR);
        routes.get(base + "/collected/figures", this::collectedMetrics, InstancePermission.ADMINISTRATOR);
    }

    /** What a beacon has gathered is only worth asking for when this instance is one. */
    private void requireBeacon() {
        if (!config.receiving()) throw Refusal.BEACON_NOT_RECEIVING.raise();
    }

    private void faults(Context ctx) {
        requireBeacon();
        ctx.json(collected.faults(Boolean.parseBoolean(ctx.queryParam("includeAcknowledged"))));
    }

    /** Marking a fault as seen, or naming the version that put it right. */
    private void resolveFault(Context ctx) {
        requireBeacon();
        var request = ctx.bodyAsClass(ResolveRequest.class);
        if (!collected.resolveFault(pathId(ctx), request.acknowledged(), request.resolvedIn())) {
            throw Refusal.BEACON_FAULT_NOT_HERE.raise();
        }
        ctx.status(io.javalin.http.HttpStatus.NO_CONTENT);
    }

    private void collectedReports(Context ctx) {
        requireBeacon();
        ctx.json(collected.reports(Boolean.parseBoolean(ctx.queryParam("includeAcknowledged"))));
    }

    private void acknowledgeReport(Context ctx) {
        requireBeacon();
        if (!collected.acknowledgeReport(pathId(ctx))) throw Refusal.BEACON_REPORT_NOT_ACKNOWLEDGED.raise();
        ctx.status(io.javalin.http.HttpStatus.NO_CONTENT);
    }

    /**
     * The picture that came with a report this beacon was sent.
     *
     * <p>Served from the report rather than from the file library: a picture forwarded here is of a
     * page of somebody else's installation, and it has no business appearing in a list of files
     * anybody browses.
     */
    private void collectedReportScreenshot(Context ctx) {
        requireBeacon();
        var report = collected.reports(true).stream()
                .filter(row -> row.id() == pathId(ctx))
                .findFirst()
                .orElseThrow(Refusal.BEACON_REPORT_NOT_HERE_FOR_PICTURE::raise);
        if (report.screenshotFileId() == null) throw Refusal.BEACON_REPORT_HAS_NO_PICTURE.raise();
        var picture =
                pictures.read(report.screenshotFileId()).orElseThrow(Refusal.BEACON_REPORT_PICTURE_NOT_HERE::raise);
        ctx.contentType(picture.contentType()).result(picture.data());
    }

    private void collectedMetrics(Context ctx) {
        requireBeacon();
        String raw = ctx.queryParam("days");
        int days = raw == null ? 30 : Math.clamp(Integer.parseInt(raw), 1, 365);
        ctx.json(collected.metrics(days));
    }

    private int pathId(Context ctx) {
        try {
            return Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw Refusal.BEACON_ID_NOT_A_NUMBER.raise();
        }
    }

    /** Whether a fault has been seen, and which version put it right. */
    public record ResolveRequest(boolean acknowledged, String resolvedIn) {}

    /**
     * What this instance is set up to do, so the screens can say so rather than guess.
     *
     * @param enabled       whether anything is sent at all
     * @param url           the beacon being reported to
     * @param receiving     whether this instance is itself a beacon
     * @param contactName   a name a beacon may answer on, empty where none was given
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

    private void status(Context ctx) {
        ctx.json(new BeaconStatus(
                config.enabled(),
                config.url(),
                config.forwardProblems(),
                config.forwardReports(),
                config.reviewReportPictures(),
                config.metricsEnabled(),
                config.receiving(),
                config.contactName(),
                config.contactMail()));
    }

    /**
     * What an operator chose, written down.
     *
     * <p>Stored rather than configured, so a change takes effect on the next entry rather than on the
     * next restart. Everything that reads these asks at the moment it matters.
     */
    private void updateSettings(Context ctx) {
        var request = ctx.bodyAsClass(SettingsRequest.class);
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
        status(ctx);
    }

    /**
     * The switches and the contact, as the screen sends them back.
     *
     * @param reviewReportPictures whether a report carrying a picture waits for somebody here before
     *     it is passed on. On unless an operator says otherwise
     */
    public record SettingsRequest(
            boolean enabled,
            String url,
            boolean forwardProblems,
            boolean forwardReports,
            boolean reviewReportPictures,
            boolean metricsEnabled,
            boolean receiving,
            String contactName,
            String contactMail) {}

    private ProblemLogAppender.Snapshot entry(Context ctx) {
        long id;
        try {
            id = Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw Refusal.BEACON_PROBLEM_ID_NOT_A_NUMBER.raise();
        }
        var appender = ProblemLogAppender.instance();
        if (appender == null) throw Refusal.PROBLEM_LOG_NOT_RUNNING.raise();
        return appender.getProblems(true).stream()
                .filter(problem -> problem.id() == id)
                .map(ProblemLogAppender.ProblemEntry::snapshot)
                .findFirst()
                .orElseThrow(Refusal.BEACON_PROBLEM_NOT_HERE::raise);
    }

    /** The exact payload one problem would travel as, shown before anything is sent. */
    private void previewProblem(Context ctx) {
        ctx.json(reports.payloadFor(entry(ctx), updates.currentVersion()));
    }

    private void sendProblem(Context ctx) {
        requireEnabled();
        ctx.json(new SendResult(reports.send(entry(ctx), updates.currentVersion()) ? 1 : 0));
    }

    /** What the list's own action sends: the ticked entries, in one go. */
    private void sendProblems(Context ctx) {
        requireEnabled();
        var request = ctx.bodyAsClass(SendRequest.class);
        if (request.ids() == null || request.ids().isEmpty()) {
            throw Refusal.BEACON_NOTHING_CHOSEN_TO_SEND.raise();
        }
        var appender = ProblemLogAppender.instance();
        if (appender == null) throw Refusal.PROBLEM_LOG_NOT_RUNNING_ON_SEND.raise();
        var chosen = appender.getProblems(true).stream()
                .filter(problem -> request.ids().contains(problem.id()))
                .map(ProblemLogAppender.ProblemEntry::snapshot)
                .toList();
        ctx.json(new SendResult(reports.sendAll(chosen, updates.currentVersion())));
    }

    /**
     * What one report would be sent as.
     *
     * <p>A report is a person talking, so the bytes matter more here than anywhere else: the message
     * is theirs, and the only honest way to ask an operator to pass it on is to show what goes.
     */
    private void previewReport(Context ctx) {
        ctx.json(reports.reportPayloadFor(report(ctx), updates.currentVersion()));
    }

    /**
     * Passes one report on now, whatever the automatic switch says.
     *
     * <p>The switch governs what leaves on its own; a button is an operator deciding about this one
     * report in front of them. That is also the only way a report written before the switch was
     * turned on can ever reach a beacon.
     */
    private void sendReport(Context ctx) {
        requireEnabled();
        var report = report(ctx);
        var decision =
                ctx.body().isBlank() ? new SendReportRequest(null, false) : ctx.bodyAsClass(SendReportRequest.class);

        Integer pictureId = report.screenshotFileId();
        boolean temporary = false;
        if (decision.dropScreenshot()) {
            pictureId = null;
        } else if (decision.screenshot() != null && !decision.screenshot().isBlank()) {
            var covered = pictures.store(decision.screenshot(), null);
            if (covered.isPresent()) {
                pictureId = covered.get();
                temporary = true;
            }
        }

        byte[] bytes = null;
        String type = null;
        if (pictureId != null) {
            var picture = pictures.read(pictureId);
            if (picture.isPresent()) {
                bytes = picture.get().data();
                type = picture.get().contentType();
            }
        }
        boolean queued = reports.sendReportNow(report, updates.currentVersion(), bytes, type);
        if (queued) problemReports.markForwarded(report.id());
        if (temporary) pictures.forget(pictureId);
        ctx.json(new SendResult(queued ? 1 : 0));
    }

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
    public record SendReportRequest(String screenshot, boolean dropScreenshot) {}

    private ProblemReport report(Context ctx) {
        return problemReports.findById(pathId(ctx)).orElseThrow(Refusal.BEACON_REPORT_NOT_HERE::raise);
    }

    /** The day's numbers as they would go, so an operator can see what "how much" means. */
    private void previewMetrics(Context ctx) {
        ctx.json(metrics.batch(updates.currentVersion(), Instant.now()));
    }

    private void requireEnabled() {
        if (!config.enabled()) {
            throw Refusal.BEACON_NOT_SET_UP.raise();
        }
    }

    /** The problems an operator ticked. */
    public record SendRequest(List<Long> ids) {}

    /** How many were handed to the sender. */
    public record SendResult(int queued) {}
}
