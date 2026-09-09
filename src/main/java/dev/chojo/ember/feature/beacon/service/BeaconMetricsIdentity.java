/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * The instance's own name in the metrics, and the minute of the day it reports at.
 *
 * <p>Both come from the same secret. The identifier is written by the migration and lives in
 * {@code application_setting}; the reporting minute is derived from it rather than from the instance
 * identity, and that choice is the whole reason this class exists.
 *
 * <p>The instance identity is {@code sha256(publicKey)} and discovery publishes that key. A slot
 * derived from it would be computable by anybody, so the arrival time of a metrics row would name
 * its sender however carefully the payload avoided doing so, permanently and for every row. The
 * metrics identifier is not published, so a slot drawn from it spreads a fleet without saying who is
 * who.
 */
@Singleton
public class BeaconMetricsIdentity {

    /** Where the instance's own metrics identifier lives. */
    public static final String SETTING_KEY = "beacon_metrics_uid";

    private static final int MINUTES_PER_DAY = 24 * 60;

    private final ApplicationSettingRepository settings;

    @Inject
    public BeaconMetricsIdentity(ApplicationSettingRepository settings) {
        this.settings = settings;
    }

    /**
     * The instance's metrics identifier, minted if the row is somehow missing.
     *
     * @return the identifier, never null
     */
    public String instanceMetricsUid() {
        return settings.get(SETTING_KEY).filter(value -> !value.isBlank()).orElseGet(() -> {
            String minted = UUID.randomUUID().toString();
            settings.set(SETTING_KEY, minted);
            return minted;
        });
    }

    /**
     * The minute of the UTC day this instance reports at, the same one every day.
     *
     * <p>Deterministic on purpose. A value redrawn at every start would put an instance that
     * restarts often back into the crowd it is meant to be spread out of, and a fleet brought up
     * together would agree on nothing except that they all just started.
     *
     * @return a minute of the day, from 0 to 1439
     */
    public int dailySlotMinute() {
        return slotFor(instanceMetricsUid());
    }

    /**
     * The slot a given identifier maps to, split out so it can be tested without a database.
     *
     * @param metricsUid the secret identifier the slot is drawn from
     * @return a minute of the day, from 0 to 1439
     */
    public static int slotFor(String metricsUid) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(metricsUid.getBytes(StandardCharsets.UTF_8));
            int value = ((digest[0] & 0xFF) << 8) | (digest[1] & 0xFF);
            return value % MINUTES_PER_DAY;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 not available", e);
        }
    }
}
