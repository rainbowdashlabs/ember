/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderServiceTest {

    @TempDir
    Path tempDir;

    private PlaceholderService service;

    @BeforeEach
    void setUp() {
        service = new PlaceholderService(tempDir.resolve("placeholders.json"));
    }

    @Test
    void namesAreFoundWithAndWithoutWhitespace() {
        assertEquals(
                Set.of("a", "b", "c.d", "e_f", "g-h"),
                PlaceholderService.namesIn("{{a}} {{ b }} {{  c.d  }} {{e_f}} {{ g-h }}"));
    }

    @Test
    void aNameIsReportedOnceHoweverOftenItAppears() {
        assertEquals(Set.of("name"), PlaceholderService.namesIn("{{ name }} and again {{name}}"));
    }

    @Test
    void malformedTokensAreNotPlaceholders() {
        assertTrue(PlaceholderService.namesIn("{ name } {{ }} {{ na me }} {{}}").isEmpty());
    }

    @Test
    void textWithoutPlaceholdersIsReturnedUnchanged() {
        service.save(Map.of("name", "value"));
        assertEquals("plain text", service.apply("plain text"));
    }

    @Test
    void configuredValuesAreSubstituted() {
        service.save(Map.of("betreiber.name", "Jugendfeuerwehr Musterstadt"));
        assertEquals("Betreiber: Jugendfeuerwehr Musterstadt", service.apply("Betreiber: {{ betreiber.name }}"));
    }

    @Test
    void aPlaceholderWithoutValueStaysVisible() {
        service.save(Map.of("known", "here"));
        assertEquals("here and {{ unknown }}", service.apply("{{ known }} and {{ unknown }}"));
    }

    @Test
    void valuesWithRegexMetaCharactersAreInsertedLiterally() {
        service.save(Map.of("contact", "a$b\\c"));
        assertEquals("a$b\\c", service.apply("{{ contact }}"));
    }

    @Test
    void blankValuesAreNotStored() {
        var values = new java.util.HashMap<String, String>();
        values.put("kept", "value");
        values.put("blank", "  ");
        values.put("missing", null);
        service.save(values);
        assertEquals(Map.of("kept", "value"), service.values());
    }

    @Test
    void valuesAreRereadAfterTheFileChanges() throws IOException {
        Path file = tempDir.resolve("placeholders.json");
        service.save(Map.of("name", "first"));
        assertEquals("first", service.values().get("name"));

        Files.writeString(file, "{\"name\":\"second\"}", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 2000));
        assertEquals("second", service.values().get("name"));
    }

    @Test
    void missingOrBrokenFilesYieldNoValues() throws IOException {
        assertTrue(service.values().isEmpty());
        Files.writeString(tempDir.resolve("placeholders.json"), "not json", StandardCharsets.UTF_8);
        assertTrue(service.values().isEmpty());
    }

    @Test
    void scanReportsEverySectionAPlaceholderAppearsIn() throws IOException {
        Path docs = tempDir.resolve("imprint");
        Files.createDirectories(docs.resolve("de"));
        Files.createDirectories(docs.resolve("en"));
        Files.createDirectories(docs.resolve("history"));
        Files.writeString(docs.resolve("de/01-impressum.md"), "{{ betreiber.name }}", StandardCharsets.UTF_8);
        Files.writeString(docs.resolve("en/01-imprint.md"), "{{ betreiber.name }}", StandardCharsets.UTF_8);
        Files.writeString(docs.resolve("history/deadbeef.md"), "{{ archived }}", StandardCharsets.UTF_8);

        var found = service.scan(docs, "imprint");

        assertEquals(Set.of("betreiber.name"), found.keySet());
        List<String> locales = found.get("betreiber.name").stream()
                .map(usage -> usage.locale())
                .sorted()
                .toList();
        assertEquals(List.of("de", "en"), locales);

        // Sorted rather than taken in the order they were found: which language of a document the
        // scan reaches first is the file system's business, and it differs between machines.
        List<String> sections = found.get("betreiber.name").stream()
                .map(usage -> usage.section())
                .sorted()
                .toList();
        assertEquals(List.of("impressum", "imprint"), sections);
        assertTrue(found.get("betreiber.name").stream().allMatch(usage -> "imprint".equals(usage.type())));
    }

    @Test
    void disabledSectionsAreScannedToo() throws IOException {
        Path docs = tempDir.resolve("tos");
        Files.createDirectories(docs.resolve("de"));
        Files.writeString(docs.resolve("de/_02-entwurf.md"), "{{ draft }}", StandardCharsets.UTF_8);

        var found = service.scan(docs, "tos");

        assertEquals(Set.of("draft"), found.keySet());
        assertEquals("entwurf", found.get("draft").getFirst().section());
    }

    @Test
    void emptyInputIsHandledWithoutScanning() {
        assertTrue(PlaceholderService.namesIn(null).isEmpty());
        assertTrue(PlaceholderService.namesIn("").isEmpty());
        assertEquals("", service.apply(""));
        assertEquals(null, service.apply(null));
    }

    @Test
    void anUnusableValueFileIsReportedRatherThanThrown() throws IOException {
        Path blocked = tempDir.resolve("blocked");
        Files.writeString(blocked, "not a directory", StandardCharsets.UTF_8);

        var blockedService = new PlaceholderService(blocked.resolve("nested/placeholders.json"));
        blockedService.save(Map.of("name", "value"));

        assertTrue(blockedService.values().isEmpty());
    }

    @Test
    void anUnreadableDocumentDirectoryIsReportedRatherThanThrown() throws IOException {
        Path docs = tempDir.resolve("unreadable");
        Files.createDirectories(docs.resolve("de"));
        Files.writeString(docs.resolve("de/01-section.md"), "{{ name }}", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(docs.resolve("de"), Set.of());

        try {
            assertTrue(service.scan(docs, "tos").isEmpty());
        } finally {
            Files.setPosixFilePermissions(
                    docs.resolve("de"), java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void scanningAMissingDirectoryFindsNothing() {
        assertTrue(service.scan(tempDir.resolve("absent"), "tos").isEmpty());
    }

    @Test
    void theBundledImprintDeclaresItsPlaceholders() throws IOException {
        var names = PlaceholderService.namesIn(
                Files.readString(Path.of("templates/data/documents/imprint/de/01-impressum.md")));
        assertFalse(names.isEmpty(), "the shipped imprint is meant to be filled in through placeholders");
        assertTrue(names.contains("betreiber.name"));
    }
}
