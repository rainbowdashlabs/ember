/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("THEMING")
public class Theming {
    @Overwrite(env = @Env)
    private String defaultTheme = "ember";

    @Overwrite(env = @Env)
    private ThemeFeel defaultFeel = ThemeFeel.ROUNDED;

    @Overwrite(env = @Env)
    private boolean lockFeel = false;

    public String defaultTheme() {
        return defaultTheme;
    }

    public ThemeFeel defaultFeel() {
        return defaultFeel;
    }

    public boolean lockFeel() {
        return lockFeel;
    }
}
