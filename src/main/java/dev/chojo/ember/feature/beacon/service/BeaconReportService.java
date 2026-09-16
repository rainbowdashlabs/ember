/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.discovery.service.DiscoveryHttpClient;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.service.ProblemLogAppender;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sending this instance's problems to its beacon.
 *
 * <p>Nothing here is called from the logging path. The error log captures every WARN and ERROR,
 * including the ones this class writes when a send fails, so forwarding straight from the appender
 * would let a failing beacon feed itself: the send fails, the failure is logged, the log becomes a
 * problem, the problem is forwarded, the send fails. Work goes on a bounded queue instead, and this
 * class's own logger is the one the forwarder never forwards.
 */
@Singleton
public class BeaconReportService {

    /** The logger whose own output is never forwarded, so a failing beacon cannot feed itself. */
    public static final String OWN_LOGGER = BeaconReportService.class.getName();

    /** Reads what a beacon answers a picture with, which is the number the report then names. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Logger log = LoggerFactory.getLogger(BeaconReportService.class);
    private static final int QUEUE_CAPACITY = 200;
    private static final long BACKOFF_SECONDS = 30;

    /** How many wordings of one fault travel. Enough to tell them apart, not a log shipped whole. */
    private static final int MAX_DISTINCT_MESSAGES = 10;

    private static final int MAX_MESSAGE_CHARS = 4000;

    /** A mail address, which is the one thing in a message that is a person and never a diagnosis. */
    private static final Pattern MAIL_ADDRESS =
            Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+", Pattern.CASE_INSENSITIVE);

    /** How the screen writes down one call it made, which is what a query has to be taken out of. */
    private static final Pattern REQUEST_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]*)\"");

    private final BeaconSettings config;
    private final DiscoveryHttpClient httpClient;
    private final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    @Inject
    public BeaconReportService(BeaconSettings config, DiscoveryHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        startWorker();
    }

    private void startWorker() {
        var worker = Executors.newSingleThreadExecutor(runnable -> {
            var thread = new Thread(runnable, "beacon-sender");
            thread.setDaemon(true);
            return thread;
        });
        worker.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    queue.take().run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("A beacon send failed", e);
                    sleepBackoff();
                }
            }
        });
    }

    private void sleepBackoff() {
        try {
            TimeUnit.SECONDS.sleep(BACKOFF_SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Starts forwarding every new problem as it appears, if the operator asked for that.
     *
     * <p>The appender hands entries over on the thread that logged them, so nothing here does more
     * than put one on the queue. Entries the beacon itself logged never arrive: the appender leaves
     * them out, which is what stops a failing beacon from feeding itself.
     *
     * <p>The listener is always registered and asks the setting each time. Deciding once at boot
     * would mean an operator switching forwarding on had to restart before anything went, which is
     * the whole reason these settings are stored rather than configured.
     *
     * @param version this instance's version
     */
    public void startForwarding(String version) {
        var appender = ProblemLogAppender.instance();
        if (appender == null) return;
        appender.onNewProblem(entry -> {
            if (config.forwardProblems()) send(entry.snapshot(), version);
        });
    }

    /**
     * The payload one problem would be sent as, without sending it.
     *
     * <p>What the operator is shown before the button does anything. An exception message quotes
     * what failed, and what failed is sometimes somebody's address, so the only honest way to ask
     * for consent is to show the bytes.
     *
     * @param entry   the problem as the local log holds it
     * @param version this instance's version
     * @return the payload, ready to be shown or sent
     */
    public BeaconPayloads.ProblemPayload payloadFor(ProblemLogAppender.Snapshot entry, String version) {
        return new BeaconPayloads.ProblemPayload(
                envelope(),
                version,
                blankToNull(config.contactName()),
                blankToNull(config.contactMail()),
                BeaconFingerprint.of(entry.exceptionClass(), entry.stacktrace(), entry.logger()),
                entry.level(),
                entry.logger(),
                entry.exceptionClass(),
                wordsOf(entry),
                String.join("\n", BeaconFingerprint.frameNames(entry.stacktrace())),
                entry.count(),
                entry.firstOccurrence(),
                entry.lastOccurrence());
    }

    /**
     * What the fault was logged with, which is most of what names it.
     *
     * <p>A warning without an exception carries no class and no frames, so without this a beacon is
     * told a logger name and a count: several different failures logged by one class arrive as one
     * row nobody can act on. The distinct wordings the group gathered are carried too, capped,
     * because the same fingerprint often covers "failed: HTTP 409" and "failed: HTTP 500" and the
     * difference between them is the whole of the diagnosis.
     *
     * <p>Mail addresses are taken out. A message quotes what failed and sometimes what failed is a
     * person's address, and unlike a path or an identifier it is never the thing that names the
     * fault. Everything else is left as it was written: an address inside the product, an
     * identifier, a status code, all of which are what makes a fault findable again.
     */
    private static String wordsOf(ProblemLogAppender.Snapshot entry) {
        var words = new LinkedHashSet<String>();
        if (entry.exceptionMessage() != null && !entry.exceptionMessage().isBlank()) {
            words.add(entry.exceptionMessage().strip());
        }
        for (String message : entry.distinctMessages()) {
            if (message != null && !message.isBlank()) words.add(message.strip());
            if (words.size() >= MAX_DISTINCT_MESSAGES) break;
        }
        String joined = String.join("\n", words);
        String withoutMail = MAIL_ADDRESS.matcher(joined).replaceAll("[mail]");
        return withoutMail.length() > MAX_MESSAGE_CHARS ? withoutMail.substring(0, MAX_MESSAGE_CHARS) : withoutMail;
    }

    /**
     * Queues one problem for its beacon. Returns at once; the sending happens on the worker.
     *
     * @param entry   the problem being forwarded
     * @param version this instance's version
     * @return whether it was queued, false when the queue is full or reporting is off
     */
    public boolean send(ProblemLogAppender.Snapshot entry, String version) {
        if (!config.enabled()) return false;
        return queue.offer(() -> deliver("/api/v1/beacon/problems", payloadFor(entry, version)));
    }

    /**
     * Queues a set of problems, which is what the list's own action sends.
     *
     * @param entries the problems being forwarded
     * @param version this instance's version
     * @return how many were queued
     */
    public int sendAll(List<ProblemLogAppender.Snapshot> entries, String version) {
        int queued = 0;
        for (var entry : entries) {
            if (send(entry, version)) queued++;
        }
        return queued;
    }

    /**
     * Queues one problem report, stripped of everything that names the person who wrote it.
     *
     * <p>Asks {@link BeaconSettings#forwardReports()} itself rather than trusting the caller to,
     * the same way the problem listener does. A stacktrace is the machine talking and a report is a
     * person, so the two are agreed to separately, and a switch that only the caller checks is one
     * the next caller forgets.
     *
     * @param report  the report as the station holds it
     * @param version this instance's version
     * @return whether it was queued
     */
    public boolean sendReport(ProblemReport report, String version) {
        if (!config.forwardReports()) return false;
        if (report.hasScreenshot() && config.reviewReportPictures()) return false;
        return sendReportNow(report, version);
    }

    /**
     * Whether this report is waiting for somebody here to look at its picture before it goes.
     *
     * <p>Only a report with a picture ever waits, and only where the operator asked for that. A
     * report waiting is not a report refused: it goes when somebody sends it, complete.
     */
    public boolean waitsForReview(ProblemReport report) {
        return config.forwardReports() && report.hasScreenshot() && config.reviewReportPictures();
    }

    /** Whether this report goes of its own accord, rather than waiting or not going at all. */
    public boolean goesByItself(ProblemReport report) {
        return config.forwardReports() && !waitsForReview(report);
    }

    /**
     * Queues one report because somebody asked for this one, rather than because the switch is on.
     *
     * <p>The switch governs what leaves on its own. An operator pressing a button has decided about
     * the report in front of them, and it is the only way one written before the switch was turned on
     * ever reaches a beacon.
     *
     * @return whether it was queued, false when the queue is full
     */
    public boolean sendReportNow(ProblemReport report, String version) {
        return sendReportNow(report, version, null, null);
    }

    /**
     * Sends one report and the picture it was written with, in that order and only together.
     *
     * <p>The picture goes first and the report second, naming what the beacon numbered it. A picture
     * that does not arrive stops the report going at all, so a beacon never holds a report claiming a
     * picture it has not got: the other way round it would, and a half report is the one thing this
     * is arranged to avoid.
     *
     * @param picture     the picture as it is to leave, already covered, or null to send the report alone
     * @param contentType what those bytes are
     * @return whether the work was queued, false when the queue is full
     */
    public boolean sendReportNow(ProblemReport report, String version, byte[] picture, String contentType) {
        if (picture == null || picture.length == 0) {
            return queue.offer(() -> deliver("/api/v1/beacon/reports", reportPayloadFor(report, version, null)));
        }
        var image = new BeaconPayloads.ReportImagePayload(
                envelope(), contentType, Base64.getEncoder().encodeToString(picture));
        return queue.offer(() -> {
            var numbered = deliverPicture(image);
            if (numbered.isEmpty()) {
                log.warn("The picture of a report was not taken, so the report was not sent either");
                return;
            }
            deliver("/api/v1/beacon/reports", reportPayloadFor(report, version, numbered.get()));
        });
    }

    /**
     * Hands over the picture and reads back the number the beacon gave it.
     *
     * @return the number, or empty where the beacon could not be reached or turned the picture away
     */
    private Optional<Integer> deliverPicture(BeaconPayloads.ReportImagePayload image) {
        var answer = httpClient.beaconPost(config.url(), "/api/v1/beacon/report-images", image);
        if (answer.isEmpty() || !answer.get().accepted()) {
            log.warn("The beacon at {} did not take the picture of a report", config.url());
            return Optional.empty();
        }
        try {
            var taken = JSON.readValue(answer.get().body(), BeaconPayloads.ReportImageAccepted.class);
            return Optional.of(taken.imageId());
        } catch (RuntimeException e) {
            log.warn("The beacon at {} took the picture but did not say what it numbered it", config.url());
            return Optional.empty();
        }
    }

    /**
     * Hands one payload to the beacon and says so where it was turned away.
     *
     * <p>A refusal used to be a {@code false} nobody read: a closed port, a wrong address and a
     * rejected signature all looked alike and none of them reached a log. An operator who had
     * switched forwarding on then had nothing to go on but an empty beacon, which is exactly how a
     * missing header went unnoticed through several releases.
     */
    private void deliver(String path, Object payload) {
        var answer = httpClient.beaconPost(config.url(), path, payload);
        if (answer.isEmpty()) {
            log.warn("The beacon at {} could not be reached for {}", config.url(), path);
            return;
        }
        if (!answer.get().accepted()) {
            log.warn(
                    "The beacon at {} answered {} for {}: {}",
                    config.url(),
                    answer.get().status(),
                    path,
                    answer.get().body());
        }
    }

    /**
     * The payload one report would be sent as, without sending it.
     *
     * <p>The counterpart of {@link #payloadFor}, and for the same reason: what leaves the instance is
     * decided here, so here is where it can be read.
     *
     * <p>What travels is everything about the screen and nothing about the person. Who wrote it,
     * which member they are and which station they belong to stay at home; the browser, the size of
     * the window, the rights they held and the calls the screen had just made all go, because
     * "the button did nothing" names no defect without them.
     *
     * @param report  the report as the station holds it
     * @param version this instance's version
     * @return the payload, ready to be shown or sent
     */
    public BeaconPayloads.ReportPayload reportPayloadFor(ProblemReport report, String version) {
        return reportPayloadFor(report, version, null);
    }

    /**
     * The same payload, naming the picture a beacon has just taken.
     *
     * @param imageId what the beacon numbered the picture, or null where the report carries none
     */
    public BeaconPayloads.ReportPayload reportPayloadFor(ProblemReport report, String version, Integer imageId) {
        return new BeaconPayloads.ReportPayload(
                envelope(),
                version,
                blankToNull(config.contactName()),
                blankToNull(config.contactMail()),
                report.message(),
                withoutQuery(report.pageUrl()),
                blankToNull(report.browserInfo()),
                blankToNull(report.screenSize()),
                blankToNull(report.userRoles()),
                requestsWithoutQueries(report.recentRequests()),
                report.createdAt(),
                imageId);
    }

    /**
     * The calls the screen made, with every address stripped of its query the way the page is.
     *
     * <p>They travel as the screen recorded them, which is JSON. A query carries what somebody
     * searched for and sometimes who they looked at, and none of that says which call went wrong,
     * so the same rule applies to each of them as to the page the report was written on.
     */
    private static String requestsWithoutQueries(String recentRequests) {
        if (recentRequests == null || recentRequests.isBlank()) return null;
        String stripped = REQUEST_URL
                .matcher(recentRequests)
                .replaceAll(match -> Matcher.quoteReplacement("\"url\":\"" + withoutQuery(match.group(1)) + "\""));
        return stripped.length() > MAX_MESSAGE_CHARS ? stripped.substring(0, MAX_MESSAGE_CHARS) : stripped;
    }

    /**
     * The address without its query string.
     *
     * <p>A query carries what somebody searched for and sometimes who they looked at, none of which
     * a beacon needs to know which page went wrong. The screen already sends the path alone; this is
     * the guarantee rather than the hope, because what leaves the instance is decided here.
     */
    private static String withoutQuery(String page) {
        if (page == null) return null;
        int query = page.indexOf('?');
        return query < 0 ? page : page.substring(0, query);
    }

    /**
     * A fresh envelope. The nonce and the issue time are what stop a captured delivery being replayed,
     * and the audience is what stops one captured by one beacon being handed to another.
     */
    private BeaconPayloads.Envelope envelope() {
        return new BeaconPayloads.Envelope(
                BeaconPayloads.PROTOCOL_VERSION,
                Instant.now(),
                UUID.randomUUID().toString(),
                config.url());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
