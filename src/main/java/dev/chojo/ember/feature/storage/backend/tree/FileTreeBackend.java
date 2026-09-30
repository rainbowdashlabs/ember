/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

import dev.chojo.ember.feature.storage.backend.BackendCapability;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The {@link StorageBackend} of every tree-shaped store: the local disk, SFTP and SMB.
 *
 * <p>Everything but the protocol lives here once. A store writes {@code <key>.partial.<uuid>} and
 * moves it onto {@code <key>}, so a reader never sees half a file; the metadata goes into the
 * {@code <key>.meta.json} sidecar the same way, written once with the SHA-256 computed while the body
 * streamed. The listing hides sidecars and partial files, a delete prunes the directories it leaves
 * empty, and the probe writes, reads and removes a marker under {@code _probe/}. That byte layout is
 * the one every earlier build wrote, so either reads what the other stored.
 *
 * <p>The protocol part is a {@link FileTree}, lent per call by a {@link TreeSource}.
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
    private final String basePath;
    private final Set<BackendCapability> capabilities;

    /**
     * A backend over the trees a source lends.
     *
     * @param type         which kind of store this is
     * @param trees        where each call gets its tree
     * @param basePath     the directory inside the tree every key lives below, {@code /}-separated;
     *                     empty for the tree's root
     * @param capabilities the optional features the store offers
     */
    public FileTreeBackend(
            StorageBackendType type,
            TreeSource<? extends FileTree> trees,
            String basePath,
            Set<BackendCapability> capabilities) {
        this.type = type;
        this.trees = trees;
        this.basePath = normalize(basePath);
        this.capabilities =
                capabilities.isEmpty() ? EnumSet.noneOf(BackendCapability.class) : EnumSet.copyOf(capabilities);
    }

    /**
     * A base path as a tree path: separators unified to {@code /}, none at either end.
     *
     * @param basePath the configured base path, possibly {@code null}
     * @return the normalised path, empty for the root
     */
    public static String normalize(String basePath) {
        if (basePath == null) return "";
        String path = basePath.replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    @Override
    public StorageBackendType type() {
        return type;
    }

    @Override
    public Set<BackendCapability> capabilities() {
        return capabilities.isEmpty() ? EnumSet.noneOf(BackendCapability.class) : EnumSet.copyOf(capabilities);
    }

    @Override
    public void store(String fullKey, InputStream body, long contentLength, ObjectMetadata metadata) {
        storeSealed(fullKey, body, contentLength, metadata);
    }

    /**
     * Stores the body and writes its sidecar once, with the SHA-256 computed while the body streamed.
     *
     * <p>A body held in memory is stored again on a fresh connection when the first one breaks
     * halfway; a body streamed from elsewhere cannot be read twice and is not.
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
     * Opens a key for reading. The tree stays lent to the returned stream until the stream is
     * closed, so a slow download holds one connection and never more; a caller that does not close
     * what it read keeps it, which the pool reports. Opening is tried once more on a fresh connection
     * when the first one turns out to be gone.
     */
    @Override
    public Optional<StoredStream> read(String fullKey) {
        String target = path(fullKey);
        for (int attempt = 1; ; attempt++) {
            Lease<? extends FileTree> lease = trees.acquire();
            try {
                return openLeased(lease, target);
            } catch (IOException e) {
                boolean broken = lease.tree().brokenBy(e);
                if (broken) lease.discard();
                lease.close();
                if (!broken) throw new StorageException(type + " storage call failed: " + e.getMessage(), e);
                if (attempt >= 2) {
                    throw new StorageUnavailableException(type + " storage lost its connection: " + e.getMessage(), e);
                }
                log.info("The connection to the {} storage was lost, trying once more on a fresh one", type);
            } catch (RuntimeException e) {
                if (!lease.tree().isUsable()) lease.discard();
                lease.close();
                throw e;
            }
        }
    }

    private Optional<StoredStream> openLeased(Lease<? extends FileTree> lease, String target) throws IOException {
        FileTree tree = lease.tree();
        var opened = tree.open(target);
        if (opened.isEmpty()) {
            lease.close();
            return Optional.empty();
        }
        ObjectMetadata metadata;
        try {
            metadata = readSidecar(tree, target);
        } catch (RuntimeException e) {
            opened.get().body().close();
            throw e;
        }
        return Optional.of(new StoredStream(
                new LeasedStream(opened.get().body(), lease), opened.get().size(), metadata));
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
        String rooted = prefix == null ? "" : prefix;
        return call(true, tree -> {
            var out = new ArrayList<String>();
            walk(tree, rooted, (key, size) -> out.add(key));
            out.sort(String::compareTo);
            return out;
        });
    }

    @Override
    public long sumSizeByPrefix(String prefix) {
        String rooted = prefix == null ? "" : prefix;
        return call(true, tree -> {
            long[] total = {0};
            walk(tree, rooted, (key, size) -> total[0] += size);
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
            } catch (IOException e) {
                if (tree.brokenBy(e)) lease.discard();
                return HealthStatus.unhealthy(type + " backend probe failed: " + e.getMessage());
            } catch (RuntimeException e) {
                if (!tree.isUsable()) lease.discard();
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
        trees.close();
    }

    /**
     * Runs one call on a lent tree.
     *
     * <p>A tree whose connection broke during the call is closed rather than lent again. An idempotent
     * call is then tried once more on a fresh tree, since repeating it cannot do anything twice; when
     * that fails too, or the call was not idempotent, the storage is reported unreachable.
     *
     * @param idempotent whether repeating the call is harmless
     * @param call       what to do with the tree
     * @return what the call answered
     * @throws StorageUnavailableException when the connection was lost and could not be had again
     * @throws StorageException            when the server refused the call
     */
    protected <R> R call(boolean idempotent, TreeCall<R> call) {
        int attempts = idempotent ? 2 : 1;
        for (int attempt = 1; ; attempt++) {
            try (var lease = trees.acquire()) {
                try {
                    return call.apply(lease.tree());
                } catch (IOException e) {
                    if (!lease.tree().brokenBy(e)) {
                        throw new StorageException(type + " storage call failed: " + e.getMessage(), e);
                    }
                    lease.discard();
                    if (attempt >= attempts) {
                        throw new StorageUnavailableException(
                                type + " storage lost its connection: " + e.getMessage(), e);
                    }
                    log.info("The connection to the {} storage was lost, trying once more on a fresh one", type);
                } catch (RuntimeException e) {
                    if (!lease.tree().isUsable()) lease.discard();
                    throw e;
                }
            }
        }
    }

    /**
     * The tree path of a key: the key below the base path.
     *
     * @param fullKey the key, never empty
     * @return the path inside the tree
     */
    protected String path(String fullKey) {
        if (fullKey == null || fullKey.isEmpty()) {
            throw new IllegalArgumentException("fullKey must not be empty");
        }
        String key = normalize(fullKey);
        return basePath.isEmpty() ? key : basePath + "/" + key;
    }

    private static boolean isFile(FileTree tree, String path) throws IOException {
        return tree.stat(path).filter(info -> !info.directory()).isPresent();
    }

    private static OutputStream createWithParents(FileTree tree, String path) throws IOException {
        try {
            return tree.create(path);
        } catch (NoSuchFileException missingParent) {
            tree.makeDirectories(parentOf(path));
            return tree.create(path);
        }
    }

    private static void writeFile(FileTree tree, String path, InputStream body) throws IOException {
        try (OutputStream out = createWithParents(tree, path)) {
            byte[] buffer = new byte[TRANSFER_BUFFER_BYTES];
            int read;
            while ((read = body.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
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
        String start = prefix.isEmpty() ? basePath : path(prefix);
        var info = tree.stat(start);
        if (info.isEmpty()) return;
        if (!info.get().directory()) {
            if (!prefix.isEmpty()) visitor.visit(prefix, info.get().size());
            return;
        }
        walkDirectory(tree, start, normalize(prefix), visitor);
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

    /**
     * One call against a lent tree.
     *
     * @param <R> what the call answers
     */
    @FunctionalInterface
    protected interface TreeCall<R> {
        /**
         * Does the work.
         *
         * @param tree the lent tree
         * @return the answer
         * @throws IOException when the protocol fails
         */
        R apply(FileTree tree) throws IOException;
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
