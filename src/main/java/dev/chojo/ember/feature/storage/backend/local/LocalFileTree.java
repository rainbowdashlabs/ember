/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.local;

import dev.chojo.ember.feature.storage.backend.tree.FileInfo;
import dev.chojo.ember.feature.storage.backend.tree.FileTree;
import dev.chojo.ember.feature.storage.backend.tree.OpenFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The local disk as a {@link FileTree}, rooted at one directory. Holds no connection and is safe to
 * share between callers.
 */
final class LocalFileTree implements FileTree {
    private final Path root;

    LocalFileTree(Path root) {
        this.root = root;
    }

    /** The path on disk of a tree path, refusing any path that would leave the root. */
    Path resolve(String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Key escapes storage root: " + path);
        }
        return resolved;
    }

    @Override
    public OutputStream create(String path) throws IOException {
        return Files.newOutputStream(
                resolve(path),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    @Override
    public Optional<OpenFile> open(String path) throws IOException {
        Path file = resolve(path);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(new OpenFile(Files.newInputStream(file), Files.size(file)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<FileInfo> stat(String path) throws IOException {
        Path file = resolve(path);
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return Optional.of(info(file, attributes));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        }
    }

    @Override
    public void replace(String source, String target) throws IOException {
        try {
            Files.move(
                    resolve(source),
                    resolve(target),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(resolve(source), resolve(target), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public boolean remove(String path) throws IOException {
        return Files.deleteIfExists(resolve(path));
    }

    @Override
    public void makeDirectories(String path) throws IOException {
        Files.createDirectories(resolve(path));
    }

    @Override
    public List<FileInfo> list(String directory) throws IOException {
        Path dir = resolve(directory);
        if (!Files.isDirectory(dir)) return List.of();
        var out = new ArrayList<FileInfo>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                try {
                    out.add(info(entry, Files.readAttributes(entry, BasicFileAttributes.class)));
                } catch (NoSuchFileException gone) {
                    continue;
                }
            }
        } catch (NoSuchFileException e) {
            return List.of();
        }
        return out;
    }

    @Override
    public boolean removeDirectory(String path) {
        try {
            Path dir = resolve(path);
            return Files.isDirectory(dir) && Files.deleteIfExists(dir);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public void close() {}

    private static FileInfo info(Path file, BasicFileAttributes attributes) {
        Path name = file.getFileName();
        return new FileInfo(name == null ? "" : name.toString(), attributes.isDirectory(), attributes.size());
    }
}
