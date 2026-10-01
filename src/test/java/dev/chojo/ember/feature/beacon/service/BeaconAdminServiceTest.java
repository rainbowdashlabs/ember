/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.beacon.entity.BeaconReport;
import dev.chojo.ember.feature.beacon.repository.BeaconReadRepository;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.BeaconSettingsRequest;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.SendReportRequest;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.service.ProblemLogAppender;
import dev.chojo.ember.feature.system.service.ProblemReportScreenshotService;
import dev.chojo.ember.feature.system.service.ProblemReportService;
import dev.chojo.ember.feature.system.service.UpdateCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BeaconAdminServiceTest {
    private static final MediaContent PICTURE = new MediaContent(new byte[] {1}, "image/png");

    private BeaconSettings config;
    private BeaconReportService reports;
    private BeaconMetricsService metrics;
    private BeaconReadRepository collected;
    private ProblemReportService problemReports;
    private ProblemReportScreenshotService pictures;
    private ProblemLogAppender problemLog;
    private BeaconAdminService service;

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    private static BeaconReport collectedReport(int id, Integer pictureId) {
        return new BeaconReport(
                id, "m", "/p", "1", "b", "s", "r", "q", null, null, Instant.EPOCH, false, pictureId, "i1");
    }

    private static ProblemReport report(int id, Integer pictureId) {
        return new ProblemReport(
                id, 3, 11, "Mara", "It broke", "/x", "[]", "[]", "ua", "1x1", pictureId, false, null, null, null);
    }

    private static ProblemLogAppender.ProblemEntry problem(long id) {
        var entry = mock(ProblemLogAppender.ProblemEntry.class);
        when(entry.id()).thenReturn(id);
        when(entry.snapshot()).thenReturn(mock(ProblemLogAppender.Snapshot.class));
        return entry;
    }

    @BeforeEach
    void setup() {
        config = mock(BeaconSettings.class);
        reports = mock(BeaconReportService.class);
        metrics = mock(BeaconMetricsService.class);
        collected = mock(BeaconReadRepository.class);
        problemReports = mock(ProblemReportService.class);
        pictures = mock(ProblemReportScreenshotService.class);
        problemLog = mock(ProblemLogAppender.class);
        var updates = mock(UpdateCheckService.class);
        when(updates.currentVersion()).thenReturn("1.2.3");
        service = new BeaconAdminService(
                config, reports, metrics, updates, collected, problemReports, pictures, () -> problemLog);
    }

    @Test
    void whatABeaconGatheredIsOnlyThereWhereThisInstanceIsOne() {
        assertEquals(Refusal.BEACON_NOT_RECEIVING, refusalOf(() -> service.faults(false)));
        assertEquals(Refusal.BEACON_NOT_RECEIVING, refusalOf(() -> service.collectedReports(false)));
        assertEquals(Refusal.BEACON_NOT_RECEIVING, refusalOf(() -> service.collectedMetrics(30)));
        assertEquals(Refusal.BEACON_NOT_RECEIVING, refusalOf(() -> service.acknowledgeReport(1)));
        assertEquals(Refusal.BEACON_NOT_RECEIVING, refusalOf(() -> service.resolveFault(1, true, null)));
        assertEquals(Refusal.BEACON_NOT_RECEIVING, refusalOf(() -> service.collectedPicture(1)));
    }

    @Test
    void aBeaconListsResolvesAndAcknowledgesWhatItGathered() {
        when(config.receiving()).thenReturn(true);
        when(collected.resolveFault(1, true, "1.2.4")).thenReturn(true);
        when(collected.acknowledgeReport(2)).thenReturn(true);

        service.faults(true);
        service.collectedReports(false);
        service.collectedMetrics(900);
        service.resolveFault(1, true, "1.2.4");
        service.acknowledgeReport(2);

        verify(collected).faults(true);
        verify(collected).reports(false);
        verify(collected).metrics(365);
        assertEquals(Refusal.BEACON_FAULT_NOT_HERE, refusalOf(() -> service.resolveFault(9, true, null)));
        assertEquals(Refusal.BEACON_REPORT_NOT_ACKNOWLEDGED, refusalOf(() -> service.acknowledgeReport(9)));
    }

    @Test
    void aGatheredPictureIsServedFromItsReport() {
        when(config.receiving()).thenReturn(true);
        when(collected.reports(true))
                .thenReturn(List.of(collectedReport(1, null), collectedReport(2, 7), collectedReport(3, 8)));
        when(pictures.read(7)).thenReturn(Optional.of(PICTURE));
        when(pictures.read(8)).thenReturn(Optional.empty());

        assertEquals(PICTURE, service.collectedPicture(2));
        assertEquals(Refusal.BEACON_REPORT_HAS_NO_PICTURE, refusalOf(() -> service.collectedPicture(1)));
        assertEquals(Refusal.BEACON_REPORT_PICTURE_NOT_HERE, refusalOf(() -> service.collectedPicture(3)));
        assertEquals(Refusal.BEACON_REPORT_NOT_HERE_FOR_PICTURE, refusalOf(() -> service.collectedPicture(4)));
    }

    @Test
    void theSettingsAreWrittenAndReadBack() {
        when(config.enabled()).thenReturn(true);
        when(config.url()).thenReturn("https://beacon.test");

        var status = service.update(new BeaconSettingsRequest(
                true, "https://beacon.test", true, false, true, false, false, "Mara", "m@test"));

        verify(config).update(true, "https://beacon.test", true, false, true, false, false, "Mara", "m@test");
        assertEquals("https://beacon.test", status.url());
    }

    @Test
    void problemsAreSentOnlyFromARunningLogOfABeaconThatIsSetUp() {
        assertEquals(Refusal.BEACON_NOT_SET_UP, refusalOf(() -> service.sendProblem(1)));
        when(config.enabled()).thenReturn(true);
        var failing = new BeaconAdminService(
                config,
                reports,
                metrics,
                mock(UpdateCheckService.class),
                collected,
                problemReports,
                pictures,
                () -> null);
        assertEquals(Refusal.PROBLEM_LOG_NOT_RUNNING, refusalOf(() -> failing.sendProblem(1)));
        assertEquals(Refusal.PROBLEM_LOG_NOT_RUNNING_ON_SEND, refusalOf(() -> failing.sendProblems(List.of(1L))));
        assertEquals(Refusal.BEACON_NOTHING_CHOSEN_TO_SEND, refusalOf(() -> service.sendProblems(List.of())));
        assertEquals(Refusal.BEACON_NOTHING_CHOSEN_TO_SEND, refusalOf(() -> service.sendProblems(null)));
    }

    @Test
    void chosenProblemsArePreviewedAndSent() {
        when(config.enabled()).thenReturn(true);
        var first = problem(1);
        var second = problem(2);
        when(problemLog.getProblems(true)).thenReturn(List.of(first, second));
        when(reports.send(any(), eq("1.2.3"))).thenReturn(true);
        when(reports.sendAll(any(), eq("1.2.3"))).thenReturn(1);

        service.previewProblem(1);
        assertEquals(1, service.sendProblem(2).queued());
        assertEquals(1, service.sendProblems(List.of(2L)).queued());
        assertEquals(Refusal.BEACON_PROBLEM_NOT_HERE, refusalOf(() -> service.previewProblem(9)));

        verify(reports).payloadFor(first.snapshot(), "1.2.3");
        verify(reports).sendAll(List.of(second.snapshot()), "1.2.3");
    }

    @Test
    void aReportGoesWithTheReportersPictureACoveredCopyOrNone() {
        when(config.enabled()).thenReturn(true);
        var report = report(5, 7);
        when(problemReports.find(5)).thenReturn(Optional.of(report));
        when(problemReports.forward(any(), any(), anyBoolean())).thenReturn(true);
        when(pictures.store("covered", null)).thenReturn(Optional.of(9));
        when(pictures.store("unreadable", null)).thenReturn(Optional.empty());

        assertEquals(1, service.sendReport(5, SendReportRequest.AS_IT_STANDS).queued());
        service.sendReport(5, new SendReportRequest("covered", false));
        service.sendReport(5, new SendReportRequest("unreadable", false));
        service.sendReport(5, new SendReportRequest("covered", true));

        verify(problemReports, times(2)).forward(report, 7, false);
        verify(problemReports).forward(report, 9, true);
        verify(problemReports).forward(report, null, false);
    }

    @Test
    void aReportThatIsNotHereIsNeitherPreviewedNorSent() {
        when(config.enabled()).thenReturn(true);
        when(problemReports.find(anyInt())).thenReturn(Optional.empty());

        assertEquals(Refusal.BEACON_REPORT_NOT_HERE, refusalOf(() -> service.previewReport(5)));
        assertEquals(
                Refusal.BEACON_REPORT_NOT_HERE, refusalOf(() -> service.sendReport(5, SendReportRequest.AS_IT_STANDS)));
        verify(problemReports, never()).forward(any(), any(), anyBoolean());
    }

    @Test
    void theReportAndTheFiguresArePreviewedAsTheyWouldGo() {
        var report = report(5, null);
        when(problemReports.find(5)).thenReturn(Optional.of(report));

        service.previewReport(5);
        service.previewMetrics();

        verify(reports).reportPayloadFor(report, "1.2.3");
        verify(metrics).batch(eq("1.2.3"), any());
    }
}
