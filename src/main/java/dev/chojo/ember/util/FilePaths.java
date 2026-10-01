/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The name and the parent of a path, for the paths that may have none.
 *
 * <p>{@link Path#getFileName()} answers null for a root and {@link Path#getParent()} for a path of
 * a single name. Neither happens to an entry of a listed directory or a file resolved below one, but
 * the types do not say so, and calling through them reads as a gamble.
 */
public final class FilePaths {

    private FilePaths() {}

    /**
     * The last name of a path, as text.
     *
     * @param path the path
     * @return its file name, or an empty text for a root, which has none
     */
    public static String nameOf(Path path) {
        Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }

    /**
     * Creates the directory a file is going to be written to, and the ones above it.
     *
     * @param file the file about to be written
     * @throws IOException when a directory cannot be created
     */
    public static void createParentDirectories(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
