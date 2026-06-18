/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.insights.service.BotClassifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotClassifierTest {

    private final BotClassifier classifier = new BotClassifier();

    @Test
    void knownCrawlersAreFlagged() {
        assertTrue(classifier.isBot("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"));
        assertTrue(classifier.isBot("Bingbot/2.0"));
        assertTrue(classifier.isBot("LinkedInBot/1.0"));
        assertTrue(classifier.isBot("facebookexternalhit/1.1"));
    }

    @Test
    void genericKeywordsAreFlagged() {
        assertTrue(classifier.isBot("MyCustomBot/1.0"));
        assertTrue(classifier.isBot("Foo crawler"));
        assertTrue(classifier.isBot("custom-spider"));
        assertTrue(classifier.isBot("curl/8.0"));
        assertTrue(classifier.isBot("python-requests/2.31"));
        assertTrue(classifier.isBot("HeadlessChrome/120"));
    }

    @Test
    void realBrowsersArePassed() {
        assertFalse(classifier.isBot(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"));
        assertFalse(classifier.isBot(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_2) AppleWebKit/605.1.15 Version/17.2 Safari/605.1.15"));
        assertFalse(
                classifier.isBot(
                        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile Safari/604.1"));
    }

    @Test
    void emptyOrNullUserAgentCountsAsBot() {
        assertTrue(classifier.isBot(null));
        assertTrue(classifier.isBot(""));
        assertTrue(classifier.isBot("   "));
    }
}
