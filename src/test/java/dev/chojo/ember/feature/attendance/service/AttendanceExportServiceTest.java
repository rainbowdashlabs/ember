/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sheet that is printed to be signed, and the address at the foot of the page.
 *
 * <p>The PDF itself is compiled by a program outside the JVM, so what is checked here is that it is
 * produced at all for each shape the dialog can ask for: a document that fails to render answers
 * empty, which is what these assertions would catch.
 */
class AttendanceExportServiceTest extends RepositoryTestBase {

    private static AttendanceExportService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int sessionId;

    @BeforeAll
    static void setup() {
        service = new AttendanceExportService(
                attendanceRepo, accountRepo, stationMemberRepo, memberGroupRepo, stationRepo, new Api());
        station = stationRepo.create("Signing Station");
        account = accountRepo.create("signing-sheet@test.com", "Anna", "Schmidt");
        member = stationMemberRepo.create(station.id(), account.id());
        int templateId =
                attendanceRepo.createTemplate(station.id(), "Signing Template").id();
        var session = attendanceRepo.createSession(
                templateId,
                Instant.parse("2026-03-06T17:00:00Z"),
                Instant.parse("2026-03-06T19:00:00Z"),
                null,
                "Dienstabend",
                null);
        sessionId = session.id();
        attendanceRepo.createEntry(
                sessionId, member.id(), AttendanceEntry.AttendanceStatus.PRESENT, AttendanceEntry.EntrySource.EXPECTED);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static byte[] export(AttendanceExportService.SheetOptions options) {
        var pdf = service.exportSessionPdf(sessionId, "Max Mustermann", options);
        assertTrue(pdf.isPresent(), "the sheet was rendered");
        return pdf.get().bytes();
    }

    @Test
    void theSheetIsStillTheOneItAlwaysWasWhenNothingIsAskedFor() {
        assertTrue(export(AttendanceExportService.SheetOptions.PLAIN).length > 0);
    }

    @Test
    void aSheetToSignRenders() {
        var options = new AttendanceExportService.SheetOptions(true, null, 0, null);

        assertTrue(export(options).length > 0);
    }

    @Test
    void aSheetCanBeHeadedByHandAndCarryRoomForPeopleNobodyExpected() {
        var options = new AttendanceExportService.SheetOptions(true, "", 6, null);

        assertTrue(export(options).length > 0);
    }

    @Test
    void aSheetCanBeGivenATitleOfItsOwn() {
        var options = new AttendanceExportService.SheetOptions(false, "Jahreshauptversammlung", 0, null);

        assertTrue(export(options).length > 0);
    }

    /** More blank lines than fit on a page are cut back rather than refused. */
    @Test
    void theRoomForUnexpectedPeopleIsBounded() {
        var asked = new AttendanceExportService.SheetOptions(true, null, 500, null);

        assertEquals(AttendanceExportService.SheetOptions.MAX_BLANK_ROWS, asked.blankRows());
        assertEquals(0, new AttendanceExportService.SheetOptions(true, null, -3, null).blankRows());
    }

    /**
     * A station that hides the address still prints it where the export says otherwise, and the other
     * way round: the dialog decides the one document, the setting decides the rest.
     */
    @Test
    void whatTheStationSettledOnIsWhatIsPrintedUnlessTheExportSaysOtherwise() {
        stationRepo.updatePdfHidesInstanceUrl(station.id(), true);

        assertTrue(export(AttendanceExportService.SheetOptions.PLAIN).length > 0);
        assertTrue(export(new AttendanceExportService.SheetOptions(false, null, 0, true)).length > 0);

        var hidden = stationRepo.findById(station.id()).orElseThrow();
        assertTrue(hidden.pdfHidesInstanceUrl(), "the export did not write the station's own answer");

        stationRepo.updatePdfHidesInstanceUrl(station.id(), false);
        assertFalse(stationRepo.findById(station.id()).orElseThrow().pdfHidesInstanceUrl());
    }
}
