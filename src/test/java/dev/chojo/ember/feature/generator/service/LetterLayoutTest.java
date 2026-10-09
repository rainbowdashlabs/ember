/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.entity.ContentRows;
import dev.chojo.ember.feature.content.entity.GuardianCondition;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.MemberView;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a letter's rows are laid out for one member: columns at their share of the row, lines between
 * them where the row asks for them, blocks stacked in a column, blocks meant for other members left out
 * with their column kept, and rows left with nothing dropped. The texts keep the numbers they were
 * converted under, whoever reads the letter. Lines, gaps and signature lines are drawn with what
 * {@code letter.typ} needs of them, and a signature line holds a field for each person its signer asks
 * to sign for the member.
 */
class LetterLayoutTest {
    private static final RestrictionAudience TRIAL_ONLY = new RestrictionAudience(
            List.of(StationUserType.TRIAL), List.of(), List.of(), List.of(), RestrictionMode.AND);
    private static final RestrictionAudience MEMBERS_ONLY = new RestrictionAudience(
            List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.AND);
    private static final RestrictionMember MEMBER =
            new RestrictionMember(7, StationUserType.MEMBER, List.of(), List.of());
    private static final MemberView FOR_MEMBER = withGuardians(1);

    private static MemberView withGuardians(int guardians) {
        return MemberView.of(audience -> audience.includes(MEMBER), guardians);
    }

    private static ContentCell text(String text, double width, @Nullable RestrictionAudience audience) {
        return new ContentCell(0, 0, 0, width, CellContentType.MARKDOWN, text, CellConfig.EMPTY, audience, null);
    }

    private static ContentCell block(CellContentType type, String content, CellConfig config) {
        return new ContentCell(0, 0, 0, 100, type, content, config);
    }

    private static ContentCell signature(@Nullable SignatureRole signer, String below) {
        return block(CellContentType.SIGNATURE, below, new CellConfig.SignatureConfig(signer));
    }

    private static ContentCell signature(
            SignatureRole signer, @Nullable RestrictionAudience audience, @Nullable GuardianCondition condition) {
        return new ContentCell(
                0,
                0,
                0,
                100,
                CellContentType.SIGNATURE,
                "",
                new CellConfig.SignatureConfig(signer),
                audience,
                condition);
    }

    private static ContentCell onCondition(String text, GuardianCondition condition) {
        return new ContentCell(0, 0, 0, 100, CellContentType.MARKDOWN, text, CellConfig.EMPTY, null, condition);
    }

    private static ContentCell nested(double width, @Nullable RestrictionAudience audience, ContentRow... rows) {
        var config = new CellConfig.NestedRowsConfig(CellConfig.MAPPER.readTree(ContentRows.toJson(List.of(rows))));
        return new ContentCell(0, 0, 0, width, CellContentType.NESTED_ROWS, "", config, audience, null);
    }

    private static ContentRow row(ContentCell... cells) {
        return new ContentRow(0, 0, 0, List.of(cells));
    }

    private static ContentRow lined(ContentCell... cells) {
        return new ContentRow(0, 0, 0, List.of(cells), true);
    }

    private static LetterContent body(ContentRow... rows) {
        return new LetterContent(List.of(), List.of(), List.of(rows), LetterPage.defaults());
    }

    /**
     * Draws each text as its number, nothing for a picture, and a signature line as its number and its
     * fields, so the layout is all that is left.
     */
    private static Map<String, Object> layout(LetterContent letter, MemberView view) {
        return new LetterLayout(view, new LetterLayout.Blocks() {
                    @Override
                    public Map<String, Object> text(int index, ContentCell cell) {
                        return Map.of("kind", "text", "index", index);
                    }

                    @Override
                    public @Nullable Map<String, Object> image(ContentCell cell) {
                        return null;
                    }

                    @Override
                    public Map<String, Object> signature(int index, ContentCell cell, List<String> fields) {
                        return Map.of("kind", "signature", "index", index, "fields", fields);
                    }
                })
                .letter(letter);
    }

    private static List<Map<String, Object>> body(LetterContent letter, MemberView view) {
        return rows(layout(letter, view).get("body"));
    }

    private static Map<String, Object> onlyCell(LetterContent letter, MemberView view) {
        return cells(body(letter, view).getFirst()).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object rows) {
        return (List<Map<String, Object>>) rows;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cells(Map<String, Object> row) {
        return (List<Map<String, Object>>) row.get("cells");
    }

    @Test
    void columnsTakeTheirShareOfTheRow() {
        var body = body(body(row(text("links", 30, null), text("rechts", 70, null))), FOR_MEMBER);

        var cells = cells(body.getFirst());
        assertEquals(0.3, cells.getFirst().get("width"));
        assertEquals(0, cells.getFirst().get("index"));
        assertEquals(0.7, cells.get(1).get("width"));
        assertEquals(1, cells.get(1).get("index"));
        assertEquals(false, body.getFirst().get("lines"));
    }

    @Test
    void aRowAskingForLinesBetweenItsColumnsSaysSo() {
        var letter = body(lined(text("links", 50, null), text("rechts", 50, null)));
        var stacked = body(row(nested(100, null, lined(text("innen", 50, null), text("auch", 50, null)))));

        assertEquals(true, body(letter, FOR_MEMBER).getFirst().get("lines"));
        var inner = rows(onlyCell(stacked, FOR_MEMBER).get("rows"));
        assertEquals(true, inner.getFirst().get("lines"), "rows stacked in a column keep their lines");
    }

    @Test
    void aDividerIsALineWithItsLabel() {
        var labelled = body(row(block(CellContentType.DIVIDER, "", new CellConfig.DividerConfig(" Termine "))));
        var bare = body(row(block(CellContentType.DIVIDER, "", new CellConfig.DividerConfig(null))));

        assertEquals("divider", onlyCell(labelled, FOR_MEMBER).get("kind"));
        assertEquals("Termine", onlyCell(labelled, FOR_MEMBER).get("label"));
        assertEquals("", onlyCell(bare, FOR_MEMBER).get("label"));
        assertEquals(false, onlyCell(labelled, FOR_MEMBER).get("vertical"));
    }

    @Test
    void aVerticalDividerIsALineWithoutLabel() {
        var letter = body(row(block(CellContentType.DIVIDER, "", new CellConfig.DividerConfig("Termine", true))));

        assertEquals(true, onlyCell(letter, FOR_MEMBER).get("vertical"));
        assertEquals("", onlyCell(letter, FOR_MEMBER).get("label"), "a vertical line has no room for a label");
    }

    @Test
    void aSpacerIsAGapOfItsHeightInMillimetres() {
        var tall = body(row(block(CellContentType.SPACER, "", new CellConfig.SpacerConfig(96))));
        var plain = body(row(block(CellContentType.SPACER, "", new CellConfig.SpacerConfig(null))));

        assertEquals("spacer", onlyCell(tall, FOR_MEMBER).get("kind"));
        assertEquals(25.4, (double) onlyCell(tall, FOR_MEMBER).get("heightMm"), 1e-9);
        assertEquals(
                32 * LetterLayout.MM_PER_PIXEL,
                (double) onlyCell(plain, FOR_MEMBER).get("heightMm"),
                1e-9);
    }

    @Test
    void blocksStackedInAColumnAreLaidOutAsRowsOfTheirOwn() {
        var letter = body(row(nested(50, null, row(text("oben", 100, null)), row(text("unten", 100, null)))));

        var stacked = onlyCell(letter, FOR_MEMBER);

        assertEquals("rows", stacked.get("kind"));
        assertEquals(0.5, stacked.get("width"));
        var inner = rows(stacked.get("rows"));
        assertEquals(2, inner.size());
        assertEquals(1, cells(inner.get(1)).getFirst().get("index"));
    }

    /** The column of a block left out stays, empty, so the others keep their place. */
    @Test
    void aBlockForOthersIsLeftOutAndItsColumnStays() {
        var letter = body(row(text("Probe", 50, TRIAL_ONLY), text("Alle", 50, null)));

        var cells = cells(body(letter, FOR_MEMBER).getFirst());

        assertEquals("empty", cells.getFirst().get("kind"));
        assertEquals(0.5, cells.getFirst().get("width"));
        assertEquals(1, cells.get(1).get("index"), "the text left out still counts");
    }

    @Test
    void aRowLeftWithNothingIsDropped() {
        var letter = body(
                row(text("Probe", 100, TRIAL_ONLY)),
                row(nested(100, TRIAL_ONLY, row(text("auch Probe", 100, null)))),
                row(text("   ", 100, null)),
                row(text("Alle", 100, null)));

        var body = body(letter, FOR_MEMBER);

        assertEquals(1, body.size());
        assertEquals(3, cells(body.getFirst()).getFirst().get("index"), "every text before it was counted");
    }

    @Test
    void everybodySeesEveryBlockAndTheTextsAMemberSeesAreTheirs() {
        var letter = new LetterContent(
                List.of(row(text("{{station.name}}", 100, null))),
                List.of(),
                List.of(
                        row(text("{{member.fullName}}", 50, null), text("{{profile.3}}", 50, TRIAL_ONLY)),
                        row(onCondition("{{guardian2.fullName}}", GuardianCondition.SECOND_GUARDIAN)),
                        row(signature(SignatureRole.ISSUER, "{{generatedBy.fullName}}"))),
                LetterPage.defaults());

        assertEquals(3, body(letter, MemberView.EVERYBODY).size());
        assertEquals(
                List.of("{{station.name}}", "{{member.fullName}}", "{{generatedBy.fullName}}"),
                LetterLayout.visibleTexts(letter, FOR_MEMBER));
        assertEquals(
                List.of(
                        "{{station.name}}",
                        "{{member.fullName}}",
                        "{{profile.3}}",
                        "{{guardian2.fullName}}",
                        "{{generatedBy.fullName}}"),
                LetterLayout.visibleTexts(letter, MemberView.EVERYBODY));
        assertTrue(rows(layout(body(), FOR_MEMBER).get("header")).isEmpty());
    }

    @Test
    void aBlockOnTheSecondGuardianFollowsHowManyGuardiansTheMemberHas() {
        var letter = body(
                row(onCondition("beide", GuardianCondition.SECOND_GUARDIAN)),
                row(onCondition("eine", GuardianCondition.NO_SECOND_GUARDIAN)));

        assertEquals(0, onlyCell(letter, withGuardians(2)).get("index"));
        assertEquals(1, onlyCell(letter, withGuardians(1)).get("index"));
        assertEquals(1, onlyCell(letter, withGuardians(0)).get("index"));
        assertEquals(2, body(letter, MemberView.EVERYBODY).size(), "a look without a member shows both");
    }

    @Test
    void aSignatureLineHoldsTheFieldsOfItsSigner() {
        assertEquals(List.of("issuer"), fieldsOf(SignatureRole.ISSUER, 1));
        assertEquals(List.of("participant"), fieldsOf(SignatureRole.PARTICIPANT, 1));
        assertEquals(List.of("guardian1"), fieldsOf(SignatureRole.GUARDIAN_1, 0));
        assertEquals(List.of("guardian2"), fieldsOf(SignatureRole.GUARDIAN_2, 2));
        assertEquals(List.of("anyGuardian"), fieldsOf(SignatureRole.ANY_GUARDIAN, 2));
        assertEquals(List.of("guardian1", "guardian2"), fieldsOf(SignatureRole.EACH_GUARDIAN, 2));
        assertEquals(List.of("guardian1"), fieldsOf(SignatureRole.EACH_GUARDIAN, 1));
        assertEquals(List.of("guardian1"), fieldsOf(SignatureRole.EACH_GUARDIAN, 0), "a line stays to sign by hand");
    }

    private static Object fieldsOf(SignatureRole signer, int guardians) {
        return onlyCell(body(row(signature(signer, "Unterschrift"))), withGuardians(guardians))
                .get("fields");
    }

    /**
     * Each field of a line confirms what the line says, trimmed; a line without a statement and a line the
     * member does not get leave their fields out, so they keep the default.
     */
    @Test
    void eachFieldOfALineConfirmsWhatTheLineSays() {
        var letter = body(
                row(block(
                        CellContentType.SIGNATURE,
                        "",
                        new CellConfig.SignatureConfig(SignatureRole.EACH_GUARDIAN, " Wir sind einverstanden. "))),
                row(block(
                        CellContentType.SIGNATURE, "", new CellConfig.SignatureConfig(SignatureRole.PARTICIPANT, " "))),
                row(new ContentCell(
                        0,
                        0,
                        0,
                        100,
                        CellContentType.SIGNATURE,
                        "",
                        new CellConfig.SignatureConfig(SignatureRole.ISSUER, "Nur zur Probe."),
                        TRIAL_ONLY,
                        null)));

        assertEquals(
                Map.of("guardian1", "Wir sind einverstanden.", "guardian2", "Wir sind einverstanden."),
                LetterLayout.signatureStatements(letter, withGuardians(2)));
        assertEquals(
                Map.of(
                        "issuer",
                        "Nur zur Probe.",
                        "guardian1",
                        "Wir sind einverstanden.",
                        "guardian2",
                        "Wir sind einverstanden."),
                LetterLayout.signatureStatements(letter, MemberView.EVERYBODY));
    }

    @Test
    void aSecondGuardiansLineIsLeftOutForAMemberWithOne() {
        var letter = body(row(signature(SignatureRole.GUARDIAN_2, "Erziehungsberechtigte 2")));

        assertTrue(body(letter, withGuardians(1)).isEmpty());
        assertTrue(body(letter, withGuardians(0)).isEmpty());
        assertTrue(LetterLayout.visibleTexts(letter, withGuardians(1)).isEmpty(), "its text needs no value");
    }

    @Test
    void aSignatureLineWithoutSignerPrintsNothingButKeepsItsNumber() {
        var letter = body(row(signature(null, "unbekannt")), row(text("danach", 100, null)));

        var body = body(letter, FOR_MEMBER);

        assertEquals(1, body.size());
        assertEquals(1, cells(body.getFirst()).getFirst().get("index"));
    }

    @Test
    void alternativeLinesForOneSignerPassWhereTheyNeverMeet() {
        var letter = body(
                row(signature(SignatureRole.ISSUER, TRIAL_ONLY, null)),
                row(signature(SignatureRole.ISSUER, MEMBERS_ONLY, null)));

        assertDoesNotThrow(() -> requireOnce(letter, FOR_MEMBER));
        for (int guardians = 0; guardians <= 2; guardians++) {
            int count = guardians;
            assertDoesNotThrow(() -> requireOnce(letter, MemberView.anyMember(count)));
        }
    }

    @Test
    void twoLinesForOneSignerAreRefusedForTheMemberWhoGetsBoth() {
        var letter = body(
                row(signature(SignatureRole.ISSUER, null, null)),
                row(signature(SignatureRole.ISSUER, MEMBERS_ONLY, null)));

        assertRefused(DocumentRefusal.DOCUMENT_SIGNER_TWICE_FOR_MEMBER, () -> requireOnce(letter, FOR_MEMBER));
        assertDoesNotThrow(() -> requireOnce(letter, MemberView.anyMember(1)), "a trial member gets one");
    }

    @Test
    void eachGuardianMeetsTheFirstGuardianAndTheSecondOnlyWhereThereIsOne() {
        var withFirst =
                body(row(signature(SignatureRole.EACH_GUARDIAN, "")), row(signature(SignatureRole.GUARDIAN_1, "")));
        var withSecond =
                body(row(signature(SignatureRole.EACH_GUARDIAN, "")), row(signature(SignatureRole.GUARDIAN_2, "")));

        assertRefused(
                DocumentRefusal.DOCUMENT_SIGNER_TWICE_FOR_MEMBER,
                () -> requireOnce(withFirst, MemberView.anyMember(0)));
        assertDoesNotThrow(() -> requireOnce(withSecond, MemberView.anyMember(1)));
        assertRefused(
                DocumentRefusal.DOCUMENT_SIGNER_TWICE_FOR_MEMBER,
                () -> requireOnce(withSecond, MemberView.anyMember(2)));
    }

    @Test
    void linesOnOppositeGuardianConditionsNeverMeet() {
        var letter = body(
                row(signature(SignatureRole.ANY_GUARDIAN, null, GuardianCondition.SECOND_GUARDIAN)),
                row(signature(SignatureRole.ANY_GUARDIAN, null, GuardianCondition.NO_SECOND_GUARDIAN)));

        for (int guardians = 0; guardians <= 3; guardians++) {
            int count = guardians;
            assertDoesNotThrow(() -> requireOnce(letter, MemberView.anyMember(count)));
        }
    }

    private static void requireOnce(LetterContent letter, MemberView view) {
        LetterLayout.requireSignersOnce(letter, view, DocumentRefusal.DOCUMENT_SIGNER_TWICE_FOR_MEMBER);
    }

    private static void assertRefused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }

    @Test
    void aMemberMatchesAnAudienceTheWayAStoredOneMatches() {
        var trial = new RestrictionMember(8, StationUserType.TRIAL, List.of(), List.of());
        var byMember = new RestrictionAudience(List.of(), List.of(), List.of(), List.of(7), RestrictionMode.AND);

        assertTrue(TRIAL_ONLY.includes(trial));
        assertFalse(TRIAL_ONLY.includes(MEMBER));
        assertTrue(byMember.includes(MEMBER));
        assertTrue(RestrictionAudience.empty().includes(MEMBER));
    }
}
