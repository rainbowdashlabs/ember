/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Own date formats as the editor checks them while they are typed. */
class DateFormatTest {
    private static DateFormatCheck check(String pattern) {
        return DateFormat.check(pattern, DocumentLanguage.DE, true);
    }

    @Test
    void everyTokenPrintsTheExampleDayInTheLanguageOfTheTemplate() {
        assertEquals(
                List.of(
                        "T=3",
                        "TT=03",
                        "TTT=Sa.",
                        "TTTT=Samstag",
                        "M=10",
                        "MM=10",
                        "MMM=Okt.",
                        "MMMM=Oktober",
                        "JJ=26",
                        "JJJJ=2026",
                        "h=18",
                        "hh=18",
                        "mm=30"),
                Arrays.stream(DateToken.values())
                        .map(token -> token.written() + "=" + token.example(DocumentLanguage.DE))
                        .toList());
        assertEquals(
                "Sat, 3. Oct 2026",
                DateFormat.check("TTT, T. MMM JJJJ", DocumentLanguage.EN, false).example());
    }

    @Test
    void separatorsStandAsTheyAre() {
        assertEquals(DateFormatCheck.printed("03.10.26 18:30 / 3-10"), check("TT.MM.JJ hh:mm / T-M"));
    }

    @Test
    void aTimeOfDayIsRefusedForADateWithoutOne() {
        assertEquals(
                DateFormatCheck.refused(DateFormatProblem.CLOCK, null),
                DateFormat.check("hh:mm", DocumentLanguage.DE, false));
        assertEquals("18:30", check("hh:mm").example());
    }

    @Test
    void whatCannotBePrintedIsRefusedWithItsReason() {
        assertEquals(DateFormatCheck.refused(DateFormatProblem.EMPTY, null), check("  "));
        assertEquals(DateFormatCheck.refused(DateFormatProblem.TOO_LONG, "40"), check("T.".repeat(21)));
        assertEquals(DateFormatCheck.refused(DateFormatProblem.UNKNOWN, "U"), check("TT.MM.JJJJ Uhr"));
        assertEquals(DateFormatCheck.refused(DateFormatProblem.UNKNOWN, "TTTTT"), check("TTTTT"));
        assertEquals(DateFormatCheck.refused(DateFormatProblem.UNKNOWN, "JJJ"), check("JJJ"));
        assertEquals(DateFormatCheck.refused(DateFormatProblem.NO_TOKEN, null), check(" .-"));
        assertNull(check("T.".repeat(20)).problem());
    }
}
