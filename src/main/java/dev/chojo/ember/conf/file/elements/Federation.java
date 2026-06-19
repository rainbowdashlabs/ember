/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

/**
 * Federation-layer configuration. The single flag here controls whether the
 * outbound URL validator bypasses its public-internet checks; production
 * deployments leave it disabled, local-dev / CI overrides may enable it so a
 * peer running on {@code localhost} can be reached.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class Federation {
    @Overwrite(env = @Env)
    private boolean allowPrivateHosts = false;

    /**
     * @return {@code true} when {@code RemoteUrlValidator} should permit non-HTTPS
     *         schemes and private / loopback / link-local IP addresses. Intended
     *         for local development only.
     */
    public boolean allowPrivateHosts() {
        return allowPrivateHosts;
    }

    @Override
    public String toString() {
        return "Federation{allowPrivateHosts=" + allowPrivateHosts + '}';
    }
}
