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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version of every legal document covers its written text and the storage categories, never the
 * list of stored keys, wherever the document carries the generated storage section.
 */
class LegalDocumentVersionTest {
    @TempDir
    Path dir;

    private Path privacyDir;

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

    private static List<BrowserStorageEntry> withEntry(String key, Necessity necessity) {
        var entries = baseEntries();
        entries.add(entry(key, necessity));
        return entries;
    }

    private LegalDocumentService documents(BrowserStorageCatalog catalog) {
        return new LegalDocumentService(
                dir.resolve("placeholders.json").toString(), new BrowserStorageService(catalog));
    }

    private LegalDocumentService base() {
        return documents(catalog("Remembers the view.", baseEntries()));
    }

    @BeforeEach
    void privacyText() throws IOException {
        privacyDir = dir.resolve("privacy");
        Files.createDirectories(privacyDir.resolve("de"));
        Files.writeString(privacyDir.resolve("de").resolve("01-general.md"), "# Privacy\nWe value your privacy.");
        Files.writeString(privacyDir.resolve("de").resolve("02-browser-storage.md"), "");
    }

    @Test
    void aKeyAddedToAKnownCategoryLeavesTheVersionAsItWas() {
        var before = base();
        var after = documents(catalog("Remembers the view.", withEntry("sidebar_collapsed", Necessity.COMFORT)));

        assertEquals(
                before.versionOf(privacyDir).version(),
                after.versionOf(privacyDir).version());
        assertEquals(
                before.getDocument(privacyDir, "en").version(),
                after.getDocument(privacyDir).version());
        assertNotEquals(
                before.wholeDocumentHash(privacyDir),
                after.wholeDocumentHash(privacyDir),
                "the document itself still lists every key");
    }

    @Test
    void aKeyAddedToAKnownCategoryIsNoChangeAtStartup() {
        assertFalse(base().initialize(privacyDir));

        var after = documents(catalog("Remembers the view.", withEntry("sidebar_collapsed", Necessity.COMFORT)));

        assertFalse(after.initialize(privacyDir));
    }

    @Test
    void aNewCategoryMovesTheVersion() {
        var after = documents(catalog("Remembers the view.", withEntry("page_drafts", Necessity.FUNCTIONAL)));

        assertNotEquals(
                base().versionOf(privacyDir).version(),
                after.versionOf(privacyDir).version());
    }

    @Test
    void aRewordedCategoryMovesTheVersion() {
        var after = documents(catalog("Remembers the view and the colours.", baseEntries()));

        assertNotEquals(
                base().versionOf(privacyDir).version(),
                after.versionOf(privacyDir).version());
    }

    @Test
    void anEditedTextMovesTheVersionAndComesWithItsDiff() throws IOException {
        var documents = base();
        documents.initialize(privacyDir);
        String before = documents.versionOf(privacyDir).version();

        Files.writeString(privacyDir.resolve("de").resolve("01-general.md"), "# Privacy\nWe value it a lot.");
        assertTrue(documents.initialize(privacyDir));
        String after = documents.versionOf(privacyDir).version();

        assertNotEquals(before, after);
        var diff = documents.getDiff(privacyDir, before, after);
        assertNotNull(diff);
        assertTrue(diff.contains("+ We value it a lot."), diff);
    }

    @Test
    void aDocumentWithoutAStorageSectionIsVersionedByItsTextAlone() throws IOException {
        Files.delete(privacyDir.resolve("de").resolve("02-browser-storage.md"));
        var documents = base();

        assertEquals(
                documents.wholeDocumentHash(privacyDir),
                documents.versionOf(privacyDir).version());
    }

    @Test
    void theLegacyHashOfTheCurrentTextIsPinnedAndOutlivesANewKey() {
        var before = base();
        String legacy = before.wholeDocumentHash(privacyDir);
        before.initialize(privacyDir);

        var after = documents(catalog("Remembers the view.", withEntry("sidebar_collapsed", Necessity.COMFORT)));
        after.initialize(privacyDir);

        assertEquals(legacy, after.versionOf(privacyDir).legacyVersion());
        assertTrue(after.versionOf(privacyDir).covers(legacy));
    }

    @Test
    void aVersionFileStillHoldingTheWholeDocumentHashIsNoChange() throws IOException {
        var documents = base();
        Files.writeString(privacyDir.resolve("version.txt"), documents.wholeDocumentHash(privacyDir) + "\n");

        assertFalse(documents.initialize(privacyDir));
        assertEquals(
                documents.versionOf(privacyDir).version(),
                Files.readString(privacyDir.resolve("version.txt")).strip());
    }

    @Test
    void aLegacyHashDiffsAgainstTheVersionItWasPinnedTo() throws IOException {
        var documents = base();
        String legacy = documents.wholeDocumentHash(privacyDir);
        documents.initialize(privacyDir);

        Files.writeString(privacyDir.resolve("de").resolve("01-general.md"), "# Privacy\nWe value it a lot.");
        documents.initialize(privacyDir);
        var current = documents.versionOf(privacyDir);

        assertFalse(current.covers(legacy));
        var diff = documents.getDiff(privacyDir, legacy, current.version());
        assertNotNull(diff);
        assertTrue(diff.contains("+ We value it a lot."), diff);
        assertNull(documents.getDiff(privacyDir, "unknown", current.version()));
    }
}
