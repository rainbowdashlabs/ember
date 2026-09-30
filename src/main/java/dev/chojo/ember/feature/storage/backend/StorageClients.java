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
 * The protocol clients every remote backend of the process shares.
 *
 * <p>One SSH client, whose I/O threads carry the sessions of every SFTP backend, instead of a client
 * and its threads per backend; and one SMB client per combination of encryption and DFS, the two
 * settings smbj fixes per client rather than per connection. Backends only open sessions on them and
 * close those, never the clients; the clients are closed when the process stops.
 *
 * <p>SMB waits at most {@link #IO_TIMEOUT} for an answer and for the socket. The SSH side sets its
 * own limits in {@link SftpSessions#newClient()}.
 */
@Singleton
public class StorageClients implements AutoCloseable {
    /** How long a remote call waits for its answer. */
    public static final Duration IO_TIMEOUT = Duration.ofSeconds(30);

    private final Map<SmbKey, SMBClient> smbClients = new HashMap<>();
    private final Executor drainer;
    private SshClient ssh;
    private boolean closed;

    /**
     * Clients whose backends drain their pools on the task scheduler's workers.
     *
     * @param scheduler the scheduler of the process
     */
    @Inject
    public StorageClients(TaskScheduler scheduler) {
        this(scheduler.executor());
    }

    /**
     * Clients whose backends drain their pools on the given executor; a direct one drains while the
     * backend is being closed.
     *
     * @param drainer where a closed pool waits for its lent connections
     */
    public StorageClients(Executor drainer) {
        this.drainer = drainer;
    }

    /** Where a closed pool of these clients' backends waits for the connections still lent. */
    public Executor drainer() {
        return drainer;
    }

    /**
     * The shared SSH client, started on first use.
     *
     * @return the client
     */
    public synchronized SshClient ssh() {
        if (closed) throw new StorageUnavailableException("Storage clients have been closed");
        if (ssh == null) ssh = SftpSessions.newClient();
        return ssh;
    }

    /**
     * The shared SMB client for one combination of settings.
     *
     * @param seal whether SMB3 in-flight encryption is on
     * @param dfs  whether DFS referrals are followed
     * @return the client
     */
    public synchronized SMBClient smb(boolean seal, boolean dfs) {
        if (closed) throw new StorageUnavailableException("Storage clients have been closed");
        return smbClients.computeIfAbsent(new SmbKey(seal, dfs), key -> newSmbClient(seal, dfs));
    }

    /**
     * An SMB client with the timeouts every backend uses.
     *
     * @param seal whether SMB3 in-flight encryption is on
     * @param dfs  whether DFS referrals are followed
     * @return the client
     */
    public static SMBClient newSmbClient(boolean seal, boolean dfs) {
        return new SMBClient(SmbConfig.builder()
                .withEncryptData(seal)
                .withDfsEnabled(dfs)
                .withTimeout(IO_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .withSoTimeout(IO_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .build());
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
