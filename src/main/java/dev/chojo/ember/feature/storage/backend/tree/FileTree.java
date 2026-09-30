/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;

/**
 * The file operations of one tree-shaped protocol. Paths are relative to the root of the tree and
 * separated by {@code /}. One tree is one connection's worth of state, used by one caller at a time.
 */
public interface FileTree extends AutoCloseable {

    /**
     * Creates or truncates a file and opens it for writing; closing the stream finishes the file.
     *
     * @throws NoSuchFileException when the parent directory does not exist
     */
    OutputStream create(String path) throws IOException;

    /** Opens a file for reading; empty when there is no such file. */
    Optional<OpenFile> open(String path) throws IOException;

    /** What is at a path; empty when nothing is. */
    Optional<FileInfo> stat(String path) throws IOException;

    /**
     * Moves a file onto another, replacing it, atomically wherever the protocol allows. When it fails
     * the source is still in place.
     */
    void replace(String source, String target) throws IOException;

    /** Removes a file; false when there was none. */
    boolean remove(String path) throws IOException;

    /** Creates a directory and every missing one above it. */
    void makeDirectories(String path) throws IOException;

    /** The entries of a directory without {@code .} and {@code ..}; empty when there is no such directory. */
    List<FileInfo> list(String directory) throws IOException;

    /** Removes a directory if it is empty; false when it was not removed. */
    boolean removeDirectory(String path);

    /** False once the connection underneath the tree is gone. */
    boolean isUsable();

    /**
     * Whether a failure means the connection is gone, a timeout included, rather than that the server
     * answered no. A tree that answers yes tears that connection down, so no tree on it is lent again.
     */
    default boolean brokenBy(IOException failure) {
        return !isUsable();
    }

    /** Releases the connection state the tree holds. Never throws. */
    @Override
    void close();
}
