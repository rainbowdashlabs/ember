/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.beacon.repository.BeaconReadRepository;
import dev.chojo.ember.feature.beacon.service.BeaconMetricsService;
import dev.chojo.ember.feature.beacon.service.BeaconReportService;
import dev.chojo.ember.feature.beacon.service.BeaconSettings;
import dev.chojo.ember.feature.system.service.ProblemLogAppender;
import dev.chojo.ember.feature.system.service.UpdateCheckService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
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

    @Inject
    public BeaconAdminRoutes(
            BeaconSettings config,
            BeaconReportService reports,
            BeaconMetricsService metrics,
            UpdateCheckService updates,
            BeaconReadRepository collected) {
        this.config = config;
        this.reports = reports;
        this.metrics = metrics;
        this.updates = updates;
        this.collected = collected;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/admin/beacon";
        routes.get(base, this::status, InstancePermission.ADMINISTRATOR);
        routes.put(base, this::updateSettings, InstancePermission.ADMINISTRATOR);
        routes.get(base + "/problems/{id}/preview", this::previewProblem, InstancePermission.ADMINISTRATOR);
        routes.post(base + "/problems/{id}/send", this::sendProblem, InstancePermission.ADMINISTRATOR);
        routes.post(base + "/problems/send", this::sendProblems, InstancePermission.ADMINISTRATOR);
        routes.get(base + "/figures/preview", this::previewMetrics, InstancePermission.ADMINISTRATOR);

        routes.get(base + "/collected/faults", this::faults, InstancePermission.ADMINISTRATOR);
        routes.put(base + "/collected/faults/{id}", this::resolveFault, InstancePermission.ADMINISTRATOR);
        routes.get(base + "/collected/reports", this::collectedReports, InstancePermission.ADMINISTRATOR);
        routes.post(
                base + "/collected/reports/{id}/acknowledge",
                this::acknowledgeReport,
                InstancePermission.ADMINISTRATOR);
        routes.get(base + "/collected/figures", this::collectedMetrics, InstancePermission.ADMINISTRATOR);
    }

    /** What a beacon has gathered is only worth asking for when this instance is one. */
    private void requireBeacon() {
        if (!config.receiving()) throw new NotFoundResponse();
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
            throw new NotFoundResponse();
        }
        ctx.status(io.javalin.http.HttpStatus.NO_CONTENT);
    }

    private void collectedReports(Context ctx) {
        requireBeacon();
        ctx.json(collected.reports(Boolean.parseBoolean(ctx.queryParam("includeAcknowledged"))));
    }

    private void acknowledgeReport(Context ctx) {
        requireBeacon();
        if (!collected.acknowledgeReport(pathId(ctx))) throw new NotFoundResponse();
        ctx.status(io.javalin.http.HttpStatus.NO_CONTENT);
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
            throw new BadRequestResponse("That is not an id");
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
                request.metricsEnabled(),
                request.receiving(),
                request.contactName(),
                request.contactMail());
        status(ctx);
    }

    /** The switches and the contact, as the screen sends them back. */
    public record SettingsRequest(
            boolean enabled,
            String url,
            boolean forwardProblems,
            boolean forwardReports,
            boolean metricsEnabled,
            boolean receiving,
            String contactName,
            String contactMail) {}

    private ProblemLogAppender.Snapshot entry(Context ctx) {
        long id;
        try {
            id = Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("That is not a problem id");
        }
        var appender = ProblemLogAppender.instance();
        if (appender == null) throw new NotFoundResponse();
        return appender.getProblems(true).stream()
                .filter(problem -> problem.id() == id)
                .map(ProblemLogAppender.ProblemEntry::snapshot)
                .findFirst()
                .orElseThrow(NotFoundResponse::new);
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
            throw new BadRequestResponse("Nothing was chosen");
        }
        var appender = ProblemLogAppender.instance();
        if (appender == null) throw new NotFoundResponse();
        var chosen = appender.getProblems(true).stream()
                .filter(problem -> request.ids().contains(problem.id()))
                .map(ProblemLogAppender.ProblemEntry::snapshot)
                .toList();
        ctx.json(new SendResult(reports.sendAll(chosen, updates.currentVersion())));
    }

    /** The day's numbers as they would go, so an operator can see what "how much" means. */
    private void previewMetrics(Context ctx) {
        ctx.json(metrics.batch(updates.currentVersion(), Instant.now()));
    }

    private void requireEnabled() {
        if (!config.enabled()) {
            throw new BadRequestResponse("This instance reports to no beacon");
        }
    }

    /** The problems an operator ticked. */
    public record SendRequest(List<Long> ids) {}

    /** How many were handed to the sender. */
    public record SendResult(int queued) {}
}
