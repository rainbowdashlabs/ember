/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import dev.chojo.ember.conf.file.elements.HibpSettings;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BreachCheckWorkerTest {

    private static HibpSettings settings(boolean enabled, int staleDays) {
        return new HibpSettings() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public int staleAfterDays() {
                return staleDays;
            }
        };
    }

    private static BreachCheckWorker worker(HibpClient hibp, AccountRepository repo, HibpSettings cfg) {
        return new BreachCheckWorker(hibp, repo, cfg, new SynchronousExecutor());
    }

    @Test
    void pwnedAndStaleSetsForcePasswordChange() {
        var hibp = mock(HibpClient.class);
        var repo = mock(AccountRepository.class);
        when(hibp.isPwned(anyString())).thenReturn(true);
        when(repo.findCredential(eq(42))).thenReturn(Optional.of(new AccountCredential(42, "hash", false, null, null)));

        worker(hibp, repo, settings(true, 30)).enqueueCheck(42, "long-enough-passphrase");

        verify(repo).updateLastBreachCheck(eq(42), ArgumentMatchers.any(Instant.class), eq(true));
    }

    @Test
    void notPwnedJustUpdatesTimestamp() {
        var hibp = mock(HibpClient.class);
        var repo = mock(AccountRepository.class);
        when(hibp.isPwned(anyString())).thenReturn(false);
        when(repo.findCredential(eq(11))).thenReturn(Optional.of(new AccountCredential(11, "hash", false, null, null)));

        worker(hibp, repo, settings(true, 30)).enqueueCheck(11, "long-enough-passphrase");

        verify(repo).updateLastBreachCheck(eq(11), ArgumentMatchers.any(Instant.class), eq(false));
    }

    @Test
    void freshCheckIsSkipped() {
        var hibp = mock(HibpClient.class);
        var repo = mock(AccountRepository.class);
        var recent = Instant.now().minusSeconds(60);
        when(repo.findCredential(eq(7))).thenReturn(Optional.of(new AccountCredential(7, "hash", false, recent, null)));

        worker(hibp, repo, settings(true, 30)).enqueueCheck(7, "long-enough-passphrase");

        verify(hibp, never()).isPwned(anyString());
        verify(repo, never()).updateLastBreachCheck(anyInt(), ArgumentMatchers.any(), anyBoolean());
    }

    @Test
    void disabledIsNoop() {
        var hibp = mock(HibpClient.class);
        var repo = mock(AccountRepository.class);

        worker(hibp, repo, settings(false, 30)).enqueueCheck(1, "long-enough-passphrase");

        verify(hibp, never()).isPwned(anyString());
        verify(repo, never()).findCredential(anyInt());
    }

    @Test
    void nullPlaintextIsNoop() {
        var hibp = mock(HibpClient.class);
        var repo = mock(AccountRepository.class);

        worker(hibp, repo, settings(true, 30)).enqueueCheck(1, null);

        verify(hibp, never()).isPwned(anyString());
    }

    @Test
    void missingCredentialIsTolerated() {
        var hibp = mock(HibpClient.class);
        var repo = mock(AccountRepository.class);
        when(repo.findCredential(eq(99))).thenReturn(Optional.empty());

        worker(hibp, repo, settings(true, 30)).enqueueCheck(99, "long-enough-passphrase");

        verify(hibp, never()).isPwned(anyString());
        verify(repo, never()).updateLastBreachCheck(anyInt(), ArgumentMatchers.any(), anyBoolean());
    }

    /**
     * Runs submitted tasks on the calling thread so the test can assert the
     * worker's effects synchronously without sleeping.
     */
    private static final class SynchronousExecutor extends AbstractExecutorService {
        private volatile boolean shutdown = false;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
