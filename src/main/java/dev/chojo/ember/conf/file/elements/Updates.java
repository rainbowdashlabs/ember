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
 * Configuration for the check that tells an administrator a newer release exists.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("UPDATES")
public class Updates {

    private static final int MIN_CHECK_INTERVAL_HOURS = 1;
    private static final int MAX_CHECK_INTERVAL_HOURS = 24 * 7;

    /**
     * Whether this instance asks GitHub whether a newer release exists.
     *
     * <p>Switched off, nothing leaves the instance and nobody is told about a new version. That is
     * the right setting for an installation with no outbound access, and for anyone who would
     * rather their instance did not announce itself by asking.
     */
    @Overwrite(env = @Env)
    private boolean enabled = true;

    /**
     * How many hours pass between two checks.
     *
     * <p>Releases arrive weeks apart, so asking often buys nothing and only spends the anonymous
     * rate limit. Read as at least an hour and at most a week.
     */
    @Overwrite(env = @Env)
    private int checkIntervalHours = 6;

    /**
     * The GitHub repository the releases are read from, as {@code owner/name}.
     *
     * <p>Configurable because an operator running their own build from a fork should be told about
     * their own releases rather than somebody else's.
     */
    @Overwrite(env = @Env)
    private String repository = "rainbowdashlabs/ember";

    public boolean enabled() {
        return enabled;
    }

    public int checkIntervalHours() {
        return Math.clamp(checkIntervalHours, MIN_CHECK_INTERVAL_HOURS, MAX_CHECK_INTERVAL_HOURS);
    }

    public String repository() {
        return repository;
    }

    @Override
    public String toString() {
        return "Updates{enabled=" + enabled + ", checkIntervalHours=" + checkIntervalHours() + ", repository="
                + repository + '}';
    }
}
