/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.feature.legal.entity.BrowserStorageCatalog;
import dev.chojo.ember.feature.legal.entity.BrowserStorageEntry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserStorageServiceTest {

    private final BrowserStorageService service = new BrowserStorageService();

    @Test
    void catalogLoadsFromClasspath() {
        var catalog = service.catalog();
        assertNotNull(catalog, "browser_storage.json must be readable from the classpath");
        assertFalse(catalog.entries().isEmpty(), "the catalog must declare at least one entry");
    }

    @Test
    void everyEntryIsCompleteAndUnique() {
        var seen = new HashSet<String>();
        for (var entry : service.catalog().entries()) {
            assertTrue(seen.add(entry.key()), "duplicate key in catalog: " + entry.key());
            assertNotNull(entry.necessity(), "necessity missing for " + entry.key());
            assertNotNull(entry.retention(), "retention missing for " + entry.key());
            assertNotNull(entry.purpose(), "purpose missing for " + entry.key());
            assertFalse(entry.purpose().de().isBlank(), "German purpose missing for " + entry.key());
            assertFalse(entry.purpose().en().isBlank(), "English purpose missing for " + entry.key());
        }
    }

    @Test
    void everyNecessityAndRetentionIsWorded() {
        var text = service.catalog().text();
        for (var necessity : BrowserStorageEntry.Necessity.values()) {
            assertNotNull(text.necessity().get(necessity), "wording missing for necessity " + necessity);
        }
        for (var retention : BrowserStorageEntry.Retention.values()) {
            assertNotNull(text.retention().get(retention), "wording missing for retention " + retention);
        }
    }

    @Test
    void markdownListsEveryKeyPerLocale() {
        for (String locale : new String[] {"de", "en"}) {
            String markdown = service.toMarkdown(locale);
            assertTrue(markdown.startsWith("## "), "the section must open with a heading for locale " + locale);
            for (var entry : service.catalog().entries()) {
                assertTrue(markdown.contains("`" + entry.key() + "`"), "key " + entry.key() + " missing in " + locale);
            }
        }
    }

    @Test
    void markdownAvoidsTablesBecauseTheLegalRendererStripsThem() {
        assertFalse(service.toMarkdown("de").contains("|"), "the legal renderer drops table markup");
    }

    @Test
    void germanAndEnglishDiffer() {
        assertFalse(
                service.toMarkdown("de").equals(service.toMarkdown("en")),
                "the English rendering must not fall back to German wholesale");
    }

    @Test
    void unknownLocaleFallsBackToGerman() {
        assertEquals(service.toMarkdown("de"), service.toMarkdown("fr"));
    }

    @Test
    void anUnreadableCatalogRendersNothingRatherThanBreakingTheDocument() {
        assertEquals("", new BrowserStorageService((BrowserStorageCatalog) null).toMarkdown("de"));
        assertEquals("", new BrowserStorageService(new BrowserStorageCatalog(1, null, List.of())).toMarkdown("de"));
    }

    @Test
    void generatedSectionIsRecognisedRegardlessOfPrefix() {
        assertTrue(BrowserStorageService.isGeneratedSection("03-browser-storage.md"));
        assertTrue(BrowserStorageService.isGeneratedSection("_01-browser-storage.md"));
        assertFalse(BrowserStorageService.isGeneratedSection("02-rights.md"));
    }
}
