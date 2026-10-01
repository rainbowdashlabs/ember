/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A path's name and parent directories, including for the paths that have neither.
 */
class FilePathsTest {

    @TempDir
    Path directory;

    @Test
    void theNameIsTheLastPartOfThePath() {
        assertEquals("report.pdf", FilePaths.nameOf(directory.resolve("2026").resolve("report.pdf")));
    }

    @Test
    void aRootHasAnEmptyName() {
        assertEquals("", FilePaths.nameOf(directory.getRoot()));
    }

    @Test
    void theDirectoriesAboveAFileAreCreated() throws IOException {
        Path file = directory.resolve("a").resolve("b").resolve("file.txt");

        FilePaths.createParentDirectories(file);

        assertTrue(Files.isDirectory(file.getParent()));
    }

    @Test
    void aFileWithoutAParentNeedsNoDirectory() {
        assertDoesNotThrow(() -> FilePaths.createParentDirectories(Path.of("file.txt")));
    }
}
