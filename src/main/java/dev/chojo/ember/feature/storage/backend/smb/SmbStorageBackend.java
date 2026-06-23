/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.smb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mserref.NtStatus;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.MetadataSidecar;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SMB3-backed {@link StorageBackend}. Talks directly to the share through the smbj library —
 * no kernel mount, no FUSE, no {@code SYS_ADMIN}.
 *
 * <p>SMB3 {@code seal} (in-flight encryption) is on by default; operators downgrading to a
 * legacy SMB1 server set it off via {@link SmbBackendConfig}. DFS referral following is off
 * by default and again opt-in.
 *
 * <p>Atomicity matches the local backend: payloads write to {@code <key>.partial.<uuid>}
 * inside the share, then rename onto the final path on success. Metadata is persisted as a
 * sibling {@code <key>.meta.json} in the same JSON shape the local backend uses, so a future
 * migration tool can copy bytes between backends without touching the metadata.
 *
 * <p>The backend opens an {@link SMBClient} per instance and a {@link Session} +
 * {@link DiskShare} lazily on first use; both stay open for the lifetime of the backend and
 * are closed via {@link #close()}.
 */
public class SmbStorageBackend implements StorageBackend, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SmbStorageBackend.class);
    private static final String META_SUFFIX = ".meta.json";
    private static final String PROBE_PREFIX = "_probe";
    private static final int TRANSFER_BUFFER_BYTES = 8 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SmbBackendConfig config;
    private final SMBClient client;
    private final String basePath;

    private Connection connection;
    private Session session;
    private DiskShare share;

    public SmbStorageBackend(SmbBackendConfig config) {
        this(config, defaultClient(config));
    }

    /** Visible for tests that want to inject a pre-configured {@link SMBClient}. */
    SmbStorageBackend(SmbBackendConfig config, SMBClient client) {
        this.config = config;
        this.client = client;
        this.basePath = normalizeBasePath(config.basePath());
    }

    private static SMBClient defaultClient(SmbBackendConfig config) {
        var builder = SmbConfig.builder().withEncryptData(config.seal()).withDfsEnabled(config.dfs());
        return new SMBClient(builder.build());
    }

    private static String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isBlank() || basePath.equals("/") || basePath.equals("\\")) return "";
        String trimmed = basePath.replace('/', '\\');
        while (trimmed.startsWith("\\")) trimmed = trimmed.substring(1);
        while (trimmed.endsWith("\\")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    @Override
    public StorageBackendType type() {
        return StorageBackendType.SMB;
    }

    @Override
    public synchronized void store(String fullKey, InputStream body, long contentLength, ObjectMetadata metadata) {
        String target = path(fullKey);
        String partial = target + ".partial." + UUID.randomUUID();
        DiskShare share = openShare();
        try {
            ensureParent(share, target);
            try (File file = openForWrite(share, partial)) {
                writeStream(file, body);
            }
            renameOnto(share, partial, target);
            writeMetadataSidecar(share, target, metadata);
        } catch (IOException | SMBApiException e) {
            safeDelete(share, partial);
            throw new StorageException("SMB store failed for " + fullKey, e);
        }
    }

    @Override
    public synchronized void updateMetadata(String fullKey, ObjectMetadata metadata) {
        DiskShare share = openShare();
        String target = path(fullKey);
        if (!share.fileExists(target)) {
            throw new StorageException("Cannot update metadata; missing object " + fullKey);
        }
        try {
            writeMetadataSidecar(share, target, metadata);
        } catch (IOException e) {
            throw new StorageException("SMB updateMetadata failed for " + fullKey, e);
        }
    }

    @Override
    public synchronized Optional<StoredStream> read(String fullKey) {
        DiskShare share = openShare();
        String target = path(fullKey);
        if (!share.fileExists(target)) return Optional.empty();
        try {
            File file = openForRead(share, target);
            long size = file.getFileInformation().getStandardInformation().getEndOfFile();
            ObjectMetadata metadata = readMetadataSidecar(share, target);
            InputStream stream = new SmbReadStream(file);
            return Optional.of(new StoredStream(stream, size, metadata));
        } catch (SMBApiException e) {
            throw new StorageException("SMB read failed for " + fullKey, e);
        }
    }

    @Override
    public synchronized void delete(String fullKey) {
        DiskShare share = openShare();
        String target = path(fullKey);
        safeDelete(share, target);
        safeDelete(share, target + META_SUFFIX);
        pruneEmptyParents(share, target);
    }

    @Override
    public synchronized boolean exists(String fullKey) {
        return openShare().fileExists(path(fullKey));
    }

    @Override
    public synchronized List<String> listByPrefix(String prefix) {
        DiskShare share = openShare();
        var out = new ArrayList<String>();
        String rooted = prefix == null ? "" : prefix;
        String full = rooted.isEmpty() ? (basePath.isEmpty() ? "" : basePath) : path(rooted);
        if (!full.isEmpty() && share.fileExists(full)) {
            out.add(rooted);
            return out;
        }
        if (!full.isEmpty() && !share.folderExists(full)) return out;
        walk(share, full, rooted, out);
        out.sort(String::compareTo);
        return out;
    }

    @Override
    public synchronized HealthStatus probe() {
        String key = PROBE_PREFIX + "/" + UUID.randomUUID();
        String target = path(key);
        DiskShare share = openShare();
        try {
            ensureParent(share, target);
            try (File file = openForWrite(share, target);
                    OutputStream out = file.getOutputStream()) {
                out.write("probe".getBytes());
            }
            try (File file = openForRead(share, target);
                    InputStream in = file.getInputStream()) {
                String readBack = new String(in.readAllBytes());
                if (!"probe".equals(readBack)) {
                    return HealthStatus.unhealthy("SMB backend read returned unexpected payload");
                }
            }
            return HealthStatus.ok();
        } catch (IOException | SMBApiException e) {
            return HealthStatus.unhealthy("SMB backend probe failed: " + e.getMessage());
        } finally {
            safeDelete(share, target);
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (share != null) share.close();
        } catch (IOException ignored) {
        }
        try {
            if (session != null) session.close();
        } catch (IOException ignored) {
        }
        try {
            if (connection != null) connection.close();
        } catch (IOException ignored) {
        }
        client.close();
    }

    private DiskShare openShare() {
        if (share != null && share.isConnected()) return share;
        try {
            if (connection == null || !connection.isConnected()) {
                connection = client.connect(config.host(), config.port());
            }
            if (session == null) {
                session = connection.authenticate(new AuthenticationContext(
                        config.username(),
                        config.password() == null
                                ? new char[0]
                                : config.password().toCharArray(),
                        config.domain() == null || config.domain().isBlank() ? "" : config.domain()));
            }
            share = (DiskShare) session.connectShare(config.share());
            return share;
        } catch (IOException e) {
            throw new StorageException("SMB connect failed: " + e.getMessage(), e);
        }
    }

    private String path(String fullKey) {
        if (fullKey == null || fullKey.isEmpty()) {
            throw new IllegalArgumentException("fullKey must not be empty");
        }
        String slashed = fullKey.replace('/', '\\');
        if (basePath.isEmpty()) return slashed;
        return basePath + "\\" + slashed;
    }

    private void ensureParent(DiskShare share, String target) {
        int slash = target.lastIndexOf('\\');
        if (slash < 0) return;
        String parent = target.substring(0, slash);
        if (parent.isEmpty() || share.folderExists(parent)) return;
        createDirectories(share, parent);
    }

    private void createDirectories(DiskShare share, String dir) {
        if (dir.isEmpty() || share.folderExists(dir)) return;
        int slash = dir.lastIndexOf('\\');
        if (slash > 0) createDirectories(share, dir.substring(0, slash));
        try {
            share.mkdir(dir);
        } catch (SMBApiException e) {
            if (e.getStatus() != NtStatus.STATUS_OBJECT_NAME_COLLISION) throw e;
        }
    }

    private File openForWrite(DiskShare share, String path) {
        return share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                EnumSet.noneOf(SMB2CreateOptions.class));
    }

    private File openForRead(DiskShare share, String path) {
        return share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null);
    }

    private void writeStream(File file, InputStream body) throws IOException {
        try (OutputStream out = file.getOutputStream()) {
            byte[] buffer = new byte[TRANSFER_BUFFER_BYTES];
            int read;
            while ((read = body.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private void renameOnto(DiskShare share, String partial, String target) {
        if (share.fileExists(target)) share.rm(target);
        try (File partialFile = share.openFile(
                partial,
                EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null)) {
            partialFile.rename(target);
        }
    }

    private void writeMetadataSidecar(DiskShare share, String target, ObjectMetadata metadata) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(MetadataSidecar.from(metadata));
        String meta = target + META_SUFFIX;
        try (File file = openForWrite(share, meta);
                OutputStream out = file.getOutputStream()) {
            out.write(bytes);
        }
    }

    private ObjectMetadata readMetadataSidecar(DiskShare share, String target) {
        String meta = target + META_SUFFIX;
        if (!share.fileExists(meta)) return ObjectMetadata.of("application/octet-stream");
        try (File file = openForRead(share, meta);
                InputStream in = file.getInputStream()) {
            return objectMapper
                    .readValue(in.readAllBytes(), MetadataSidecar.class)
                    .toObjectMetadata();
        } catch (IOException | SMBApiException e) {
            log.warn("Failed to read SMB metadata sidecar {}", meta, e);
            return ObjectMetadata.of("application/octet-stream");
        }
    }

    private void walk(DiskShare share, String absoluteDir, String relativePrefix, List<String> out) {
        for (FileIdBothDirectoryInformation entry : share.list(absoluteDir)) {
            String name = entry.getFileName();
            if (name.equals(".") || name.equals("..")) continue;
            String childAbsolute = absoluteDir + "\\" + name;
            String childRelative = relativePrefix.isEmpty() ? name : relativePrefix + "/" + name;
            boolean isDir = (entry.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
            if (isDir) {
                walk(share, childAbsolute, childRelative, out);
                continue;
            }
            if (childRelative.endsWith(META_SUFFIX)) continue;
            if (childRelative.contains(".partial.")) continue;
            out.add(childRelative);
        }
    }

    private void safeDelete(DiskShare share, String path) {
        try {
            if (share.fileExists(path)) share.rm(path);
        } catch (SMBApiException e) {
            log.warn("SMB delete failed for {}", path, e);
        }
    }

    private void pruneEmptyParents(DiskShare share, String target) {
        int slash = target.lastIndexOf('\\');
        while (slash > 0) {
            String parent = target.substring(0, slash);
            if (parent.equals(basePath)) return;
            try {
                if (!share.folderExists(parent) || share.list(parent).size() > 2) return;
                share.rmdir(parent, false);
            } catch (SMBApiException e) {
                return;
            }
            slash = parent.lastIndexOf('\\');
            target = parent;
        }
    }

    /**
     * {@link InputStream} that holds the underlying {@link File} open and closes it together
     * with the stream. smbj's {@code File.getInputStream()} only wraps the share-side handle;
     * closing the stream without closing the file would leak the open handle.
     */
    private static final class SmbReadStream extends InputStream {
        private final File file;
        private final InputStream delegate;

        private SmbReadStream(File file) {
            this.file = file;
            this.delegate = file.getInputStream();
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                file.close();
            }
        }
    }
}
