/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullTextSearchTest {

    @ParameterizedTest
    @ValueSource(
            strings = {"simple", "german", "english", "french", "spanish", "italian", "dutch", "portuguese", "russian"})
    void allowsSupportedConfigs(String config) {
        assertEquals(config, FullTextSearch.config(config));
    }

    @ParameterizedTest
    @ValueSource(strings = {"klingon", "GERMAN", "", " ", "simple'; DROP TABLE kb_file; --", "german simple"})
    void rejectsUnknownConfigs(String config) {
        assertEquals(FullTextSearch.DEFAULT_CONFIG, FullTextSearch.config(config));
    }

    @Test
    void rejectsNullConfig() {
        assertEquals(FullTextSearch.DEFAULT_CONFIG, FullTextSearch.config(null));
    }

    @Test
    void vectorSplicesOnlyAllowedConfig() {
        assertEquals("to_tsvector('german', :text)", FullTextSearch.vector("german", "text"));
        assertEquals("to_tsvector('simple', :text)", FullTextSearch.vector("dothraki", "text"));
    }

    @Test
    void prefixQuerySplicesOnlyAllowedConfig() {
        assertEquals("to_tsquery('english', :tsquery)", FullTextSearch.prefixQuery("english", "tsquery"));
        assertEquals("to_tsquery('simple', :tsquery)", FullTextSearch.prefixQuery("english; --", "tsquery"));
    }

    @Test
    void headlineSplicesOnlyAllowedConfig() {
        String fragment =
                FullTextSearch.headline("russian", "fc.text_content", "to_tsquery('russian', :tsquery)", "MaxWords=30");
        assertEquals(
                "ts_headline('russian', fc.text_content, to_tsquery('russian', :tsquery), 'MaxWords=30')", fragment);
        assertTrue(FullTextSearch.headline("bogus", "col", "q", "MaxWords=5").startsWith("ts_headline('simple',"));
    }

    @Test
    void generatedFragmentsReferenceUserTextByBindName() {
        assertTrue(FullTextSearch.vector("german", "text").contains(":text"));
        assertTrue(FullTextSearch.prefixQuery("german", "tsquery").contains(":tsquery"));
    }

    @Test
    void prefixTermsAppendsPrefixMarkerPerWord() {
        assertEquals("Notr:*", FullTextSearch.prefixTerms("Notr"));
        assertEquals("Erste:* & Hilfe:*", FullTextSearch.prefixTerms("Erste Hilfe"));
    }

    @Test
    void prefixTermsCollapsesWhitespace() {
        assertEquals("a:* & b:*", FullTextSearch.prefixTerms("   a \t\n  b  "));
    }

    @Test
    void prefixTermsStripsPunctuationAndKeepsLetters() {
        assertEquals("Loschzug:*", FullTextSearch.prefixTerms("Lösch-zug!".replace("ö", "o")));
        assertEquals("Löschzug:*", FullTextSearch.prefixTerms("Lösch-zug!"));
        assertEquals("abc123_:*", FullTextSearch.prefixTerms("abc'123_\""));
    }

    @Test
    void prefixTermsOnBlankQuery() {
        assertEquals("", FullTextSearch.prefixTerms("   "));
    }

    /**
     * A bare {@code :*} is not valid tsquery syntax and makes {@code to_tsquery} reject the whole
     * query, so a word that strips to nothing has to be dropped rather than marked.
     */
    @Test
    void prefixTermsDropsWordsThatStripToNothing() {
        assertEquals("", FullTextSearch.prefixTerms("???"));
        assertEquals("", FullTextSearch.prefixTerms("-- !! ??"));
        assertEquals("Notruf:*", FullTextSearch.prefixTerms("!!! Notruf ???"));
        assertEquals("Erste:* & Hilfe:*", FullTextSearch.prefixTerms("Erste - Hilfe"));
    }

    @Test
    void stripMarkupWrapsExpression() {
        assertEquals(
                "regexp_replace(regexp_replace(fc.text_content, '<[^>]+>', ' ', 'g'), '[#*_~`>\\[\\]()!|]', '', 'g')",
                FullTextSearch.stripMarkup("fc.text_content"));
    }
}
