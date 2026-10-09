/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.lifecycle.TaskScheduler;

import java.time.Duration;
import java.util.List;

/** Credential key stamps for tests: switched off, or asking test timestamp services only. */
public final class TestKeyStamps {
    private TestKeyStamps() {}

    /**
     * @param credentials where the credentials are stored
     * @return key stamps with timestamps switched off, which never reach anything
     */
    public static CredentialKeyStamps off(TwoFactorRepository credentials) {
        return new CredentialKeyStamps(timestampsOff(), credentials, new TaskScheduler());
    }

    /** @return timestamp services with timestamps switched off */
    static TimestampServices timestampsOff() {
        return new TimestampServices(List.of(), TimestampServices.TIMEOUT, TimestampServices.BUDGET);
    }

    /**
     * @param services the services to ask, in this order
     * @return timestamp services asking only these, with a short budget
     */
    static TimestampServices asking(TimestampServices.Service... services) {
        return new TimestampServices(List.of(services), Duration.ofSeconds(2), Duration.ofSeconds(4));
    }
}
