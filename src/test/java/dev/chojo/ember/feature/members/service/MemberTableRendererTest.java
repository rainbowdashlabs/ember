/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTableCellType;
import dev.chojo.ember.feature.members.entity.MemberTableColumnKind;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.CsvWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a drawn table looks like once it is a file.
 *
 * <p>Two things are decided here and nowhere else. A cell is made safe for a spreadsheet, which is
 * not the same as quoting it. And the tokens the table carries become words, because a file has no
 * screen behind it to do that for it.
 */
class MemberTableRendererTest {
    private MemberTableRenderer renderer;

    private static MemberTable.MemberTableHeader builtin(String key, String label) {
        return new MemberTable.MemberTableHeader(
                label, MemberTableColumnKind.BUILTIN, key, null, MemberTableCellType.TEXT);
    }

    private static MemberTable.MemberTableHeader question(String label) {
        return new MemberTable.MemberTableHeader(
                label, MemberTableColumnKind.REGISTRATION_FIELD, null, 7, MemberTableCellType.TEXT);
    }

    private static Station stationSpeaking(String locale) {
        return new Station(
                1,
                null,
                "Wache",
                "Europe/Berlin",
                locale,
                null,
                null,
                false,
                null,
                ThemeFeel.ROUNDED,
                false,
                PublicKbMode.OFF,
                null,
                DiscoveryVisibility.NONE,
                null,
                false,
                false,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                StationKind.REGULAR,
                null,
                false,
                false);
    }

    @BeforeEach
    void setup() {
        var stations = mock(StationRepository.class);
        when(stations.findLogo(anyInt())).thenReturn(Optional.empty());
        renderer = new MemberTableRenderer(stations, new Api());
    }

    /** The header is the labels, and a row is its values, in the order the table put them. */
    @Test
    void theFileHoldsTheTableInOrder() {
        var table = new MemberTable(
                List.of(builtin("name", "Name"), question("Schuhgröße")),
                List.of(new MemberTable.MemberTableRow(1, List.of("Anna Beispiel", "39"))));

        var csv = renderer.toCsv(table, stationSpeaking("de-DE"), CsvWriter.Separator.SEMICOLON);

        assertEquals("Name;Schuhgröße\nAnna Beispiel;39\n", csv);
    }

    /**
     * A cell carrying the separator is quoted, and one starting like a formula is pushed behind a
     * quote first.
     *
     * <p>A spreadsheet reads a leading equals as something to calculate, and a name is not a
     * calculation however it begins.
     */
    @Test
    void aCellIsMadeSafeForASpreadsheet() {
        var table = new MemberTable(
                List.of(builtin("name", "Name"), question("Notiz")),
                List.of(new MemberTable.MemberTableRow(1, List.of("Beispiel; Anna", "=1+1"))));

        var csv = renderer.toCsv(table, stationSpeaking("de-DE"), CsvWriter.Separator.SEMICOLON);

        assertTrue(csv.contains("\"Beispiel; Anna\""), "a cell holding the separator is quoted");
        assertTrue(csv.contains("'=1+1"), "and one that reads as a formula is not left as one");
    }

    /** What the table carries as a token, the file says in words. */
    @Test
    void tokensBecomeWords() {
        var table = new MemberTable(
                List.of(builtin("registrationStatus", "Anmeldung"), builtin("memberType", "Benutzertyp")),
                List.of(new MemberTable.MemberTableRow(1, List.of("ACCEPTED", "TRIAL"))));

        var csv = renderer.toCsv(table, stationSpeaking("de-DE"), CsvWriter.Separator.SEMICOLON);

        assertTrue(csv.contains("Bestätigt"), "the answer reads as a word");
        assertTrue(csv.contains("Probe"), "and so does the kind of member");
        assertFalse(csv.contains("ACCEPTED"), "the database's own spelling does not reach the file");
    }

    /** A station reading English gets English words, from the same tokens. */
    @Test
    void aStationReadingEnglishGetsEnglishWords() {
        var table = new MemberTable(
                List.of(builtin("registrationStatus", "Registration")),
                List.of(new MemberTable.MemberTableRow(1, List.of("WITHDRAWN"))));

        var csv = renderer.toCsv(table, stationSpeaking("en-GB"), CsvWriter.Separator.SEMICOLON);

        assertTrue(csv.contains("Withdrawn"));
    }

    /** Every answer somebody can give has a word, because a sheet is read by people. */
    @Test
    void everyAnswerHasAWord() {
        var table = new MemberTable(
                List.of(builtin("registrationStatus", "Anmeldung")),
                List.of(
                        new MemberTable.MemberTableRow(1, List.of("ACCEPTED")),
                        new MemberTable.MemberTableRow(2, List.of("PENDING")),
                        new MemberTable.MemberTableRow(3, List.of("DENIED")),
                        new MemberTable.MemberTableRow(4, List.of("DECLINED")),
                        new MemberTable.MemberTableRow(5, List.of("WITHDRAWN")),
                        new MemberTable.MemberTableRow(6, List.of("SOMETHING_ELSE"))));

        var german = renderer.toCsv(table, stationSpeaking("de-DE"), CsvWriter.Separator.SEMICOLON);
        assertTrue(german.contains("Bestätigt"));
        assertTrue(german.contains("Ausstehend"));
        assertTrue(german.contains("Abgelehnt"));
        assertTrue(german.contains("Abgemeldet"));
        assertTrue(german.contains("Zurückgezogen"));
        assertTrue(german.contains("SOMETHING_ELSE"), "and one nobody has a word for is left as it came");

        var english = renderer.toCsv(table, stationSpeaking("en-GB"), CsvWriter.Separator.SEMICOLON);
        assertTrue(english.contains("Confirmed"));
        assertTrue(english.contains("Pending"));
        assertTrue(english.contains("Turned down"));
        assertTrue(english.contains("Declined"));
        assertTrue(english.contains("Withdrawn"));
    }

    /** And so does every kind of member a station keeps. */
    @Test
    void everyKindOfMemberHasAWord() {
        var table = new MemberTable(
                List.of(builtin("memberType", "Benutzertyp")),
                List.of(
                        new MemberTable.MemberTableRow(1, List.of("TRIAL")),
                        new MemberTable.MemberTableRow(2, List.of("MEMBER")),
                        new MemberTable.MemberTableRow(3, List.of("GUARDIAN")),
                        new MemberTable.MemberTableRow(4, List.of("TEAM")),
                        new MemberTable.MemberTableRow(5, List.of("MANAGER")),
                        new MemberTable.MemberTableRow(6, List.of("SOMETHING_ELSE"))));

        var german = renderer.toCsv(table, stationSpeaking("de-DE"), CsvWriter.Separator.SEMICOLON);
        assertTrue(german.contains("Probe"));
        assertTrue(german.contains("Mitglied"));
        assertTrue(german.contains("Erziehungsberechtigter"));
        assertTrue(german.contains("Team"));
        assertTrue(german.contains("Manager"));
        assertTrue(german.contains("SOMETHING_ELSE"));

        var english = renderer.toCsv(table, stationSpeaking("en-GB"), CsvWriter.Separator.SEMICOLON);
        assertTrue(english.contains("Trial"));
        assertTrue(english.contains("Member"));
        assertTrue(english.contains("Guardian"));
    }

    /** A cell nobody filled in is left empty rather than given a word of its own. */
    @Test
    void anEmptyCellStaysEmpty() {
        var table = new MemberTable(
                List.of(builtin("registrationStatus", "Anmeldung"), builtin("memberType", "Benutzertyp")),
                List.of(new MemberTable.MemberTableRow(1, java.util.Arrays.asList("", "  "))));

        assertEquals(
                "Anmeldung;Benutzertyp\n;\"  \"\n",
                renderer.toCsv(table, stationSpeaking("de-DE"), CsvWriter.Separator.SEMICOLON));
    }

    /** An answer somebody gave is their own words and is never reworded, whatever it says. */
    @Test
    void anAnswerIsLeftAsItWasGiven() {
        var table = new MemberTable(
                List.of(question("Kommentar")), List.of(new MemberTable.MemberTableRow(1, List.of("ACCEPTED"))));

        var csv = renderer.toCsv(table, stationSpeaking("de-DE"), CsvWriter.Separator.SEMICOLON);

        assertTrue(csv.contains("ACCEPTED"), "a question's answer is not a token to be translated");
    }

    /** A table nobody is on is still a table, and the file carries its header. */
    @Test
    void anEmptyTableStillHasItsHeader() {
        var table = new MemberTable(List.of(builtin("name", "Name")), List.of());

        assertEquals("Name\n", renderer.toCsv(table, stationSpeaking("de-DE"), CsvWriter.Separator.SEMICOLON));
    }

    /**
     * The sheet somebody carries is a real document, rendered rather than described.
     *
     * <p>Worth rendering in a test because the template and the data have to agree about every key it
     * reads: a sheet that failed to build would fail at the moment somebody asked for it, which is
     * always the moment they need it.
     */
    @Test
    void theSheetIsADocument() throws Exception {
        var table = new MemberTable(
                List.of(builtin("name", "Name"), builtin("registrationStatus", "Anmeldung")),
                List.of(
                        new MemberTable.MemberTableRow(1, List.of("Anna Beispiel", "ACCEPTED")),
                        new MemberTable.MemberTableRow(2, List.of("Bert Beispiel", "PENDING"))));

        var pdf = renderer.toPdf(table, stationSpeaking("de-DE"), "Wer kommt", "17.04.2027", "Die Leitung");

        assertTrue(pdf.length > 0, "something was rendered");
        assertEquals('%', (char) pdf[0], "and it is a document rather than an apology");
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    /**
     * A station that has a badge gets it on the sheet.
     *
     * <p>Worth its own rendering because the logo is the one thing the template reads from beside the
     * data: it is written into the folder the document is built in, and a sheet that named a file
     * nobody put there would fail to build at all.
     */
    @Test
    void aStationsBadgeReachesTheSheet() throws Exception {
        var stations = mock(StationRepository.class);
        when(stations.findLogo(anyInt()))
                .thenReturn(Optional.of(new StationRepository.StationLogo(onePixelPng(), "image/png")));
        var withBadge = new MemberTableRenderer(stations, new Api());

        var table = new MemberTable(
                List.of(builtin("name", "Name")), List.of(new MemberTable.MemberTableRow(1, List.of("Anna Beispiel"))));

        var pdf = withBadge.toPdf(table, stationSpeaking("de-DE"), "Mit Wappen", "", "Die Leitung");

        assertTrue(pdf.length > 0);
        assertEquals('%', (char) pdf[0]);
    }

    /** The smallest picture that is still a picture, so the renderer has something real to place. */
    private static byte[] onePixelPng() {
        return java.util.Base64.getDecoder()
                .decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }

    /** A sheet of nobody still renders, because an empty list is an answer somebody asked for. */
    @Test
    void aSheetOfNobodyStillRenders() throws Exception {
        var table = new MemberTable(List.of(builtin("name", "Name")), List.of());

        var pdf = renderer.toPdf(table, stationSpeaking("en-GB"), "Nobody", "", "The desk");

        assertTrue(pdf.length > 0);
    }

    /** The labels a screen shows beside each column are the ones the file uses. */
    @Test
    void theHeaderLabelsAreTheColumnLabels() {
        var table = new MemberTable(List.of(builtin("name", "Name"), question("Schuhgröße")), List.of());

        assertEquals(List.of("Name", "Schuhgröße"), renderer.headerLabels(table));
    }
}
