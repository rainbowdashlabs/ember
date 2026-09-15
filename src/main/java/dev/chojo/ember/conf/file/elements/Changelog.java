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
 * Configuration for what the instance says about its own changelog.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("CHANGELOG")
public class Changelog {

    /**
     * Whether an instance writes a system entry after it has been lifted to a new version.
     *
     * <p>Switched off, the changelog is still there to read; nothing is written into the news of
     * every station. That is the setting for an operator who tells their people about updates
     * themselves and would rather Ember did not do it for them.
     */
    @Overwrite(env = @Env)
    private boolean announceUpdates = true;

    public boolean announceUpdates() {
        return announceUpdates;
    }

    @Override
    public String toString() {
        return "Changelog{announceUpdates=" + announceUpdates + '}';
    }
}
