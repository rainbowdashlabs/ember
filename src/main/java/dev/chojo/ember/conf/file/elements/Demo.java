/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

public class Demo {
    @Overwrite(env = @Env)
    private boolean enabled = false;

    @Overwrite(env = @Env)
    private boolean dev = false;

    @Overwrite(env = @Env)
    private int resetIntervalHours = 1;

    public boolean enabled() {
        return enabled;
    }

    public boolean dev() {
        return dev;
    }

    public int resetIntervalHours() {
        return resetIntervalHours;
    }
}
