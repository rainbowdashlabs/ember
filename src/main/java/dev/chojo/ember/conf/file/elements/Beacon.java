/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

/**
 * Reporting to a beacon, and being one.
 *
 * <p>A beacon is an instance other instances report to: the faults they ran into, and once a day a
 * bucketed account of how much they hold. Every switch here is off until somebody turns it on, and
 * with {@link #enabled()} off nothing leaves the instance at all, whatever the rest of this says.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("BEACON")
public class Beacon {

    /**
     * Whether this instance reports to a beacon at all.
     *
     * <p>The switch everything else hangs from. Off, nothing is sent and no schedule runs, which is
     * the right setting for an installation that would rather not talk to anybody.
     */
    @Overwrite(env = @Env)
    private boolean enabled = false;

    /**
     * The beacon this instance reports to.
     */
    @Overwrite(env = @Env)
    private String url = "https://ember-panel.de";

    /**
     * Whether every new entry in the error log is forwarded as it appears.
     *
     * <p>Separate from {@link #forwardReports()} because the two are different things to agree to: a
     * stacktrace is the machine talking, a problem report is a person.
     */
    @Overwrite(env = @Env)
    private boolean forwardProblems = false;

    /**
     * Whether every problem report somebody writes is forwarded as it arrives.
     *
     * <p>Off by default and worth leaving off unless the operator has thought about it. What is sent
     * carries no member's name, but it does carry their words, and people write things about
     * themselves in a box that asks what went wrong.
     */
    @Overwrite(env = @Env)
    private boolean forwardReports = false;

    /**
     * Whether the daily count of what this instance holds is sent.
     *
     * <p>The counts are bucketed before they leave and carry no name, no address and no federation
     * identity: a station appears as a metrics identifier and nothing else.
     */
    @Overwrite(env = @Env)
    private boolean metricsEnabled = false;

    /**
     * Whether this instance accepts reports from others, which is what makes it a beacon.
     *
     * <p>Off by default, so an instance is never a beacon by accident.
     */
    @Overwrite(env = @Env)
    private boolean receiving = false;

    /**
     * A name to answer on, travelling with every report.
     *
     * <p>The operator's own, never a member's. Optional: an instance that gives none still reports
     * and is simply one nobody can write to.
     */
    @Overwrite(env = @Env)
    private String contactName = "";

    /**
     * An address to answer on. The operator's own, and optional, as {@link #contactName()} is.
     */
    @Overwrite(env = @Env)
    private String contactMail = "";

    public boolean enabled() {
        return enabled;
    }

    public String url() {
        return url;
    }

    public boolean forwardProblems() {
        return enabled && forwardProblems;
    }

    public boolean forwardReports() {
        return enabled && forwardReports;
    }

    public boolean metricsEnabled() {
        return enabled && metricsEnabled;
    }

    public boolean receiving() {
        return receiving;
    }

    public String contactName() {
        return contactName;
    }

    public String contactMail() {
        return contactMail;
    }
}
