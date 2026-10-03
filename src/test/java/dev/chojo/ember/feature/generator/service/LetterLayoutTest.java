/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.entity.ContentRows;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a letter's rows are laid out for one member: columns at their share of the row, blocks stacked in a
 * column, blocks meant for other members left out with their column kept, and rows left with nothing
 * dropped. The texts keep the numbers they were converted under, whoever reads the letter.
 */
class LetterLayoutTest {
    private static final RestrictionAudience TRIAL_ONLY = new RestrictionAudience(
            List.of(StationUserType.TRIAL), List.of(), List.of(), List.of(), RestrictionMode.AND);
    private static final RestrictionMember MEMBER =
            new RestrictionMember(7, StationUserType.MEMBER, List.of(), List.of());
    private static final Predicate<RestrictionAudience> FOR_MEMBER = audience -> audience.includes(MEMBER);

    private static ContentCell text(String text, double width, @Nullable RestrictionAudience audience) {
        return new ContentCell(0, 0, 0, width, CellContentType.MARKDOWN, text, CellConfig.EMPTY, audience);
    }

    private static ContentCell nested(double width, @Nullable RestrictionAudience audience, ContentRow... rows) {
        var config = new CellConfig.NestedRowsConfig(CellConfig.MAPPER.readTree(ContentRows.toJson(List.of(rows))));
        return new ContentCell(0, 0, 0, width, CellContentType.NESTED_ROWS, "", config, audience);
    }

    private static ContentRow row(ContentCell... cells) {
        return new ContentRow(0, 0, 0, List.of(cells));
    }

    private static LetterContent body(ContentRow... rows) {
        return new LetterContent(List.of(), List.of(), List.of(rows), LetterPage.defaults());
    }

    /** Draws each text as its number, and nothing for a picture, so the layout is all that is left. */
    private static Map<String, Object> layout(LetterContent letter, Predicate<RestrictionAudience> shown) {
        return new LetterLayout(shown, new LetterLayout.Blocks() {
                    @Override
                    public Map<String, Object> text(int index, ContentCell cell) {
                        return Map.of("kind", "text", "index", index);
                    }

                    @Override
                    public @Nullable Map<String, Object> image(ContentCell cell) {
                        return null;
                    }
                })
                .letter(letter);
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
        var body = rows(layout(body(row(text("links", 30, null), text("rechts", 70, null))), FOR_MEMBER)
                .get("body"));

        var cells = cells(body.getFirst());
        assertEquals(0.3, cells.getFirst().get("width"));
        assertEquals(0, cells.getFirst().get("index"));
        assertEquals(0.7, cells.get(1).get("width"));
        assertEquals(1, cells.get(1).get("index"));
    }

    @Test
    void blocksStackedInAColumnAreLaidOutAsRowsOfTheirOwn() {
        var letter = body(row(nested(50, null, row(text("oben", 100, null)), row(text("unten", 100, null)))));

        var stacked =
                cells(rows(layout(letter, FOR_MEMBER).get("body")).getFirst()).getFirst();

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

        var cells = cells(rows(layout(letter, FOR_MEMBER).get("body")).getFirst());

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

        var body = rows(layout(letter, FOR_MEMBER).get("body"));

        assertEquals(1, body.size());
        assertEquals(3, cells(body.getFirst()).getFirst().get("index"), "every text before it was counted");
    }

    @Test
    void everybodySeesEveryBlockAndTheTextsAMemberSeesAreTheirs() {
        var letter = new LetterContent(
                List.of(row(text("{{station.name}}", 100, null))),
                List.of(),
                List.of(row(text("{{member.fullName}}", 50, null), text("{{profile.3}}", 50, TRIAL_ONLY))),
                LetterPage.defaults());

        assertEquals(1, rows(layout(letter, LetterLayout.EVERYBODY).get("body")).size());
        assertEquals(List.of("{{station.name}}", "{{member.fullName}}"), LetterLayout.visibleTexts(letter, FOR_MEMBER));
        assertEquals(
                List.of("{{station.name}}", "{{member.fullName}}", "{{profile.3}}"),
                LetterLayout.visibleTexts(letter, LetterLayout.EVERYBODY));
        assertTrue(rows(layout(body(), FOR_MEMBER).get("header")).isEmpty());
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
