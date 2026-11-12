/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.api.refusal.BeaconRefusal;
import dev.chojo.ember.api.refusal.BodyRefusal;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.discovery.service.DiscoveryHttpClient;
import dev.chojo.ember.feature.system.entity.ProblemContext;
import dev.chojo.ember.feature.system.entity.ProblemContextLine;
import dev.chojo.ember.feature.system.entity.ProblemContextState;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.entity.StoredProblem;
import dev.chojo.ember.feature.system.service.ProblemLogFlusher;
import dev.chojo.ember.feature.system.service.ProblemLogService;
import dev.chojo.ember.lifecycle.SerialLane;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.util.Json;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sending this instance's problems to its beacon.
 *
 * <p>Nothing here is called from the logging path. The error log captures every WARN and ERROR,
 * including the ones this class writes when a send fails, so forwarding straight from the appender
 * would let a failing beacon feed itself: the send fails, the failure is logged, the log becomes a
 * problem, the problem is forwarded, the send fails. Work goes on a bounded queue instead, and the
 * problem log never hands on anything logged under the beacon's package.
 */
@Singleton
public class BeaconReportService {

    /** Reads what a beacon answers a picture with, which is the number the report then names. */
    private static final JsonMapper JSON = Json.MAPPER;

    private static final Logger log = LoggerFactory.getLogger(BeaconReportService.class);
    private static final int QUEUE_CAPACITY = 200;
    private static final long BACKOFF_SECONDS = 30;

    /** How many wordings of one fault travel. Enough to tell them apart, not a log shipped whole. */
    private static final int MAX_DISTINCT_MESSAGES = 10;

    private static final int MAX_MESSAGE_CHARS = 4000;

    /** How long a log line sent with a fault may run. */
    static final int MAX_LINE_CHARS = 1000;

    /**
     * How many characters the lines sent with one fault hold together, well inside the 64 KiB a beacon
     * reads of one delivery, with room for the fault's own words and frames.
     */
    static final int CONTEXT_BUDGET = 24_000;

    /** What one line costs on top of its texts: its time, its level and the keys around them. */
    private static final int LINE_OVERHEAD = 100;

    /** The levels a line must have to leave the instance. */
    private static final Set<String> SENT_LEVELS = Set.of("INFO", "WARN", "ERROR");

    /**
     * How long an automatic forward with lines waits after the fault was written: the window after the
     * occurrence, plus the time the stored log takes to write what was logged in it, plus a margin.
     * Sent sooner, the lines after the fault would be missing.
     */
    static final Duration CONTEXT_DELAY = ProblemLogService.CONTEXT_AFTER.plusSeconds(5);

    /** How the screen writes down one call it made, which is what a query has to be taken out of. */
    private static final Pattern REQUEST_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]*)\"");

    private final BeaconSettings config;
    private final DiscoveryHttpClient httpClient;
    private final SelfDelivery self;
    private final TaskScheduler scheduler;
    private final SerialLane lane;
    private final BeaconRedaction redaction;
    private final ProblemLogService problemLog;

    @Inject
    public BeaconReportService(
            BeaconSettings config,
            DiscoveryHttpClient httpClient,
            SelfDelivery self,
            TaskScheduler scheduler,
            BeaconRedaction redaction,
            ProblemLogService problemLog) {
        this.config = config;
        this.httpClient = httpClient;
        this.self = self;
        this.scheduler = scheduler;
        this.lane = scheduler.lane("beacon-sender", QUEUE_CAPACITY);
        this.redaction = redaction;
        this.problemLog = problemLog;
    }

    /**
     * Puts one send on the lane. A send that fails holds the lane for the backoff, so a beacon that is
     * down is not asked again at once for everything queued behind it.
     */
    private boolean enqueue(Runnable send) {
        return lane.submit(() -> {
            try {
                send.run();
            } catch (RuntimeException e) {
                log.warn("A beacon send failed", e);
                sleepBackoff();
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
     * <p>The problem log hands over a problem once it is written, when it is new or came back after it
     * was resolved, so nothing here does more than put one on the queue. A muted problem never arrives,
     * and neither does one the beacon itself logged: the problem log leaves those out, which is what
     * stops a failing beacon from feeding itself.
     *
     * <p>The listener is always registered and asks the setting each time. Deciding once at boot
     * would mean an operator switching forwarding on had to restart before anything went, which is
     * the whole reason these settings are stored rather than configured.
     *
     * <p>Where the operator also asked for the log lines around a fault, the send waits
     * {@link #CONTEXT_DELAY}, so the lines logged just after the fault have reached the stored log. Both
     * switches are asked again when it goes, so turning either off in the meantime is honoured.
     *
     * @param problems the problem log's writer, which says when a problem is new
     * @param version  this instance's version
     */
    public void startForwarding(ProblemLogFlusher problems, String version) {
        problems.onNewOrReopened(problem -> {
            if (!config.forwardProblems()) return;
            if (!config.forwardLogContext()) {
                send(problem, version, false);
                return;
            }
            scheduler.later("beacon-problem-context", CONTEXT_DELAY, () -> {
                if (config.forwardProblems()) send(problem, version, config.forwardLogContext());
            });
        });
    }

    /**
     * The payload one problem would be sent as, without sending it.
     *
     * <p>What the operator is shown before the button does anything. An exception message quotes
     * what failed, and what failed is sometimes somebody's address, so the only honest way to ask
     * for consent is to show the bytes.
     *
     * <p>Every text in it goes through one {@link BeaconRedaction.Redactor}, the words the fault was
     * logged with as much as the lines around it, so both follow one rule.
     *
     * @param entry       the problem as the local log holds it
     * @param version     this instance's version
     * @param withContext whether the log lines around its latest occurrence go with it
     * @return the payload, ready to be shown or sent
     */
    public BeaconPayloads.ProblemPayload payloadFor(StoredProblem entry, String version, boolean withContext) {
        return previewFor(entry, version, withContext).payload();
    }

    /**
     * The payload one problem would be sent as, and the occurrence its lines surround, so that sending
     * it later can send the lines that were shown rather than those around a newer occurrence.
     *
     * @param entry       the problem as the local log holds it
     * @param version     this instance's version
     * @param withContext whether the log lines around its latest occurrence go with it
     * @return the payload and the occurrence, which is null where no lines go
     */
    public ProblemPreview previewFor(StoredProblem entry, String version, boolean withContext) {
        return build(entry, version, withContext, null, redaction.redactor());
    }

    private ProblemPreview build(
            StoredProblem entry,
            String version,
            boolean withContext,
            @Nullable Instant at,
            BeaconRedaction.Redactor redactor) {
        var context = withContext ? problemLog.context(entry.id(), at, false) : null;
        var lines = context == null || !isAround(context, at) ? null : linesOf(context, redactor);
        Instant around = lines == null
                ? null
                : context.occurrences().get(context.occurrence()).occurredAt();
        var payload = new BeaconPayloads.ProblemPayload(
                envelope(),
                version,
                blankToNull(config.contactName()),
                blankToNull(config.contactMail()),
                BeaconFingerprint.of(entry.exceptionClass(), entry.stacktrace(), entry.logger()),
                entry.level().name(),
                entry.logger(),
                entry.exceptionClass(),
                wordsOf(entry, redactor),
                String.join("\n", BeaconFingerprint.frameNames(entry.stacktrace())),
                (int) Math.min(Integer.MAX_VALUE, entry.count()),
                entry.firstSeen(),
                entry.lastSeen(),
                lines);
        return new ProblemPreview(payload, around);
    }

    /**
     * Whether the lines are around the occurrence that was asked for. One no longer kept, which a fault
     * recurring while its preview was read pushes out, is answered with the newest instead; those are
     * lines nobody was shown, so then none go.
     */
    private static boolean isAround(ProblemContext context, @Nullable Instant at) {
        if (at == null || context.occurrences().isEmpty()) return true;
        boolean same =
                context.occurrences().get(context.occurrence()).occurredAt().equals(at);
        if (!same) log.info("The occurrence a preview showed is no longer kept; sending the fault without lines");
        return same;
    }

    /**
     * The stored lines around an occurrence as they may leave: at INFO and above only, since DEBUG and
     * TRACE carry request bodies and dumps nobody outside should read; redacted; each cut to
     * {@link #MAX_LINE_CHARS}; and, where together they would outgrow {@link #CONTEXT_BUDGET}, the
     * lines furthest from the occurrence left out first, so a delivery always fits what a beacon reads.
     *
     * @return the lines oldest first, or null where none are kept, so nothing is sent rather than an
     *     empty list
     */
    private static @Nullable List<BeaconPayloads.LogLinePayload> linesOf(
            ProblemContext context, BeaconRedaction.Redactor redactor) {
        if (context.state() != ProblemContextState.LINES) return null;
        List<ProblemContextLine> lines = context.lines().stream()
                .filter(line -> SENT_LEVELS.contains(line.level()))
                .toList();
        if (lines.isEmpty()) return null;
        Instant at = context.occurrences().get(context.occurrence()).occurredAt();
        var redacted = new ArrayList<BeaconPayloads.LogLinePayload>(lines.size());
        for (var line : lines) {
            redacted.add(new BeaconPayloads.LogLinePayload(
                    line.loggedAt(),
                    line.level(),
                    line.logger(),
                    cut(redactor.redact(line.thread()), MAX_LINE_CHARS),
                    cut(redactor.redact(line.message()), MAX_LINE_CHARS)));
        }
        var closestFirst = new ArrayList<>(redacted);
        closestFirst.sort(Comparator.comparing(
                line -> Duration.between(line.loggedAt(), at).abs()));
        Set<BeaconPayloads.LogLinePayload> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        int used = 0;
        for (var line : closestFirst) {
            int size = line.message().length()
                    + line.thread().length()
                    + line.logger().length()
                    + LINE_OVERHEAD;
            if (used + size > CONTEXT_BUDGET) break;
            used += size;
            kept.add(line);
        }
        return redacted.stream().filter(kept::contains).toList();
    }

    private static String cut(@Nullable String text, int limit) {
        if (text == null) return "";
        return text.length() > limit ? text.substring(0, limit) : text;
    }

    /**
     * A payload as it was shown, and the occurrence its lines surround.
     *
     * @param payload    what would be sent
     * @param contextAt  when the occurrence the lines surround happened, or null where no lines go
     */
    public record ProblemPreview(
            BeaconPayloads.ProblemPayload payload, @Nullable Instant contextAt) {}

    /**
     * What the fault was logged with, which is most of what names it.
     *
     * <p>A warning without an exception carries no class and no frames, so without this a beacon is
     * told a logger name and a count: several different failures logged by one class arrive as one
     * row nobody can act on. The distinct wordings the group gathered are carried too, capped,
     * because the same fingerprint often covers "failed: HTTP 409" and "failed: HTTP 500" and the
     * difference between them is the whole of the diagnosis.
     *
     * <p>Mail addresses, identifiers and the names of accounts are taken out by {@link BeaconRedaction}.
     * A message quotes what failed and sometimes what failed is a person, which is never the thing
     * that names the fault.
     */
    private static String wordsOf(StoredProblem entry, BeaconRedaction.Redactor redactor) {
        var words = new LinkedHashSet<String>();
        String exceptionMessage = entry.exceptionMessage();
        if (exceptionMessage != null && !exceptionMessage.isBlank()) {
            words.add(exceptionMessage.strip());
        }
        for (String message : entry.distinctMessages()) {
            if (message != null && !message.isBlank()) words.add(message.strip());
            if (words.size() >= MAX_DISTINCT_MESSAGES) break;
        }
        String joined = String.join("\n", words);
        return cut(redactor.redact(joined), MAX_MESSAGE_CHARS);
    }

    /**
     * Queues one problem for its beacon. Returns at once; the sending happens on the lane.
     *
     * <p>The payload is built on the lane, when it goes, so the lines sent with it are the ones the
     * stored log holds by then.
     *
     * @param entry       the problem being forwarded
     * @param version     this instance's version
     * @param withContext whether the log lines around it go with it
     * @return whether it was queued, false when the queue is full or reporting is off
     */
    public boolean send(StoredProblem entry, String version, boolean withContext) {
        return send(entry, version, withContext, null, null);
    }

    /**
     * Queues one problem with the lines around the occurrence an operator was shown.
     *
     * @param entry     the problem being forwarded
     * @param version   this instance's version
     * @param contextAt the occurrence the shown lines surround, or null to send no lines
     * @return whether it was queued
     */
    public boolean sendAsShown(StoredProblem entry, String version, @Nullable Instant contextAt) {
        return send(entry, version, contextAt != null, contextAt, null);
    }

    private boolean send(
            StoredProblem entry,
            String version,
            boolean withContext,
            @Nullable Instant at,
            BeaconRedaction.@Nullable Redactor shared) {
        if (!config.enabled()) return false;
        return enqueue(() -> {
            var redactor = shared == null ? redaction.redactor() : shared;
            deliver(
                    "/api/v1/beacon/problems",
                    build(entry, version, withContext, at, redactor).payload());
        });
    }

    /**
     * Queues a set of problems, which is what the list's own action sends.
     *
     * @param entries     the problems being forwarded
     * @param version     this instance's version
     * @param withContext whether the log lines around each go with it
     * @return how many were queued
     */
    public int sendAll(List<StoredProblem> entries, String version, boolean withContext) {
        if (entries.isEmpty()) return 0;
        var redactor = redaction.redactor();
        int queued = 0;
        for (var entry : entries) {
            if (send(entry, version, withContext, null, redactor)) queued++;
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
    public boolean sendReportNow(
            ProblemReport report, String version, byte @Nullable [] picture, @Nullable String contentType) {
        if (picture == null || picture.length == 0) {
            return enqueue(() -> deliver("/api/v1/beacon/reports", reportPayloadFor(report, version, null)));
        }
        var image = new BeaconPayloads.ReportImagePayload(
                envelope(), contentType, Base64.getEncoder().encodeToString(picture));
        return enqueue(() -> {
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
    /**
     * Writes a delivery straight into this instance's own beacon, where that is where it is going.
     *
     * @return whether it was taken, false for anything this does not know how to hand over, which
     *     then goes the way everything went before
     */
    private boolean takenBySelf(String path, Object payload) {
        if (payload instanceof BeaconPayloads.ReportPayload report) return self.takeReport(report);
        if (payload instanceof BeaconPayloads.ProblemPayload problem) return self.takeProblem(problem);
        return false;
    }

    private Optional<Integer> deliverPicture(BeaconPayloads.ReportImagePayload image) {
        if (self.isSelf()) return self.takePicture(image.data());
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
        if (self.isSelf() && takenBySelf(path, payload)) return;
        var answer = httpClient.beaconPost(config.url(), path, payload);
        if (answer.isEmpty()) {
            log.warn("The beacon at {} could not be reached for {}", config.url(), path);
            return;
        }
        if (refusedTheLines(payload, answer.get())) {
            log.info("The beacon at {} does not take log lines yet; sending the fault without them", config.url());
            deliver(path, ((BeaconPayloads.ProblemPayload) payload).withoutContext(envelope()));
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
     * Whether a beacon turned a fault away for the lines it carried: one from before lines were sent
     * refuses any field it does not know, and any beacon refuses a delivery larger than it reads.
     */
    private static boolean refusedTheLines(Object payload, DiscoveryHttpClient.Answer answer) {
        return payload instanceof BeaconPayloads.ProblemPayload problem
                && problem.context() != null
                && answer.status() == 400
                && answer.body() != null
                && (answer.body().contains(BodyRefusal.BODY_UNEXPECTED_FIELD.code())
                        || answer.body().contains(BeaconRefusal.BEACON_INTAKE_TOO_LARGE.code()));
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
    public BeaconPayloads.ReportPayload reportPayloadFor(
            ProblemReport report, String version, @Nullable Integer imageId) {
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
    private static @Nullable String requestsWithoutQueries(@Nullable String recentRequests) {
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
    private static @Nullable String withoutQuery(@Nullable String page) {
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

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
