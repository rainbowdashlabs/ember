/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A text laid out to fit its box: shrinking on one line, breaking into lines when it wraps, and never
 * below the smallest size.
 */
class FittedTextTest {
    private PDDocument document;
    private FontChain chain;

    @BeforeEach
    void setup() throws IOException {
        document = new PDDocument();
        chain = FontChainTest.liberationSansAlone(document);
    }

    @AfterEach
    void close() throws IOException {
        document.close();
    }

    @Test
    void aShortTextKeepsItsSize() {
        var fitted = FittedText.fit(chain, "Lena", 200, 20, 12, false);

        assertEquals(12, fitted.size());
        assertEquals(1, fitted.lines().size());
    }

    @Test
    void aLongTextOnOneLineShrinksUntilItFitsAcross() {
        var fitted = FittedText.fit(chain, "Lena Schmidt aus der Dönhoffstraße", 100, 20, 12, false);

        assertTrue(fitted.size() < 12);
        assertEquals(1, fitted.lines().size());
        assertTrue(fitted.lines().getFirst().width(fitted.size()) <= 100 - 2 * FittedText.PADDING);
    }

    @Test
    void aWrappingTextBreaksAtSpacesAndAtItsOwnLineBreaks() {
        var fitted = FittedText.fit(chain, "Dönhoffstraße 31\n10318 Berlin", 200, 60, 10, true);

        assertEquals(10, fitted.size());
        assertEquals(2, fitted.lines().size());
    }

    @Test
    void aWordWiderThanTheBoxIsBrokenInside() {
        var fitted = FittedText.fit(chain, "Donaudampfschifffahrtsgesellschaft", 40, 200, 10, true);

        assertTrue(fitted.lines().size() > 1);
        assertTrue(fitted.lines().stream().allMatch(line -> line.width(fitted.size()) <= 40));
    }

    @Test
    void nothingShrinksBelowTheSmallestSize() {
        var fitted = FittedText.fit(chain, "Ein sehr langer Text ".repeat(20), 30, 5, 12, false);

        assertEquals(FittedText.MIN_SIZE, fitted.size());
    }
}
