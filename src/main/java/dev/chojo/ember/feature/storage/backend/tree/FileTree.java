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
 * The few file operations a tree-shaped store offers, one implementation per protocol.
 *
 * <p>Everything a backend does beyond these, the partial write and its rename, the sidecar, the walk,
 * the pruning of empty directories and the probe, is written once in {@link FileTreeBackend}. A path
 * here is relative to the root of the tree and separated by {@code /}; the protocol turns it into its
 * own spelling. One tree is one connection's worth of state and is used by one caller at a time.
 */
public interface FileTree extends AutoCloseable {

    /**
     * Creates a file, or truncates one that exists, and opens it for writing.
     *
     * @param path the file
     * @return the stream the bytes are written to; closing it finishes the file
     * @throws NoSuchFileException when the directory the file is to be created in does not exist
     * @throws IOException         on any other failure
     */
    OutputStream create(String path) throws IOException;

    /**
     * Opens a file for reading.
     *
     * @param path the file
     * @return the file's bytes and size, or empty when there is no such file
     * @throws IOException on a failure other than the file being absent
     */
    Optional<OpenFile> open(String path) throws IOException;

    /**
     * What is at a path.
     *
     * @param path the file or directory
     * @return what is there, or empty when nothing is
     * @throws IOException on a failure other than the path being absent
     */
    Optional<FileInfo> stat(String path) throws IOException;

    /**
     * Moves a file onto another, replacing it. Atomic wherever the protocol offers it, so a reader
     * sees the old file or the new one and never neither.
     *
     * @param source the file to move
     * @param target where it goes
     * @throws IOException when the move fails; the source is then still in place
     */
    void replace(String source, String target) throws IOException;

    /**
     * Removes a file.
     *
     * @param path the file
     * @return false when there was no such file
     * @throws IOException on a failure other than the file being absent
     */
    boolean remove(String path) throws IOException;

    /**
     * Creates a directory and every missing directory above it. Nothing happens when it exists.
     *
     * @param path the directory
     * @throws IOException when a directory cannot be created
     */
    void makeDirectories(String path) throws IOException;

    /**
     * The entries of a directory, without {@code .} and {@code ..}.
     *
     * @param directory the directory, empty for the root
     * @return the entries, empty when there is no such directory
     * @throws IOException on a failure other than the directory being absent
     */
    List<FileInfo> list(String directory) throws IOException;

    /**
     * Removes a directory if it is empty.
     *
     * @param path the directory
     * @return false when it was not removed, because it holds something or is gone already
     */
    boolean removeDirectory(String path);

    /**
     * Whether the tree can still be used, which a pool asks before handing it out again. False once
     * the connection underneath it is gone.
     */
    boolean isUsable();

    /** Releases the connection state the tree holds. Never throws. */
    @Override
    void close();
}
