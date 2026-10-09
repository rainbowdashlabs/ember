/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

import dev.chojo.ember.feature.storage.backend.BackendDestination;
import dev.chojo.ember.feature.storage.backend.DigestingInputStream;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.MetadataSidecar;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.backend.StorageUnavailableException;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@link StorageBackend} of the tree-shaped stores: the local disk, SFTP and SMB.
 *
 * <p>A body is written to {@code <key>.partial.<uuid>} and moved onto {@code <key>}, so a reader never
 * sees half a file, and its metadata goes into the {@code <key>.meta.json} sidecar the same way. That
 * layout is the one earlier builds wrote, so either reads what the other stored. The protocol itself
 * is a {@link FileTree}, lent per call by a {@link TreeSource}.
 */
public class FileTreeBackend implements StorageBackend {
    private static final Logger log = LoggerFactory.getLogger(FileTreeBackend.class);
    private static final String META_SUFFIX = ".meta.json";
    private static final String PARTIAL_MARKER = ".partial.";
    private static final String PROBE_PREFIX = "_probe";
    private static final String UNTYPED = "application/octet-stream";
    private static final int TRANSFER_BUFFER_BYTES = 64 * 1024;

    private final StorageBackendType type;
    private final TreeSource<? extends FileTree> trees;
    private final String tree;
    private final String basePath;

    /**
     * @param tree     the tree the backend works in, without the base path: a host, port and share, or a
     *                 directory on disk; part of the {@link #destination()}
     * @param basePath the directory inside the tree every key lives below; empty or {@code null} for the root
     */
    protected FileTreeBackend(
            StorageBackendType type, TreeSource<? extends FileTree> trees, String tree, String basePath) {
        this.type = type;
        this.trees = trees;
        this.tree = tree;
        this.basePath = BackendDestination.treePath(basePath);
    }

    @Override
    public StorageBackendType type() {
        return type;
    }

    @Override
    public String destination() {
        return BackendDestination.tree(type, tree, basePath);
    }

    @Override
    public void store(String fullKey, InputStream body, long contentLength, ObjectMetadata metadata) {
        storeSealed(fullKey, body, contentLength, metadata);
    }

    /**
     * Writes the sidecar once, after the body. A body held in memory is stored again on a fresh
     * connection when the first one breaks halfway; a streamed body cannot be read twice and is not.
     */
    @Override
    public ObjectMetadata storeSealed(String fullKey, InputStream body, long contentLength, ObjectMetadata metadata) {
        String target = path(fullKey);
        boolean repeatable = body.markSupported();
        if (repeatable) body.mark(Integer.MAX_VALUE);
        return call(repeatable, tree -> {
            if (repeatable) body.reset();
            String partial = target + PARTIAL_MARKER + UUID.randomUUID();
            var digesting = new DigestingInputStream(body);
            try {
                writeFile(tree, partial, digesting);
                tree.replace(partial, target);
                ObjectMetadata sealed = metadata.withSha256(digesting.hexDigest());
                writeSidecar(tree, target, sealed);
                return sealed;
            } catch (IOException | RuntimeException e) {
                removeQuietly(tree, partial);
                throw e;
            }
        });
    }

    @Override
    public void updateMetadata(String fullKey, ObjectMetadata metadata) {
        String target = path(fullKey);
        call(true, tree -> {
            if (!isFile(tree, target)) {
                throw new StorageException("Cannot update metadata; missing object " + fullKey);
            }
            writeSidecar(tree, target, metadata);
            return null;
        });
    }

    /**
     * The tree stays lent to the returned stream until it is closed, so a slow download holds one
     * connection and never more.
     */
    @Override
    public Optional<StoredStream> read(String fullKey) {
        String target = path(fullKey);
        return leased(true, lease -> {
            var opened = lease.tree().open(target);
            if (opened.isEmpty()) {
                lease.close();
                return Optional.empty();
            }
            ObjectMetadata metadata;
            try {
                metadata = readSidecar(lease.tree(), target);
            } catch (RuntimeException e) {
                opened.get().body().close();
                throw e;
            }
            return Optional.of(new StoredStream(
                    new LeasedStream(opened.get().body(), lease), opened.get().size(), metadata));
        });
    }

    /**
     * Copies the body into {@code <target>.partial.<uuid>} with the tree's own copy and moves it onto the
     * target, so a reader never sees half a file, then gives the target the source's metadata.
     */
    @Override
    public boolean copy(String sourceKey, String targetKey) {
        String source = path(sourceKey);
        String target = path(targetKey);
        return call(true, tree -> {
            if (!isFile(tree, source)) return false;
            String partial = target + PARTIAL_MARKER + UUID.randomUUID();
            try {
                copyFile(tree, source, partial);
                tree.replace(partial, target);
            } catch (IOException | RuntimeException e) {
                removeQuietly(tree, partial);
                throw e;
            }
            writeSidecar(tree, target, readSidecar(tree, source));
            return true;
        });
    }

    @Override
    public void delete(String fullKey) {
        String target = path(fullKey);
        call(true, tree -> {
            tree.remove(target);
            tree.remove(target + META_SUFFIX);
            prune(tree, target);
            return null;
        });
    }

    @Override
    public boolean exists(String fullKey) {
        String target = path(fullKey);
        return call(true, tree -> isFile(tree, target));
    }

    @Override
    public List<String> listByPrefix(String prefix) {
        return call(true, tree -> {
            var out = new ArrayList<String>();
            walk(tree, prefix, (key, size) -> out.add(key));
            out.sort(String::compareTo);
            return out;
        });
    }

    @Override
    public long sumSizeByPrefix(String prefix) {
        return call(true, tree -> {
            long[] total = {0};
            walk(tree, prefix, (key, size) -> total[0] += size);
            return total[0];
        });
    }

    @Override
    public Optional<Long> size(String fullKey) {
        String target = path(fullKey);
        return call(
                true,
                tree -> tree.stat(target).filter(info -> !info.directory()).map(FileInfo::size));
    }

    @Override
    public HealthStatus probe() {
        String target = path(PROBE_PREFIX + "/" + UUID.randomUUID());
        byte[] payload = "probe".getBytes(StandardCharsets.UTF_8);
        try (var lease = trees.acquireForProbe()) {
            FileTree tree = lease.tree();
            try {
                writeFile(tree, target, new ByteArrayInputStream(payload));
                var opened = tree.open(target);
                if (opened.isEmpty()) return HealthStatus.unhealthy(type + " backend lost the probe it just wrote");
                try (InputStream in = opened.get().body()) {
                    if (!"probe".equals(new String(in.readAllBytes(), StandardCharsets.UTF_8))) {
                        return HealthStatus.unhealthy(type + " backend read returned unexpected payload");
                    }
                }
                return HealthStatus.ok();
            } catch (IOException | RuntimeException e) {
                if (e instanceof IOException io ? tree.brokenBy(io) : !tree.isUsable()) lease.discard();
                return HealthStatus.unhealthy(type + " backend probe failed: " + e.getMessage());
            } finally {
                removeQuietly(tree, target);
                prune(tree, target);
            }
        } catch (RuntimeException e) {
            return HealthStatus.unhealthy(type + " backend probe failed: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        trees.close(this::released);
    }

    /** Releases what the trees of this backend share, once the last lent one is back. Nothing by default. */
    protected void released() {}

    private <R> R call(boolean idempotent, IoFunction<FileTree, R> call) {
        return leased(idempotent, lease -> {
            R result = call.apply(lease.tree());
            lease.close();
            return result;
        });
    }

    /**
     * Runs one call on a lent tree, which the call closes once it succeeded. A tree whose connection
     * broke is discarded, and an idempotent call is tried once more on a fresh one before the storage
     * is reported unreachable.
     *
     * @throws StorageUnavailableException when the connection was lost and could not be had again
     * @throws StorageException            when the server refused the call
     */
    private <R> R leased(boolean idempotent, IoFunction<Lease<? extends FileTree>, R> call) {
        for (int attempt = 1; ; attempt++) {
            Lease<? extends FileTree> lease = trees.acquire();
            try {
                return call.apply(lease);
            } catch (IOException e) {
                boolean broken = lease.tree().brokenBy(e);
                if (broken) lease.discard();
                lease.close();
                if (!broken) throw new StorageException(type + " storage call failed: " + e.getMessage(), e);
                if (attempt >= (idempotent ? 2 : 1)) {
                    throw new StorageUnavailableException(type + " storage lost its connection: " + e.getMessage(), e);
                }
                log.info("The connection to the {} storage was lost, trying once more on a fresh one", type);
            } catch (RuntimeException | Error e) {
                if (!lease.tree().isUsable()) lease.discard();
                lease.close();
                throw e;
            }
        }
    }

    private String path(String fullKey) {
        if (fullKey == null || fullKey.isEmpty()) {
            throw new IllegalArgumentException("fullKey must not be empty");
        }
        String key = BackendDestination.treePath(fullKey);
        return basePath.isEmpty() ? key : basePath + "/" + key;
    }

    private static boolean isFile(FileTree tree, String path) throws IOException {
        return tree.stat(path).filter(info -> !info.directory()).isPresent();
    }

    private static void writeFile(FileTree tree, String path, InputStream body) throws IOException {
        OutputStream created;
        try {
            created = tree.create(path);
        } catch (NoSuchFileException missingParent) {
            tree.makeDirectories(parentOf(path));
            created = tree.create(path);
        }
        try (OutputStream out = created) {
            byte[] buffer = new byte[TRANSFER_BUFFER_BYTES];
            int read;
            while ((read = body.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static void copyFile(FileTree tree, String source, String target) throws IOException {
        if (tree.stat(parentOf(target)).filter(FileInfo::directory).isEmpty()) {
            tree.makeDirectories(parentOf(target));
        }
        tree.copy(source, target);
    }

    private static void writeSidecar(FileTree tree, String target, ObjectMetadata metadata) throws IOException {
        byte[] bytes = Json.MAPPER.writeValueAsBytes(MetadataSidecar.from(metadata));
        String sidecar = target + META_SUFFIX;
        String partial = sidecar + PARTIAL_MARKER + UUID.randomUUID();
        try {
            writeFile(tree, partial, new ByteArrayInputStream(bytes));
            tree.replace(partial, sidecar);
        } catch (IOException | RuntimeException e) {
            removeQuietly(tree, partial);
            throw e;
        }
    }

    private static ObjectMetadata readSidecar(FileTree tree, String target) {
        String sidecar = target + META_SUFFIX;
        try {
            var opened = tree.open(sidecar);
            if (opened.isEmpty()) return ObjectMetadata.of(UNTYPED);
            try (InputStream in = opened.get().body()) {
                return Json.MAPPER
                        .readValue(in.readAllBytes(), MetadataSidecar.class)
                        .toObjectMetadata();
            }
        } catch (IOException | JacksonException e) {
            log.warn("Failed to read metadata sidecar {}", sidecar, e);
            return ObjectMetadata.of(UNTYPED);
        }
    }

    private void walk(FileTree tree, String prefix, KeyVisitor visitor) throws IOException {
        String rooted = prefix == null ? "" : prefix;
        String start = rooted.isEmpty() ? basePath : path(rooted);
        var info = tree.stat(start);
        if (info.isEmpty()) return;
        if (!info.get().directory()) {
            if (!rooted.isEmpty()) visitor.visit(rooted, info.get().size());
            return;
        }
        walkDirectory(tree, start, BackendDestination.treePath(rooted), visitor);
    }

    private static void walkDirectory(FileTree tree, String directory, String keyPrefix, KeyVisitor visitor)
            throws IOException {
        for (FileInfo entry : tree.list(directory)) {
            String childPath = directory.isEmpty() ? entry.name() : directory + "/" + entry.name();
            String childKey = keyPrefix.isEmpty() ? entry.name() : keyPrefix + "/" + entry.name();
            if (entry.directory()) {
                walkDirectory(tree, childPath, childKey, visitor);
            } else if (!childKey.endsWith(META_SUFFIX) && !childKey.contains(PARTIAL_MARKER)) {
                visitor.visit(childKey, entry.size());
            }
        }
    }

    private void prune(FileTree tree, String path) {
        String parent = parentOf(path);
        while (!parent.isEmpty() && !parent.equals(basePath)) {
            try {
                if (!tree.list(parent).isEmpty()) return;
            } catch (IOException e) {
                return;
            }
            if (!tree.removeDirectory(parent)) return;
            parent = parentOf(parent);
        }
    }

    private static void removeQuietly(FileTree tree, String path) {
        try {
            tree.remove(path);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not remove {}", path, e);
        }
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    @FunctionalInterface
    private interface IoFunction<A, R> {
        R apply(A argument) throws IOException;
    }

    @FunctionalInterface
    private interface KeyVisitor {
        void visit(String key, long size);
    }

    /** The body of a read, handing its tree back when it is closed. */
    private static final class LeasedStream extends FilterInputStream {
        private final Lease<? extends FileTree> lease;

        private LeasedStream(InputStream body, Lease<? extends FileTree> lease) {
            super(body);
            this.lease = lease;
        }

        @Override
        public int read() throws IOException {
            try {
                return super.read();
            } catch (IOException e) {
                discardIfBroken();
                throw e;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                return super.read(b, off, len);
            } catch (IOException e) {
                discardIfBroken();
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                lease.close();
            }
        }

        private void discardIfBroken() {
            if (!lease.tree().isUsable()) lease.discard();
        }
    }
}
