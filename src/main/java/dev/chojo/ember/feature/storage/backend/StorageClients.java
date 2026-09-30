/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import dev.chojo.ember.feature.storage.backend.sftp.SftpSessions;
import dev.chojo.ember.lifecycle.TaskScheduler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.sshd.client.SshClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * The protocol clients every remote backend of the process shares: one SSH client, whose I/O threads
 * carry every SFTP session, and one SMB client per combination of encryption and DFS, which smbj fixes
 * per client. Backends open and close sessions on them; the clients close when the process stops.
 */
@Singleton
public class StorageClients implements AutoCloseable {
    /** How long an SMB call waits for its answer and for the socket. */
    public static final Duration IO_TIMEOUT = Duration.ofSeconds(30);

    private final Map<SmbKey, SMBClient> smbClients = new HashMap<>();
    private final Executor drainer;
    private SshClient ssh;
    private boolean closed;

    @Inject
    public StorageClients(TaskScheduler scheduler) {
        this(scheduler.executor());
    }

    /** @param drainer where a closed pool waits for its lent connections; a direct one waits in the caller */
    public StorageClients(Executor drainer) {
        this.drainer = drainer;
    }

    /** Where a closed pool of these clients' backends waits for the connections still lent. */
    public Executor drainer() {
        return drainer;
    }

    /** The shared SSH client, started on first use. */
    public synchronized SshClient ssh() {
        if (closed) throw new StorageUnavailableException("Storage clients have been closed");
        if (ssh == null) ssh = SftpSessions.newClient();
        return ssh;
    }

    /** The shared SMB client for one combination of encryption and DFS. */
    public synchronized SMBClient smb(boolean seal, boolean dfs) {
        if (closed) throw new StorageUnavailableException("Storage clients have been closed");
        return smbClients.computeIfAbsent(
                new SmbKey(seal, dfs),
                key -> new SMBClient(SmbConfig.builder()
                        .withEncryptData(seal)
                        .withDfsEnabled(dfs)
                        .withTimeout(IO_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                        .withSoTimeout(IO_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                        .build()));
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (ssh != null) ssh.stop();
        smbClients.values().forEach(SMBClient::close);
        smbClients.clear();
    }

    private record SmbKey(boolean seal, boolean dfs) {}
}
