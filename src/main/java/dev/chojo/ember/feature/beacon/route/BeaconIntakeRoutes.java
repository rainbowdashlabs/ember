/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.beacon.service.BeaconIntakeService;
import dev.chojo.ember.feature.beacon.service.BeaconSettings;
import dev.chojo.ember.feature.discovery.service.DiscoverySigningService;
import dev.chojo.ember.feature.system.service.ProblemReportScreenshotService;
import dev.chojo.ember.util.ClientIp;
import dev.chojo.ember.util.LeakyBucket;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Base64;

/**
 * The door a beacon opens to other instances.
 *
 * <p>Everything here is public by design: a beacon anybody may report to is the point, and the key a
 * report is signed with says who sent it rather than whether they were allowed to. What keeps that
 * from being an open drain is the order the checks run in.
 *
 * <p>The rate limit is keyed on the caller's address and applied <em>before</em> a signature is
 * verified. Keypairs are free, so a limit keyed on the sender's own identity would throttle honest
 * instances and nobody else: an attacker mints a fresh identity per request and gets a fresh bucket,
 * a fresh row and free signature verification every time. An address is the one thing a caller cannot
 * mint at will.
 */
@Singleton
public class BeaconIntakeRoutes implements Routes {

    private static final Logger log = LoggerFactory.getLogger(BeaconIntakeRoutes.class);

    /** The largest report a beacon reads. A stacktrace is small; nothing honest here is large. */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    /**
     * The largest picture a beacon reads, which is its own limit and not the one above.
     *
     * <p>A picture is thousands of times a sentence, and lifting the report's cap to fit one would
     * let every other delivery grow with it. Base64 in JSON, so the room is the picture's own ceiling
     * on the sending side with the third that encoding adds, and a little over.
     */
    private static final int MAX_IMAGE_BODY_BYTES = 5 * 1024 * 1024;

    private static final int BURST = 30;
    private static final int PER_MINUTE = 10;

    private final BeaconSettings config;
    private final Api api;
    private final Network network;
    private final BeaconIntakeService intake;
    private final DiscoverySigningService signing;
    private final ProblemReportScreenshotService pictures;
    private final LeakyBucket limiter = new LeakyBucket(BURST, PER_MINUTE, Duration.ofHours(1));

    @Inject
    public BeaconIntakeRoutes(
            BeaconSettings config,
            Api api,
            Network network,
            BeaconIntakeService intake,
            DiscoverySigningService signing,
            ProblemReportScreenshotService pictures) {
        this.config = config;
        this.api = api;
        this.network = network;
        this.intake = intake;
        this.signing = signing;
        this.pictures = pictures;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/beacon";
        routes.post(base + "/problems", this::takeProblem);
        routes.post(base + "/reports", this::takeReport);
        routes.post(base + "/report-images", this::takeReportImage);
        routes.post(base + "/figures", this::takeMetrics);
    }

    /** A beacon that is not one answers nothing, so an instance is never a beacon by accident. */
    private void requireBeacon() {
        if (!config.receiving()) throw new NotFoundResponse();
    }

    /**
     * The body, refused when it is larger than anything honest would be, and the address limit, both
     * before a single signature is checked.
     */
    private String guardedBody(Context ctx) {
        return guardedBody(ctx, MAX_BODY_BYTES);
    }

    private String guardedBody(Context ctx, int maxBytes) {
        requireBeacon();
        String address = ClientIp.resolve(ctx, network).getHostAddress();
        if (limiter.tryAcquire(address).isPresent()) {
            throw new ForbiddenResponse("Too many reports from this address");
        }
        String body = ctx.body();
        if (body.length() > maxBytes) {
            throw new BadRequestResponse("That report is larger than a beacon reads");
        }
        return body;
    }

    /**
     * Establishes who signed a delivery.
     *
     * <p>The key travels in a header and the identifier is computed from it. Nothing in the body is
     * consulted for identity, so no sender can claim to be another.
     *
     * <p>The audience is checked against this instance's own address, not against the beacon it
     * reports to itself. Those are opposite directions, and a beacon that does not report anywhere
     * has no second address at all: comparing against the outbound setting turned every signed
     * delivery away with "addressed to another beacon" on any instance whose beacon happened not to
     * be itself.
     */
    private Sender senderOf(Context ctx, String body, BeaconPayloads.Envelope envelope) {
        String key = ctx.header(DiscoverySigningService.BEACON_KEY_HEADER);
        String signature = ctx.header(DiscoverySigningService.SIGNATURE_HEADER);
        if (key == null || signature == null) {
            throw new ForbiddenResponse("A report has to be signed");
        }
        if (!signing.verify(body, signature, key)) {
            throw new ForbiddenResponse("That signature does not match");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenResponse("That is not a key");
        }
        return new Sender(intake.accept(raw, envelope, api.baseUrl()), key);
    }

    private void takeProblem(Context ctx) {
        String body = guardedBody(ctx);
        var payload = ctx.bodyAsClass(BeaconPayloads.ProblemPayload.class);
        var sender = senderOf(ctx, body, payload.envelope());
        intake.storeProblem(sender.instanceId(), sender.publicKey(), payload);
        ctx.status(HttpStatus.ACCEPTED);
    }

    private void takeReport(Context ctx) {
        String body = guardedBody(ctx);
        var payload = ctx.bodyAsClass(BeaconPayloads.ReportPayload.class);
        var sender = senderOf(ctx, body, payload.envelope());
        intake.storeReport(sender.instanceId(), sender.publicKey(), payload);
        ctx.status(HttpStatus.ACCEPTED);
    }

    /**
     * Takes the picture belonging to a report, which always arrives just before the report itself.
     *
     * <p>Answers with the number it gave the picture, because that is what the report names a moment
     * later. A picture nothing ever names is swept by age like everything else here, so a sender that
     * dies between the two leaves one file behind and not a half report.
     */
    private void takeReportImage(Context ctx) {
        String body = guardedBody(ctx, MAX_IMAGE_BODY_BYTES);
        var payload = ctx.bodyAsClass(BeaconPayloads.ReportImagePayload.class);
        var sender = senderOf(ctx, body, payload.envelope());
        intake.noteReportImage(sender.instanceId(), sender.publicKey());
        int imageId = pictures.store(payload.data(), null)
                .orElseThrow(() -> new BadRequestResponse("A picture delivery needs a picture"));
        ctx.status(HttpStatus.ACCEPTED).json(new BeaconPayloads.ReportImageAccepted(imageId));
    }

    /**
     * The daily numbers, which carry no identity and are therefore not signed.
     *
     * <p>The address limit is the whole defence here, and it is worth being plain that it is a thin
     * one: anybody who learns a metrics identifier can write that identifier's day. Last write wins,
     * so an honest report corrects a dishonest one, and nothing here is a number anybody is billed
     * on.
     */
    private void takeMetrics(Context ctx) {
        guardedBody(ctx);
        var batch = ctx.bodyAsClass(BeaconPayloads.MetricsBatch.class);
        if (batch.protocolVersion() > BeaconPayloads.PROTOCOL_VERSION) {
            throw new BadRequestResponse("This beacon does not speak that protocol version yet");
        }
        int stored = intake.storeMetrics(batch);
        log.debug("Took {} metrics subject(s)", stored);
        ctx.status(HttpStatus.ACCEPTED);
    }

    /** Who sent a delivery: the identifier computed from their key, and the key itself. */
    private record Sender(String instanceId, String publicKey) {}
}
