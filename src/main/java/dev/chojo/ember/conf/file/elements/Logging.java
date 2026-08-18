/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

/**
 * Where the log goes and how long it is kept.
 *
 * <p>The console always gets everything and is not configurable, because the failure the database
 * cannot cover is the database being the thing that broke. What is chosen here is the copy that goes
 * into the database, where it can be read and searched from the administration area.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class Logging {

    /**
     * Whether log lines are also written to the database. Off by default: it is somebody's decision
     * to keep the log where it can be read from a browser, not something to inherit by upgrading.
     */
    @Overwrite(env = @Env)
    private boolean databaseEnabled = false;

    /**
     * The lowest severity written to the database. Everything below still reaches console and file.
     */
    @Overwrite(env = @Env)
    private String databaseLevel = "DEBUG";

    /**
     * How many days of log to keep in the database. Older lines are removed hourly.
     */
    @Overwrite(env = @Env)
    private int retentionDays = 14;

    public boolean databaseEnabled() {
        return databaseEnabled;
    }

    public String databaseLevel() {
        return databaseLevel == null || databaseLevel.isBlank() ? "DEBUG" : databaseLevel;
    }

    public int retentionDays() {
        return Math.max(1, retentionDays);
    }
}
