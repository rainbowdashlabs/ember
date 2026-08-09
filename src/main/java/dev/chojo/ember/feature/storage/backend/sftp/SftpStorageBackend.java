/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.sftp;

import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.MetadataSidecar;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.util.Json;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SFTP-backed {@link StorageBackend}. Talks to the server through Apache MINA SSHD — no
 * kernel mount, no FUSE, no host-level SSH client required.
 *
 * <p>In-flight encryption is the SSH channel itself and is always on. Host-key verification
 * uses the supplied {@code knownHostsFingerprint}; an empty fingerprint disables verification
 * and is acceptable only in dev.
 *
 * <p>Atomicity matches the other backends: payloads write to {@code <key>.partial.<uuid>},
 * then rename onto the final path on success. Metadata is persisted as a sibling
 * {@code <key>.meta.json} in the same JSON shape so a future migration tool can move bytes
 * across backends without re-deriving metadata.
 */
public class SftpStorageBackend implements StorageBackend, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SftpStorageBackend.class);
    private static final String META_SUFFIX = ".meta.json";
    private static final String PROBE_PREFIX = "_probe";
    private static final int TRANSFER_BUFFER_BYTES = 8 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(15);

    private final SftpBackendConfig config;
    private final SshClient sshClient;
    private final String basePath;

    private ClientSession session;
    private SftpClient sftp;

    public SftpStorageBackend(SftpBackendConfig config) {
        this(config, defaultClient(config));
    }

    /**
     * Visible for tests that want to inject a pre-configured {@link SshClient}.
     */
    SftpStorageBackend(SftpBackendConfig config, SshClient sshClient) {
        this.config = config;
        this.sshClient = sshClient;
        this.basePath = normalizeBasePath(config.basePath());
        this.sshClient.start();
    }

    /**
     * Returns the public key fingerprint computed by smbj's {@link KeyUtils} for the supplied
     * key. Used by callers wiring host-key verification to compare against a stored fingerprint.
     */
    public static String fingerprintOf(PublicKey key) {
        return KeyUtils.getFingerPrint(key);
    }

    private static SshClient defaultClient(SftpBackendConfig config) {
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((_, _, serverKey) -> {
            if (config.trustsAnyHost()) return true;
            try {
                String fingerprint = KeyUtils.getFingerPrint(serverKey);
                return config.knownHostsFingerprint().equalsIgnoreCase(fingerprint);
            } catch (Exception e) {
                log.warn("Failed to verify SFTP host key", e);
                return false;
            }
        });
        return client;
    }

    private static String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isBlank() || basePath.equals("/")) return "";
        String trimmed = basePath;
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (!trimmed.startsWith("/")) trimmed = "/" + trimmed;
        return trimmed;
    }

    private static KeyPair parsePrivateKey(String pem) {
        try {
            var loader = SecurityUtils.getKeyPairResourceParser();
            var keys = loader.loadKeyPairs(
                    null, null, null, new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            var it = keys.iterator();
            if (!it.hasNext()) throw new StorageException("SFTP private key PEM is empty");
            return it.next();
        } catch (IOException | GeneralSecurityException e) {
            throw new StorageException("Failed to parse SFTP private key", e);
        }
    }

    @Override
    public StorageBackendType type() {
        return StorageBackendType.SFTP;
    }

    @Override
    public synchronized void store(String fullKey, InputStream body, long contentLength, ObjectMetadata metadata) {
        String target = path(fullKey);
        String partial = target + ".partial." + UUID.randomUUID();
        SftpClient sftp = openSftp();
        try {
            ensureParent(sftp, target);
            try (OutputStream out = sftp.write(
                    partial,
                    EnumSet.of(SftpClient.OpenMode.Create, SftpClient.OpenMode.Write, SftpClient.OpenMode.Truncate))) {
                byte[] buffer = new byte[TRANSFER_BUFFER_BYTES];
                int read;
                while ((read = body.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            safeRemove(sftp, target);
            sftp.rename(partial, target);
            writeMetadataSidecar(sftp, target, metadata);
        } catch (IOException e) {
            safeRemove(sftp, partial);
            throw new StorageException("SFTP store failed for " + fullKey, e);
        }
    }

    @Override
    public synchronized void updateMetadata(String fullKey, ObjectMetadata metadata) {
        SftpClient sftp = openSftp();
        String target = path(fullKey);
        if (!fileExists(sftp, target)) {
            throw new StorageException("Cannot update metadata; missing object " + fullKey);
        }
        try {
            writeMetadataSidecar(sftp, target, metadata);
        } catch (IOException e) {
            throw new StorageException("SFTP updateMetadata failed for " + fullKey, e);
        }
    }

    @Override
    public synchronized Optional<StoredStream> read(String fullKey) {
        SftpClient sftp = openSftp();
        String target = path(fullKey);
        if (!fileExists(sftp, target)) return Optional.empty();
        try {
            long size = sftp.stat(target).getSize();
            ObjectMetadata metadata = readMetadataSidecar(sftp, target);
            InputStream stream = sftp.read(target);
            return Optional.of(new StoredStream(stream, size, metadata));
        } catch (IOException e) {
            throw new StorageException("SFTP read failed for " + fullKey, e);
        }
    }

    @Override
    public synchronized void delete(String fullKey) {
        SftpClient sftp = openSftp();
        String target = path(fullKey);
        safeRemove(sftp, target);
        safeRemove(sftp, target + META_SUFFIX);
        pruneEmptyParents(sftp, target);
    }

    @Override
    public synchronized boolean exists(String fullKey) {
        return fileExists(openSftp(), path(fullKey));
    }

    @Override
    public synchronized List<String> listByPrefix(String prefix) {
        SftpClient sftp = openSftp();
        var out = new ArrayList<String>();
        String rooted = prefix == null ? "" : prefix;
        String full = rooted.isEmpty() ? (basePath.isEmpty() ? "/" : basePath) : path(rooted);
        if (fileExists(sftp, full)) {
            if (!rooted.isEmpty()) out.add(rooted);
            return out;
        }
        if (!folderExists(sftp, full)) return out;
        walk(sftp, full, rooted, out);
        out.sort(String::compareTo);
        return out;
    }

    @Override
    public synchronized HealthStatus probe() {
        String key = PROBE_PREFIX + "/" + UUID.randomUUID();
        SftpClient sftp = openSftp();
        String target = path(key);
        try {
            ensureParent(sftp, target);
            byte[] payload = "probe".getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = sftp.write(
                    target,
                    EnumSet.of(SftpClient.OpenMode.Create, SftpClient.OpenMode.Write, SftpClient.OpenMode.Truncate))) {
                out.write(payload);
            }
            try (InputStream in = sftp.read(target)) {
                String readBack = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (!"probe".equals(readBack)) {
                    return HealthStatus.unhealthy("SFTP backend read returned unexpected payload");
                }
            }
            return HealthStatus.ok();
        } catch (IOException e) {
            return HealthStatus.unhealthy("SFTP backend probe failed: " + e.getMessage());
        } finally {
            safeRemove(sftp, target);
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (sftp != null) sftp.close();
        } catch (IOException ignored) {
        }
        try {
            if (session != null) session.close();
        } catch (IOException ignored) {
        }
        sshClient.stop();
    }

    private SftpClient openSftp() {
        if (sftp != null && sftp.isOpen()) return sftp;
        try {
            if (session == null || !session.isOpen()) {
                ConnectFuture connect = sshClient.connect(config.username(), config.host(), config.port());
                session = connect.verify(CONNECT_TIMEOUT.toMillis()).getSession();
                if (config.password().isPresent()) {
                    session.addPasswordIdentity(config.password().get());
                } else {
                    session.addPublicKeyIdentity(
                            parsePrivateKey(config.privateKey().get()));
                }
                session.auth().verify(AUTH_TIMEOUT.toMillis());
            }
            sftp = SftpClientFactory.instance().createSftpClient(session);
            return sftp;
        } catch (IOException e) {
            throw new StorageException("SFTP connect failed: " + e.getMessage(), e);
        }
    }

    private String path(String fullKey) {
        if (fullKey == null || fullKey.isEmpty()) {
            throw new IllegalArgumentException("fullKey must not be empty");
        }
        if (basePath.isEmpty()) return fullKey.startsWith("/") ? fullKey : "/" + fullKey;
        return basePath + (fullKey.startsWith("/") ? fullKey : "/" + fullKey);
    }

    private boolean fileExists(SftpClient sftp, String path) {
        try {
            var attrs = sftp.stat(path);
            return attrs.isRegularFile();
        } catch (IOException e) {
            return false;
        }
    }

    private boolean folderExists(SftpClient sftp, String path) {
        try {
            return sftp.stat(path).isDirectory();
        } catch (IOException e) {
            return false;
        }
    }

    private void ensureParent(SftpClient sftp, String target) {
        int slash = target.lastIndexOf('/');
        if (slash <= 0) return;
        createDirectories(sftp, target.substring(0, slash));
    }

    private void createDirectories(SftpClient sftp, String dir) {
        if (dir.isEmpty() || dir.equals("/")) return;
        if (folderExists(sftp, dir)) return;
        int slash = dir.lastIndexOf('/');
        if (slash > 0) createDirectories(sftp, dir.substring(0, slash));
        try {
            sftp.mkdir(dir);
        } catch (IOException e) {
            if (!folderExists(sftp, dir)) {
                throw new StorageException("SFTP mkdir failed for " + dir, e);
            }
        }
    }

    private void writeMetadataSidecar(SftpClient sftp, String target, ObjectMetadata metadata) throws IOException {
        byte[] bytes = Json.MAPPER.writeValueAsBytes(MetadataSidecar.from(metadata));
        String meta = target + META_SUFFIX;
        try (OutputStream out = sftp.write(
                meta,
                EnumSet.of(SftpClient.OpenMode.Create, SftpClient.OpenMode.Write, SftpClient.OpenMode.Truncate))) {
            out.write(bytes);
        }
    }

    private ObjectMetadata readMetadataSidecar(SftpClient sftp, String target) {
        String meta = target + META_SUFFIX;
        if (!fileExists(sftp, meta)) return ObjectMetadata.of("application/octet-stream");
        try (InputStream in = sftp.read(meta)) {
            return Json.MAPPER
                    .readValue(in.readAllBytes(), MetadataSidecar.class)
                    .toObjectMetadata();
        } catch (IOException | JacksonException e) {
            log.warn("Failed to read SFTP metadata sidecar {}", meta, e);
            return ObjectMetadata.of("application/octet-stream");
        }
    }

    private void walk(SftpClient sftp, String absoluteDir, String relativePrefix, List<String> out) {
        try {
            for (SftpClient.DirEntry entry : sftp.readDir(absoluteDir)) {
                String name = entry.getFilename();
                if (name.equals(".") || name.equals("..")) continue;
                String childAbsolute = absoluteDir.endsWith("/") ? absoluteDir + name : absoluteDir + "/" + name;
                String childRelative = relativePrefix.isEmpty() ? name : relativePrefix + "/" + name;
                if (entry.getAttributes().isDirectory()) {
                    walk(sftp, childAbsolute, childRelative, out);
                    continue;
                }
                if (childRelative.endsWith(META_SUFFIX)) continue;
                if (childRelative.contains(".partial.")) continue;
                out.add(childRelative);
            }
        } catch (IOException e) {
            throw new StorageException("SFTP listing failed for " + absoluteDir, e);
        }
    }

    private void safeRemove(SftpClient sftp, String path) {
        try {
            if (fileExists(sftp, path)) sftp.remove(path);
        } catch (IOException e) {
            log.warn("SFTP remove failed for {}", path, e);
        }
    }

    private void pruneEmptyParents(SftpClient sftp, String target) {
        int slash = target.lastIndexOf('/');
        String current = target;
        while (slash > 0) {
            String parent = current.substring(0, slash);
            if (parent.equals(basePath) || parent.isEmpty() || parent.equals("/")) return;
            try {
                boolean empty = true;
                for (SftpClient.DirEntry entry : sftp.readDir(parent)) {
                    String name = entry.getFilename();
                    if (!name.equals(".") && !name.equals("..")) {
                        empty = false;
                        break;
                    }
                }
                if (!empty) return;
                sftp.rmdir(parent);
            } catch (IOException e) {
                return;
            }
            current = parent;
            slash = parent.lastIndexOf('/');
        }
    }
}
