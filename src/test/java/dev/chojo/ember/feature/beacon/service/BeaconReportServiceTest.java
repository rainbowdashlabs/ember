/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.api.refusal.BeaconRefusal;
import dev.chojo.ember.api.refusal.BodyRefusal;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.discovery.service.DiscoveryHttpClient;
import dev.chojo.ember.feature.system.entity.ProblemContext;
import dev.chojo.ember.feature.system.entity.ProblemContextLine;
import dev.chojo.ember.feature.system.entity.ProblemContextState;
import dev.chojo.ember.feature.system.entity.ProblemLevel;
import dev.chojo.ember.feature.system.entity.ProblemOccurrence;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.entity.ProblemState;
import dev.chojo.ember.feature.system.entity.StoredProblem;
import dev.chojo.ember.feature.system.service.ProblemLogFlusher;
import dev.chojo.ember.feature.system.service.ProblemLogService;
import dev.chojo.ember.lifecycle.SerialLane;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.util.Json;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What leaves an instance when somebody writes a problem report, and what does not.
 */
class BeaconReportServiceTest {

    private final DiscoveryHttpClient httpClient = mock(DiscoveryHttpClient.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final BeaconRedaction redaction = new BeaconRedaction(accounts);
    private final ProblemLogService problemLog = mock(ProblemLogService.class);

    private static final Instant AT = Instant.parse("2026-11-01T10:00:00Z");

    /** The stored log around a problem, one line naming a person, an identifier and an address. */
    private void linesAround(long problemId) {
        when(problemLog.context(problemId, null, false))
                .thenReturn(new ProblemContext(
                        ProblemContextState.LINES,
                        true,
                        List.of(new ProblemOccurrence(AT, "worker-1")),
                        0,
                        false,
                        List.of(new ProblemContextLine(
                                AT,
                                "INFO",
                                "dev.chojo.ember.A",
                                "worker-1",
                                "Mail for Nora Fülling (nora@example.com) about 5f0c6d1e-2b3a-4c5d-8e9f-0a1b2c3d4e5f",
                                false))));
    }

    /** A sender whose lane runs work at once and whose delayed work is handed to the test. */
    private BeaconReportService serviceOnHand(BeaconSettings config, TaskScheduler scheduler) {
        var lane = mock(SerialLane.class);
        when(lane.submit(any())).thenAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return true;
        });
        when(scheduler.lane(anyString(), anyInt())).thenReturn(lane);
        var self = mock(SelfDelivery.class);
        when(self.isSelf()).thenReturn(false);
        return new BeaconReportService(config, httpClient, self, scheduler, redaction, problemLog);
    }

    private static BeaconSettings sending(boolean forwardLogContext) {
        var config = mock(BeaconSettings.class);
        when(config.enabled()).thenReturn(true);
        when(config.forwardProblems()).thenReturn(true);
        when(config.forwardLogContext()).thenReturn(forwardLogContext);
        when(config.url()).thenReturn("https://beacon.test");
        when(config.contactName()).thenReturn("");
        when(config.contactMail()).thenReturn("");
        return config;
    }

    /**
     * A sender reporting to somebody else, which is what these stories are about.
     *
     * <p>An instance that is its own beacon never reaches the network at all, so it is told here
     * that it is not one: what it does instead is settled where that decision lives.
     */
    private BeaconReportService serviceThatForwards(boolean forwardReports) {
        var config = mock(BeaconSettings.class);
        when(config.forwardReports()).thenReturn(forwardReports);
        when(config.url()).thenReturn("https://beacon.test");
        when(config.contactName()).thenReturn("");
        when(config.contactMail()).thenReturn("");
        var self = mock(SelfDelivery.class);
        when(self.isSelf()).thenReturn(false);
        return new BeaconReportService(config, httpClient, self, new TaskScheduler(), redaction, problemLog);
    }

    /**
     * A report goes nowhere until the operator has said it may.
     *
     * <p>Asked here rather than at the caller. The switch existed, was stored, was drawn in the
     * settings screen and was read by nothing, so agreeing to it changed as little as refusing it.
     */
    @Test
    void aReportIsNotForwardedUntilTheOperatorAgrees() {
        assertFalse(
                serviceThatForwards(false).sendReport(aReport("/station/members"), "1.0.0"),
                "nothing is queued while forwarding is off");
    }

    /** With the switch on, a report is queued rather than sent on the thread that wrote it. */
    @Test
    void aReportIsQueuedOnceTheOperatorAgrees() {
        assertTrue(
                serviceThatForwards(true).sendReport(aReport("/station/members"), "1.0.0"), "the report is on its way");
    }

    /**
     * A button is not the switch.
     *
     * <p>The switch governs what leaves on its own; pressing send is an operator deciding about the
     * one report in front of them. Without this, a report written before the switch was turned on
     * could never be passed on at all, which is the state every report was in until now.
     */
    @Test
    void aReportSentByHandGoesWhateverTheSwitchSays() {
        assertTrue(
                serviceThatForwards(false).sendReportNow(aReport("/station/members"), "1.0.0"),
                "the operator asked for this one");
    }

    /**
     * The query string is dropped before anything leaves.
     *
     * <p>A query carries what somebody searched for and sometimes who they looked at, none of which
     * says which page went wrong. The screen already sends the path alone, so this is the guarantee
     * rather than the hope: what leaves the instance is decided here, not in the browser.
     */
    @Test
    void theQueryStringNeverLeavesWithTheReport() {
        var payload = serviceThatForwards(true).reportPayloadFor(aReport("/station/members?q=Nora%20F"), "1.0.0");
        assertEquals("/station/members", payload.page(), "the page is kept and the query is not");
    }

    /**
     * What the screen was, which is what makes a sentence about it worth reading.
     *
     * <p>"Der Knopf tut nichts" names no defect on its own. The browser it happened in, how large
     * the window was and what the reader was allowed to do are what tell a fault everybody meets
     * from one only a guardian on a phone meets, and none of it says who they were.
     */
    @Test
    void theScreenTravelsWithTheReportAndThePersonDoesNot() {
        var payload = serviceThatForwards(true).reportPayloadFor(aReport("/station/members"), "1.0.0");

        assertEquals("Firefox/141.0", payload.browser());
        assertEquals("1920x1080", payload.screenSize());
        assertEquals("LOGIN, USER", payload.roles());
        assertTrue(payload.recentRequests().contains("/api/v1/events"), "the calls before it went");

        String whole = payload.toString();
        assertFalse(whole.contains("Nora Fülling"), "who wrote it stays at the station: " + whole);
    }

    /** The calls are stripped of their queries the same way the page is, and for the same reason. */
    @Test
    void theQueriesOfTheCallsBeforeItAreDroppedToo() {
        var payload = serviceThatForwards(true).reportPayloadFor(aReport("/station/members"), "1.0.0");

        assertTrue(payload.recentRequests().contains("\"url\":\"/api/v1/members\""), payload.recentRequests());
        assertFalse(payload.recentRequests().contains("q=Nora"), payload.recentRequests());
    }

    /**
     * A warning without an exception is nothing but its words.
     *
     * <p>No class, no stacktrace, so no frames: a beacon was told a logger name, a level and a count.
     * One class logs several different failures, so they arrived as one row that named none of them.
     */
    @Test
    void whatAFaultWasLoggedWithTravelsWithIt() {
        var payload = serviceThatForwards(true).payloadFor(aWarning(), "1.0.0", false);

        assertTrue(payload.message().contains("HTTP 409"), payload.message());
        assertTrue(payload.message().contains("HTTP 500"), "the wordings are told apart: " + payload.message());
    }

    /**
     * A mail address is the one thing in a message that is a person and never a diagnosis.
     *
     * <p>Everything else is kept as it was written. An identifier, a path inside the product and a
     * status code are what make a fault findable again, and taking those out would leave the sentence
     * as useless as the logger name was.
     */
    @Test
    void aMailAddressIsTakenOutOfWhatTravels() {
        var payload = serviceThatForwards(true).payloadFor(aWarning(), "1.0.0", false);

        assertFalse(payload.message().contains("nora@example.com"), payload.message());
        assertTrue(payload.message().contains("/remote/registrations/dd396799"), "the path is kept");
    }

    /** A fault as the log holds one: no exception, and the same call failing two different ways. */
    private static StoredProblem aWarning() {
        var messages = List.of(
                "Signed GET list /remote/registrations/dd396799 failed: HTTP 409",
                "Signed GET list /remote/registrations/dd396799 failed: HTTP 500",
                "Mail an nora@example.com ging nicht");
        return new StoredProblem(
                1,
                "fp",
                ProblemLevel.WARN,
                "dev.chojo.ember.feature.federation.service.FederationHttpClient",
                messages.getFirst(),
                null,
                null,
                null,
                messages,
                2,
                Instant.now().minusSeconds(600),
                Instant.now(),
                ProblemState.OPEN,
                null,
                0);
    }

    /**
     * A report as a station holds one, with a name on it that must not travel and a call carrying a
     * query that must not travel either.
     */
    private static ProblemReport aReport(String page) {
        return new ProblemReport(
                1,
                7,
                3,
                "Nora Fülling",
                "Das ging schief",
                page,
                "LOGIN, USER",
                "[{\"url\":\"/api/v1/events\",\"status\":500},{\"url\":\"/api/v1/members?q=Nora\",\"status\":200}]",
                "Firefox/141.0",
                "1920x1080",
                null,
                false,
                null,
                null,
                Instant.now());
    }

    @Test
    void namesIdentifiersAndAddressesAreTakenOutOfTheWordsAndTheLines() {
        when(accounts.nameWords()).thenReturn(Set.of("nora", "fülling"));
        linesAround(1);

        var payload = serviceThatForwards(true).payloadFor(aWarning(), "1.0.0", true);

        var line = payload.context().getFirst().message();
        assertEquals("Mail for [name] [name] ([mail]) about [uuid]", line);
        assertFalse(payload.message().contains("nora@example.com"), payload.message());
    }

    @Test
    void noLinesTravelUnlessAskedFor() {
        linesAround(1);

        var payload = serviceThatForwards(true).payloadFor(aWarning(), "1.0.0", false);

        assertNull(payload.context());
        verify(problemLog, never()).context(anyLong(), any(), anyBoolean());
    }

    @Test
    void aForwardedFaultTakesItsLinesOnlyWhereTheOperatorSaidSoAndOnlyLater() {
        linesAround(1);
        var flusher = mock(ProblemLogFlusher.class);
        var scheduler = mock(TaskScheduler.class);
        when(httpClient.beaconPost(any(), any(), any()))
                .thenReturn(Optional.of(new DiscoveryHttpClient.Answer(202, "")));
        serviceOnHand(sending(true), scheduler).startForwarding(flusher, "1.0.0");
        var listener = ArgumentCaptor.<Consumer<StoredProblem>>captor();
        verify(flusher).onNewOrReopened(listener.capture());

        listener.getValue().accept(aWarning());
        verify(httpClient, never()).beaconPost(any(), any(), any());
        var later = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).later(anyString(), eq(BeaconReportService.CONTEXT_DELAY), later.capture());
        later.getValue().run();

        var sent = ArgumentCaptor.forClass(Object.class);
        verify(httpClient).beaconPost(any(), eq("/api/v1/beacon/problems"), sent.capture());
        assertEquals(
                1, ((BeaconPayloads.ProblemPayload) sent.getValue()).context().size());
    }

    @Test
    void aForwardedFaultGoesWithoutLinesWhileTheSettingIsOff() {
        var flusher = mock(ProblemLogFlusher.class);
        var scheduler = mock(TaskScheduler.class);
        when(httpClient.beaconPost(any(), any(), any()))
                .thenReturn(Optional.of(new DiscoveryHttpClient.Answer(202, "")));
        serviceOnHand(sending(false), scheduler).startForwarding(flusher, "1.0.0");
        var listener = ArgumentCaptor.<Consumer<StoredProblem>>captor();
        verify(flusher).onNewOrReopened(listener.capture());

        listener.getValue().accept(aWarning());

        var sent = ArgumentCaptor.forClass(Object.class);
        verify(httpClient).beaconPost(any(), eq("/api/v1/beacon/problems"), sent.capture());
        assertNull(((BeaconPayloads.ProblemPayload) sent.getValue()).context());
        verify(problemLog, never()).context(anyLong(), any(), anyBoolean());
        verify(scheduler, never()).later(anyString(), any(), any());
    }

    @Test
    void aBeaconThatDoesNotKnowLinesGetsTheFaultWithoutThem() {
        linesAround(1);
        var refused = "{\"code\":\"" + BodyRefusal.BODY_UNEXPECTED_FIELD.code() + "\"}";
        when(httpClient.beaconPost(any(), any(), any()))
                .thenReturn(Optional.of(new DiscoveryHttpClient.Answer(400, refused)))
                .thenReturn(Optional.of(new DiscoveryHttpClient.Answer(202, "")));

        serviceOnHand(sending(false), mock(TaskScheduler.class)).send(aWarning(), "1.0.0", true);

        var sent = ArgumentCaptor.forClass(Object.class);
        verify(httpClient, times(2)).beaconPost(any(), eq("/api/v1/beacon/problems"), sent.capture());
        var first = (BeaconPayloads.ProblemPayload) sent.getAllValues().get(0);
        var second = (BeaconPayloads.ProblemPayload) sent.getAllValues().get(1);
        assertEquals(1, first.context().size());
        assertNull(second.context());
        assertNotEquals(first.envelope().nonce(), second.envelope().nonce(), "the second goes in a fresh envelope");
    }

    private void linesAround(long problemId, List<ProblemContextLine> lines) {
        when(problemLog.context(problemId, null, false))
                .thenReturn(new ProblemContext(
                        ProblemContextState.LINES,
                        true,
                        List.of(new ProblemOccurrence(AT, "worker-1")),
                        0,
                        false,
                        lines));
    }

    private static ProblemContextLine at(Instant when, String level, String message) {
        return new ProblemContextLine(when, level, "dev.chojo.ember.A", "worker-1", message, false);
    }

    @Test
    void onlyLinesAtInfoAndAboveLeave() {
        linesAround(1, List.of(at(AT, "DEBUG", "body {...}"), at(AT, "TRACE", "headers"), at(AT, "INFO", "kept")));

        var payload = serviceThatForwards(true).payloadFor(aWarning(), "1.0.0", true);

        assertEquals(
                List.of("kept"),
                payload.context().stream()
                        .map(BeaconPayloads.LogLinePayload::message)
                        .toList());
    }

    @Test
    void theThreadIsRedactedLikeTheMessage() {
        when(accounts.nameWords()).thenReturn(Set.of("nora"));
        linesAround(1, List.of(new ProblemContextLine(AT, "INFO", "dev.chojo.ember.A", "import-nora", "line", false)));

        var payload = serviceThatForwards(true).payloadFor(aWarning(), "1.0.0", true);

        assertEquals("import-[name]", payload.context().getFirst().thread());
    }

    @Test
    void theLinesStayInsideWhatABeaconReadsKeepingTheClosest() {
        var lines = new ArrayList<ProblemContextLine>();
        for (int i = -50; i <= 10; i++)
            lines.add(at(AT.plusSeconds(i), "INFO", (i == 0 ? "fault " : "far ") + "x".repeat(3000)));
        linesAround(1, lines);

        var payload = serviceThatForwards(true).payloadFor(aWarning(), "1.0.0", true);

        int chars = payload.context().stream()
                .mapToInt(line -> line.message().length())
                .sum();
        assertTrue(chars <= BeaconReportService.CONTEXT_BUDGET, "held to the budget: " + chars);
        assertTrue(
                payload.context().stream().anyMatch(line -> line.message().startsWith("fault")), "the closest stays");
        assertTrue(payload.context().stream()
                .allMatch(line -> line.message().length() <= BeaconReportService.MAX_LINE_CHARS));
        assertTrue(Json.MAPPER.writeValueAsString(payload).length() < 64 * 1024, "the delivery fits a beacon");
    }

    @Test
    void noLinesKeptMeansNoLinesSent() {
        when(problemLog.context(1, null, false))
                .thenReturn(new ProblemContext(
                        ProblemContextState.LOGGING_OFF,
                        false,
                        List.of(new ProblemOccurrence(AT, "w")),
                        0,
                        false,
                        List.of()));

        assertNull(
                serviceThatForwards(true).payloadFor(aWarning(), "1.0.0", true).context());
    }

    @Test
    void aFaultWithoutLinesSerialisesWithoutTheField() {
        var payload = serviceThatForwards(true).payloadFor(aWarning(), "1.0.0", false);

        assertFalse(Json.MAPPER.writeValueAsString(payload).contains("\"context\""));
    }

    @Test
    void aFaultTooLargeForTheBeaconGoesWithoutItsLines() {
        linesAround(1);
        var refused = "{\"code\":\"" + BeaconRefusal.BEACON_INTAKE_TOO_LARGE.code() + "\"}";
        when(httpClient.beaconPost(any(), any(), any()))
                .thenReturn(Optional.of(new DiscoveryHttpClient.Answer(400, refused)))
                .thenReturn(Optional.of(new DiscoveryHttpClient.Answer(202, "")));

        serviceOnHand(sending(false), mock(TaskScheduler.class)).send(aWarning(), "1.0.0", true);

        var sent = ArgumentCaptor.forClass(Object.class);
        verify(httpClient, times(2)).beaconPost(any(), eq("/api/v1/beacon/problems"), sent.capture());
        assertNull(((BeaconPayloads.ProblemPayload) sent.getAllValues().get(1)).context());
    }

    @Test
    void aSwitchTurnedOffWhileTheFaultWaitsIsHonoured() {
        linesAround(1);
        var flusher = mock(ProblemLogFlusher.class);
        var scheduler = mock(TaskScheduler.class);
        var config = sending(true);
        when(httpClient.beaconPost(any(), any(), any()))
                .thenReturn(Optional.of(new DiscoveryHttpClient.Answer(202, "")));
        serviceOnHand(config, scheduler).startForwarding(flusher, "1.0.0");
        var listener = ArgumentCaptor.<Consumer<StoredProblem>>captor();
        verify(flusher).onNewOrReopened(listener.capture());
        listener.getValue().accept(aWarning());
        var later = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).later(anyString(), eq(BeaconReportService.CONTEXT_DELAY), later.capture());

        when(config.forwardLogContext()).thenReturn(false);
        later.getValue().run();

        var sent = ArgumentCaptor.forClass(Object.class);
        verify(httpClient).beaconPost(any(), eq("/api/v1/beacon/problems"), sent.capture());
        assertNull(((BeaconPayloads.ProblemPayload) sent.getValue()).context());
    }

    @Test
    void whatWasShownIsWhatGoes() {
        Instant shown = AT.minusSeconds(600);
        when(problemLog.context(1, shown, false))
                .thenReturn(new ProblemContext(
                        ProblemContextState.LINES,
                        true,
                        List.of(new ProblemOccurrence(AT, "worker-1"), new ProblemOccurrence(shown, "worker-1")),
                        1,
                        false,
                        List.of(at(shown, "INFO", "the shown line"))));
        when(httpClient.beaconPost(any(), any(), any()))
                .thenReturn(Optional.of(new DiscoveryHttpClient.Answer(202, "")));

        serviceOnHand(sending(false), mock(TaskScheduler.class)).sendAsShown(aWarning(), "1.0.0", shown);

        var sent = ArgumentCaptor.forClass(Object.class);
        verify(httpClient).beaconPost(any(), eq("/api/v1/beacon/problems"), sent.capture());
        assertEquals(
                "the shown line",
                ((BeaconPayloads.ProblemPayload) sent.getValue())
                        .context()
                        .getFirst()
                        .message());
    }

    @Test
    void thePreviewNamesTheOccurrenceItsLinesSurround() {
        linesAround(1);

        var preview = serviceThatForwards(true).previewFor(aWarning(), "1.0.0", true);

        assertEquals(AT, preview.contextAt());
        assertNull(
                serviceThatForwards(true).previewFor(aWarning(), "1.0.0", false).contextAt());
    }

    @Test
    void anOccurrenceNoLongerKeptSendsNoLinesRatherThanOthers() {
        Instant shown = AT.minusSeconds(600);
        when(problemLog.context(1, shown, false))
                .thenReturn(new ProblemContext(
                        ProblemContextState.LINES,
                        true,
                        List.of(new ProblemOccurrence(AT, "worker-1")),
                        0,
                        false,
                        List.of(at(AT, "INFO", "a newer line"))));
        when(httpClient.beaconPost(any(), any(), any()))
                .thenReturn(Optional.of(new DiscoveryHttpClient.Answer(202, "")));

        serviceOnHand(sending(false), mock(TaskScheduler.class)).sendAsShown(aWarning(), "1.0.0", shown);

        var sent = ArgumentCaptor.forClass(Object.class);
        verify(httpClient).beaconPost(any(), eq("/api/v1/beacon/problems"), sent.capture());
        assertNull(((BeaconPayloads.ProblemPayload) sent.getValue()).context());
    }
}
