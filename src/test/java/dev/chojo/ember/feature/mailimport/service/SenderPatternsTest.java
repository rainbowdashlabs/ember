/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SenderPatternsTest {

    /**
     * The case the whole feature rests on. An empty filter reads naturally as "everything" and here
     * that would be an open door, so it has to accept nobody.
     */
    @Test
    void aRuleTrustingNobodyTakesNothing() {
        assertFalse(SenderPatterns.accepts(List.of(), "archive@feuerwehr-musterstadt.de"));
        assertFalse(SenderPatterns.accepts(null, "archive@feuerwehr-musterstadt.de"));
        assertFalse(SenderPatterns.accepts(Arrays.asList("", "   ", null), "archive@feuerwehr-musterstadt.de"));
    }

    @Test
    void oneAddressMatchesThatAddressAndNoOther() {
        var patterns = List.of("archive@feuerwehr-musterstadt.de");

        assertTrue(SenderPatterns.accepts(patterns, "archive@feuerwehr-musterstadt.de"));
        assertFalse(SenderPatterns.accepts(patterns, "post@feuerwehr-musterstadt.de"));
        assertFalse(SenderPatterns.accepts(patterns, "archive@example.com"));
    }

    @Test
    void aDomainMatchesEveryAddressAtIt() {
        var patterns = List.of("*@feuerwehr-musterstadt.de");

        assertTrue(SenderPatterns.accepts(patterns, "archive@feuerwehr-musterstadt.de"));
        assertTrue(SenderPatterns.accepts(patterns, "irgendwer@feuerwehr-musterstadt.de"));
        assertFalse(SenderPatterns.accepts(patterns, "archive@example.com"));
    }

    /** Not implied, so mail from a subdomain needs a pattern of its own. */
    @Test
    void aDomainDoesNotReachItsSubdomains() {
        assertFalse(SenderPatterns.accepts(List.of("*@musterstadt.de"), "archive@feuerwehr.musterstadt.de"));
        assertTrue(SenderPatterns.accepts(List.of("*@feuerwehr.musterstadt.de"), "archive@feuerwehr.musterstadt.de"));
    }

    @Test
    void caseIsNotWhatTellsTwoSendersApart() {
        assertTrue(SenderPatterns.accepts(List.of("Archive@Feuerwehr-Musterstadt.DE"), "archive@feuerwehr-musterstadt.de"));
        assertTrue(SenderPatterns.accepts(List.of("*@feuerwehr-musterstadt.de"), "ARCHIVE@FEUERWEHR-MUSTERSTADT.DE"));
    }

    @Test
    void anyOnePatternHittingIsEnough() {
        var patterns = List.of("post@example.com", "*@feuerwehr-musterstadt.de", "arzt@praxis.de");

        assertTrue(SenderPatterns.accepts(patterns, "irgendwer@feuerwehr-musterstadt.de"));
        assertTrue(SenderPatterns.accepts(patterns, "arzt@praxis.de"));
        assertFalse(SenderPatterns.accepts(patterns, "fremd@woanders.de"));
    }

    @Test
    void aSenderThatIsNotThereMatchesNothing() {
        var patterns = List.of("*@feuerwehr-musterstadt.de");

        assertFalse(SenderPatterns.accepts(patterns, null));
        assertFalse(SenderPatterns.accepts(patterns, ""));
        assertFalse(SenderPatterns.accepts(patterns, "   "));
    }

    @Test
    void theTwoFormsAreValidAndTheRestIsNot() {
        assertTrue(SenderPatterns.isValid("archive@feuerwehr-musterstadt.de"));
        assertTrue(SenderPatterns.isValid("*@feuerwehr-musterstadt.de"));
        assertTrue(SenderPatterns.isValid("  Archive@Musterstadt.de  "));

        assertFalse(SenderPatterns.isValid(null));
        assertFalse(SenderPatterns.isValid(""));
        assertFalse(SenderPatterns.isValid("feuerwehr-musterstadt.de"));
        assertFalse(SenderPatterns.isValid("archive@"));
        assertFalse(SenderPatterns.isValid("@musterstadt.de"));
        assertFalse(SenderPatterns.isValid("archive@localhost"));
        assertFalse(SenderPatterns.isValid("two addresses@musterstadt.de"));
        assertFalse(SenderPatterns.isValid("a@b@musterstadt.de"));
    }

    /**
     * The refusal that matters most: a pattern wide enough to accept anybody cannot be written, in any
     * of the spellings somebody reaching for a regular expression would try.
     */
    @Test
    void nothingThatWouldTrustEverybodyIsAcceptedAsAPattern() {
        assertFalse(SenderPatterns.isValid("*"));
        assertFalse(SenderPatterns.isValid("*@*"));
        assertFalse(SenderPatterns.isValid("*@*.de"));
        assertFalse(SenderPatterns.isValid(".*"));
        assertFalse(SenderPatterns.isValid("*@"));
        assertFalse(SenderPatterns.isValid("archive@*.de"));
    }
}
