/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.feature.beacon.service.BeaconReportService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import dev.chojo.ember.feature.system.service.ProblemReportService.ReportRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemReportServiceTest {
    private static final MediaContent PICTURE = new MediaContent(new byte[] {1, 2}, "image/png");

    private ProblemReportRepository reports;
    private BeaconReportService beacon;
    private ProblemReportScreenshotService screenshots;
    private ProblemReportService service;

    static ProblemReport report(int id, Integer pictureId) {
        return new ProblemReport(
                id, 3, 11, "Mara", "It broke", "/x", "[]", "[]", "ua", "1x1", pictureId, false, null, null, null);
    }

    private static ReportRequest request(String message, String screenshot) {
        return new ReportRequest(message, "/x", "[]", "[]", "ua", "1x1", screenshot);
    }

    @BeforeEach
    void setup() {
        reports = mock(ProblemReportRepository.class);
        beacon = mock(BeaconReportService.class);
        screenshots = mock(ProblemReportScreenshotService.class);
        var updates = mock(UpdateCheckService.class);
        when(updates.currentVersion()).thenReturn("1.2.3");
        service = new ProblemReportService(reports, beacon, updates, screenshots);
    }

    @Test
    void aReportWithoutAMessageIsRefusedBeforeAnythingIsKept() {
        var refused = assertThrows(RefusalResponse.class, () -> service.submit(3, 11, "Mara", request(" ", "abc")));

        assertEquals(SystemRefusal.PROBLEM_REPORT_NEEDS_A_MESSAGE, refused.refusal());
        verify(screenshots, never()).store(any(), any());
        assertThrows(RefusalResponse.class, () -> service.submit(3, 11, "Mara", request(null, null)));
    }

    @Test
    void aReportThatGoesByItselfIsForwardedWithItsPicture() {
        when(screenshots.store("abc", 11)).thenReturn(Optional.of(7));
        when(reports.create(anyInt(), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(report(5, 7));
        when(beacon.goesByItself(any())).thenReturn(true);
        when(screenshots.read(7)).thenReturn(Optional.of(PICTURE));
        when(beacon.sendReportNow(any(), any(), any(), any())).thenReturn(true);

        var stored = service.submit(3, 11, "Mara", request("It broke", "abc"));

        assertEquals(5, stored.id());
        verify(reports).create(3, 11, "Mara", "It broke", "/x", "[]", "[]", "ua", "1x1", 7);
        verify(beacon).sendReportNow(stored, "1.2.3", PICTURE.data(), "image/png");
        verify(reports).markForwarded(5);
        verify(screenshots, never()).forget(any());
    }

    @Test
    void aReportThatWaitsForReviewStaysHere() {
        when(screenshots.store(null, null)).thenReturn(Optional.empty());
        when(reports.create(anyInt(), any(), anyString(), anyString(), any(), any(), any(), any(), any(), isNull()))
                .thenReturn(report(5, null));

        service.submit(3, null, "Mara", request("It broke", null));

        verify(beacon, never()).sendReportNow(any(), any(), any(), any());
    }

    @Test
    void aFullQueueLeavesTheReportUnforwardedAndACoveredCopyIsLetGo() {
        when(screenshots.read(9)).thenReturn(Optional.empty());
        when(beacon.sendReportNow(any(), any(), isNull(), isNull())).thenReturn(false);

        assertFalse(service.forward(report(5, 7), 9, true));

        verify(reports, never()).markForwarded(anyInt());
        verify(screenshots).forget(9);
    }

    @Test
    void aReportCanGoWithoutAnyPicture() {
        when(beacon.sendReportNow(any(), any(), isNull(), isNull())).thenReturn(true);

        assertTrue(service.forward(report(5, 7), null, false));

        verify(screenshots, never()).read(anyInt());
        verify(reports).markForwarded(5);
    }

    @Test
    void theListAndTheAcknowledgementsGoToTheStore() {
        when(reports.findAll(true)).thenReturn(List.of(report(1, null)));
        when(reports.acknowledgeAll()).thenReturn(4);

        assertEquals(1, service.list(true).size());
        service.acknowledge(1);
        assertEquals(4, service.acknowledgeAll());

        verify(reports).acknowledge(1);
    }

    @Test
    void thePictureOfAReportIsServedOnlyWhereThereIsOne() {
        when(reports.findById(1)).thenReturn(Optional.empty());
        when(reports.findById(2)).thenReturn(Optional.of(report(2, null)));
        when(reports.findById(3)).thenReturn(Optional.of(report(3, 8)));
        when(reports.findById(4)).thenReturn(Optional.of(report(4, 9)));
        when(screenshots.read(8)).thenReturn(Optional.empty());
        when(screenshots.read(9)).thenReturn(Optional.of(PICTURE));

        assertEquals(
                SystemRefusal.PROBLEM_REPORT_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.picture(1)).refusal());
        assertEquals(
                SystemRefusal.PROBLEM_REPORT_HAS_NO_PICTURE,
                assertThrows(RefusalResponse.class, () -> service.picture(2)).refusal());
        assertEquals(
                SystemRefusal.PROBLEM_REPORT_PICTURE_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.picture(3)).refusal());
        assertEquals(PICTURE, service.picture(4));
        assertTrue(service.find(4).isPresent());
    }

    @Test
    void deletingAReportLetsItsPictureGo() {
        when(reports.findById(4)).thenReturn(Optional.of(report(4, 9)));
        when(reports.findById(5)).thenReturn(Optional.empty());

        service.delete(4);
        service.delete(5);

        verify(reports).delete(4);
        verify(reports).delete(5);
        verify(screenshots).forget(9);
    }
}
