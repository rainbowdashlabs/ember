/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * What this instance sends to a beacon, and whether it is one.
 *
 * <p>Stored settings rather than file configuration, so an operator can change their mind without a
 * restart and without reaching the file the container runs from. Every one of them is read at the
 * moment it matters rather than at boot: turning forwarding off has to stop the next entry going,
 * not the next deployment.
 *
 * <p>Everything is off until somebody sets it. An instance that has never been configured reports
 * nowhere and accepts nothing.
 */
@Singleton
public class BeaconSettings {

    public static final String ENABLED = "beacon_enabled";
    public static final String URL = "beacon_url";
    public static final String FORWARD_PROBLEMS = "beacon_forward_problems";
    public static final String FORWARD_REPORTS = "beacon_forward_reports";
    public static final String METRICS_ENABLED = "beacon_metrics_enabled";
    public static final String RECEIVING = "beacon_receiving";
    public static final String CONTACT_NAME = "beacon_contact_name";
    public static final String CONTACT_MAIL = "beacon_contact_mail";

    /** Where an instance reports when nobody has said otherwise. */
    public static final String DEFAULT_URL = "https://ember-panel.de";

    private final ApplicationSettingRepository settings;

    @Inject
    public BeaconSettings(ApplicationSettingRepository settings) {
        this.settings = settings;
    }

    /**
     * Whether this instance reports at all.
     *
     * <p>The switch every sending switch below hangs from, so that turning reporting off is one
     * action rather than four.
     */
    public boolean enabled() {
        return settings.getBoolean(ENABLED, false);
    }

    public String url() {
        return settings.get(URL).filter(value -> !value.isBlank()).orElse(DEFAULT_URL);
    }

    public boolean forwardProblems() {
        return enabled() && settings.getBoolean(FORWARD_PROBLEMS, false);
    }

    /**
     * Whether problem reports somebody wrote are forwarded as they arrive.
     *
     * <p>Apart from {@link #forwardProblems()} because the two are different things to agree to: a
     * stacktrace is the machine talking, a problem report is a person.
     */
    public boolean forwardReports() {
        return enabled() && settings.getBoolean(FORWARD_REPORTS, false);
    }

    public boolean metricsEnabled() {
        return enabled() && settings.getBoolean(METRICS_ENABLED, false);
    }

    /** Whether this instance accepts reports from others, which is what makes it a beacon. */
    public boolean receiving() {
        return settings.getBoolean(RECEIVING, false);
    }

    /** A name a beacon may answer on. The operator's own, never a member's. */
    public String contactName() {
        return settings.get(CONTACT_NAME).orElse("");
    }

    /** An address a beacon may answer on. The operator's own, never a member's. */
    public String contactMail() {
        return settings.get(CONTACT_MAIL).orElse("");
    }

    /**
     * Writes what an operator chose.
     *
     * <p>The address is left alone when it arrives empty, so clearing the box in the screen falls
     * back to the default beacon rather than to nowhere at all.
     */
    public void update(
            boolean enabled,
            String url,
            boolean forwardProblems,
            boolean forwardReports,
            boolean metricsEnabled,
            boolean receiving,
            String contactName,
            String contactMail) {
        settings.setBoolean(ENABLED, enabled);
        settings.set(URL, url == null || url.isBlank() ? DEFAULT_URL : url.strip());
        settings.setBoolean(FORWARD_PROBLEMS, forwardProblems);
        settings.setBoolean(FORWARD_REPORTS, forwardReports);
        settings.setBoolean(METRICS_ENABLED, metricsEnabled);
        settings.setBoolean(RECEIVING, receiving);
        settings.set(CONTACT_NAME, contactName == null ? "" : contactName.strip());
        settings.set(CONTACT_MAIL, contactMail == null ? "" : contactMail.strip());
    }
}
