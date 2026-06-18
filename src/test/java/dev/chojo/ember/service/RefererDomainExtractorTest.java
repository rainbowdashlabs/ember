/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.insights.service.RefererDomainExtractor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class RefererDomainExtractorTest {

    private static RefererDomainExtractor extractor(String baseUrl) {
        Api api = Mockito.mock(Api.class);
        Mockito.when(api.baseUrl()).thenReturn(baseUrl);
        return new RefererDomainExtractor(api);
    }

    @Test
    void missingRefererBecomesDirect() {
        var e = extractor("https://ember.example");
        assertEquals("direct", e.extract(null));
        assertEquals("direct", e.extract(""));
        assertEquals("direct", e.extract("   "));
    }

    @Test
    void externalDomainIsLowercasedAndWwwStripped() {
        var e = extractor("https://ember.example");
        assertEquals("google.com", e.extract("https://WWW.GOOGLE.com/search?q=hi"));
        assertEquals("news.example", e.extract("http://news.example/article"));
    }

    @Test
    void internalRefererIsDetected() {
        var e = extractor("https://ember.example");
        assertEquals("internal", e.extract("https://ember.example/dashboard"));
        assertEquals("internal", e.extract("https://www.ember.example/"));
        assertEquals("internal", e.extract("https://app.ember.example/page"));
    }

    @Test
    void malformedRefererBecomesDirect() {
        var e = extractor("https://ember.example");
        assertEquals("direct", e.extract("not a url"));
        assertEquals("direct", e.extract(":::"));
    }

    @Test
    void nullOrBlankBaseUrlStillExtracts() {
        var e = extractor(null);
        assertEquals("google.com", e.extract("https://google.com/"));
        assertEquals("direct", e.extract(null));
        var blank = extractor("");
        assertEquals("google.com", blank.extract("https://google.com/"));
    }

    @Test
    void baseUrlWithoutHostFallsBackToNoInternalDetection() {
        var e = extractor("/relative/path");
        assertEquals("google.com", e.extract("https://google.com/"));
    }

    @Test
    void malformedBaseUrlFallsBackToNoInternalDetection() {
        var e = extractor("http://[invalid");
        assertEquals("google.com", e.extract("https://google.com/"));
    }
}
