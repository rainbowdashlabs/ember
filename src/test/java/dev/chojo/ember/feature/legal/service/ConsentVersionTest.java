/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.feature.legal.entity.BrowserStorageCatalog;
import dev.chojo.ember.feature.legal.entity.BrowserStorageEntry;
import dev.chojo.ember.feature.legal.entity.BrowserStorageEntry.Necessity;
import dev.chojo.ember.feature.legal.entity.BrowserStorageEntry.Retention;
import dev.chojo.ember.feature.legal.entity.LocalizedText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The consent version covers the consent text and the storage categories, never the list of stored
 * keys, while every other version keeps hashing the document as it reads.
 */
class ConsentVersionTest {
    @TempDir
    Path dir;

    private Path consentDir;

    private static LocalizedText text(String value) {
        return new LocalizedText(value, value);
    }

    private static BrowserStorageEntry entry(String key, Necessity necessity) {
        return new BrowserStorageEntry(key, null, necessity, Retention.UNTIL_CLEARED, text("purpose of " + key));
    }

    private static BrowserStorageCatalog catalog(String comfortDescription, List<BrowserStorageEntry> entries) {
        var groups = Map.of(
                Necessity.REQUIRED, new BrowserStorageCatalog.Text.Group(text("Required"), text("Needed to sign in.")),
                Necessity.FUNCTIONAL,
                        new BrowserStorageCatalog.Text.Group(text("Functional"), text("Written by one feature.")),
                Necessity.COMFORT, new BrowserStorageCatalog.Text.Group(text("Comfort"), text(comfortDescription)));
        var retention = Map.of(Retention.UNTIL_CLEARED, text("until cleared"));
        var kind = Map.of(BrowserStorageEntry.Kind.LOCAL_STORAGE, text("local storage"));
        return new BrowserStorageCatalog(
                1,
                new BrowserStorageCatalog.Text(
                        text("Storage"), text("Intro"), text("Closing"), groups, retention, kind),
                entries);
    }

    private static List<BrowserStorageEntry> baseEntries() {
        return new ArrayList<>(
                List.of(entry("storage_consent", Necessity.REQUIRED), entry("theme", Necessity.COMFORT)));
    }

    private LegalDocumentService documents(BrowserStorageCatalog catalog) {
        return new LegalDocumentService(
                dir.resolve("placeholders.json").toString(), new BrowserStorageService(catalog));
    }

    @BeforeEach
    void consentText() throws IOException {
        consentDir = dir.resolve("consent");
        Files.createDirectories(consentDir.resolve("de"));
        Files.writeString(consentDir.resolve("de").resolve("01-consent.md"), "# Consent\nPlease consent.");
        Files.writeString(consentDir.resolve("de").resolve("02-browser-storage.md"), "");
    }

    @Test
    void aKeyAddedToAKnownCategoryLeavesTheConsentVersionAsItWas() {
        var before = documents(catalog("Remembers the view.", baseEntries()));
        var entries = baseEntries();
        entries.add(entry("sidebar_collapsed", Necessity.COMFORT));
        var after = documents(catalog("Remembers the view.", entries));

        assertEquals(before.versionByStorageCategories(consentDir), after.versionByStorageCategories(consentDir));
        assertNotEquals(
                before.getDocument(consentDir).version(),
                after.getDocument(consentDir).version(),
                "the document itself still lists every key and hashes with it");
    }

    @Test
    void aNewCategoryMovesTheConsentVersion() {
        var before = documents(catalog("Remembers the view.", baseEntries()));
        var entries = baseEntries();
        entries.add(entry("page_drafts", Necessity.FUNCTIONAL));
        var after = documents(catalog("Remembers the view.", entries));

        assertNotEquals(before.versionByStorageCategories(consentDir), after.versionByStorageCategories(consentDir));
    }

    @Test
    void aRewordedCategoryMovesTheConsentVersion() {
        var before = documents(catalog("Remembers the view.", baseEntries()));
        var after = documents(catalog("Remembers the view and the colours.", baseEntries()));

        assertNotEquals(before.versionByStorageCategories(consentDir), after.versionByStorageCategories(consentDir));
    }

    @Test
    void anEditedConsentTextMovesTheConsentVersion() throws IOException {
        var documents = documents(catalog("Remembers the view.", baseEntries()));
        String before = documents.versionByStorageCategories(consentDir);

        Files.writeString(consentDir.resolve("de").resolve("01-consent.md"), "# Consent\nPlease consent again.");

        assertNotEquals(before, documents.versionByStorageCategories(consentDir));
    }

    @Test
    void aDocumentWithoutAStorageSectionHasOneVersionEitherWay() throws IOException {
        Files.delete(consentDir.resolve("de").resolve("02-browser-storage.md"));
        var documents = documents(catalog("Remembers the view.", baseEntries()));

        assertEquals(documents.getDocument(consentDir).version(), documents.versionByStorageCategories(consentDir));
    }
}
