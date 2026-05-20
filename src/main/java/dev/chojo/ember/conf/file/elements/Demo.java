/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

/**
 * Demo mode configuration controlling whether demo mode is active, the data reset interval,
 * and whether dev-mode features are enabled.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
public class Demo {
    @Overwrite(env = @Env)
    private boolean dev = false;

    @Overwrite(env = @Env)
    private int resetIntervalHours = 1;

    @Overwrite(env = @Env)
    private boolean enabled = false;

    public boolean enabled() {
        return enabled;
    }

    public boolean dev() {
        return dev;
    }

    public int resetIntervalHours() {
        return resetIntervalHours;
    }

    @Override
    public String toString() {
        return "Demo{" + "dev=" + dev + ", resetIntervalHours=" + resetIntervalHours + ", enabled=" + enabled + '}';
    }
}
