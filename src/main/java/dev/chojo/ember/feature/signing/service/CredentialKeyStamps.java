/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.twofactor.entity.CredentialKeyStamp;
import dev.chojo.ember.feature.twofactor.entity.KeyStampKind;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnCredential;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Timestamps the public key of every WebAuthn credential, so a signing act's evidence can show that the
 * key it verifies under existed before the act and was not put in place afterwards.
 *
 * <p>The timestamp is an RFC 3161 token over the SHA-256 of the public key, COSE encoded exactly as it is
 * stored, from the services {@link TimestampServices} lists. A credential keeps the first stamp it gets,
 * marked with how it got it ({@link KeyStampKind}):
 *
 * <ul>
 *   <li>right after a registration, on a background thread, so the registration neither waits for a
 *       timestamp service nor fails when none answers;
 *   <li>by a daily retry for credentials still without one, because no service answered at the
 *       registration, timestamps were off then, or the credential is older than key timestamps. Such a
 *       stamp proves the key only from its own time on, and says so;
 *   <li>during a signing act with a credential that has none yet, before the evidence is recorded.
 * </ul>
 *
 * <p>Nothing here blocks or refuses: a stamp that cannot be had is logged once and left for the next
 * chance.
 */
@Singleton
public class CredentialKeyStamps implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(CredentialKeyStamps.class);
    private static final Duration START_DELAY = Duration.ofMinutes(15);
    private static final Duration INTERVAL = Duration.ofDays(1);

    /** How many credentials one retry stamps at most. */
    static final int MAX_PER_RUN = 50;

    /**
     * How old a registration has to be before the retry takes it, so it never races the stamp the
     * registration itself is still waiting for.
     */
    static final Duration SETTLED = Duration.ofMinutes(10);

    private final TimestampServices timestamps;
    private final TwoFactorRepository credentials;
    private final TaskScheduler scheduler;
    private final Clock clock;

    @Inject
    public CredentialKeyStamps(TimestampServices timestamps, TwoFactorRepository credentials, TaskScheduler scheduler) {
        this(timestamps, credentials, scheduler, Clock.systemUTC());
    }

    CredentialKeyStamps(
            TimestampServices timestamps, TwoFactorRepository credentials, TaskScheduler scheduler, Clock clock) {
        this.timestamps = timestamps;
        this.credentials = credentials;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /**
     * Stamps a credential just registered, on a background thread. Call it once the credential is stored
     * and nothing holds a transaction open for it.
     *
     * @param factorId the new credential's factor
     */
    public void afterRegistration(int factorId) {
        if (!timestamps.enabled()) return;
        scheduler.background("credential-key-stamp", () -> stampRegistered(factorId));
    }

    /**
     * Stamps a credential just registered, if it has no stamp yet.
     *
     * @param factorId the credential's factor
     */
    void stampRegistered(int factorId) {
        credentials
                .findWebAuthnByFactorId(factorId)
                .filter(credential -> credential.keyStamp() == null)
                .ifPresent(credential -> stamp(credential, KeyStampKind.AT_REGISTRATION));
    }

    /**
     * The stamp a signing act with the credential records: the one it has, or, when it has none yet, one
     * obtained now and kept for the credential.
     *
     * @param credential the credential that answered the signing challenge, as it was read for the act
     * @return the stamp, or null when the credential has none and no timestamp service gave one now
     */
    public @Nullable CredentialKeyStamp forSigning(WebAuthnCredential credential) {
        CredentialKeyStamp held = credential.keyStamp();
        if (held != null) return held;
        return stamp(credential, KeyStampKind.AT_FIRST_SIGNING).orElse(null);
    }

    /**
     * Stamps credentials still without a stamp, the oldest first, at most {@link #MAX_PER_RUN}, and stops
     * at the first one no service stamps, since the next would fail the same way.
     */
    void stampLate() {
        if (!timestamps.enabled()) return;
        var due = credentials.findUnstampedWebAuthn(clock.instant().minus(SETTLED), MAX_PER_RUN);
        int stamped = 0;
        for (WebAuthnCredential credential : due) {
            if (stamp(credential, KeyStampKind.AFTER_REGISTRATION).isEmpty()) break;
            stamped++;
        }
        if (stamped > 0) log.info("Stamped the public keys of {} credentials after their registration", stamped);
    }

    /**
     * Obtains a stamp for the credential's key and keeps it, unless another stamp was kept meanwhile, which
     * is then the one returned.
     */
    private Optional<CredentialKeyStamp> stamp(WebAuthnCredential credential, KeyStampKind kind) {
        int factorId = credential.factorId();
        var obtained = timestamps.stamp(Sha256.digest().digest(credential.publicKeyCose()));
        if (obtained.isEmpty()) {
            log.warn("No timestamp service stamped the public key of credential {} ({})", factorId, kind);
            return Optional.empty();
        }
        var stamp = new CredentialKeyStamp(
                obtained.get().token(), obtained.get().time(), obtained.get().service(), kind);
        if (credentials.recordKeyStamp(factorId, stamp)) return Optional.of(stamp);
        return credentials.findWebAuthnByFactorId(factorId).map(WebAuthnCredential::keyStamp);
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "credential-key-stamps", Schedule.fixedDelay(START_DELAY, INTERVAL), this::stampLate));
    }
}
