/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

/**
 * One entry of a {@link FileTree}.
 *
 * @param name      the last segment of its path
 * @param directory whether it is a directory
 * @param size      its size in bytes; zero for a directory
 */
public record FileInfo(String name, boolean directory, long size) {
    public FileInfo {
        if (directory) size = 0;
    }

    /**
     * The entry at a tree path, named after its last segment.
     *
     * @param path the {@code /}-separated tree path
     */
    public static FileInfo at(String path, boolean directory, long size) {
        return new FileInfo(path.substring(path.lastIndexOf('/') + 1), directory, size);
    }
}
