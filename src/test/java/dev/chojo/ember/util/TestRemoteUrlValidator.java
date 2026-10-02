/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;

/**
 * Test helper producing a {@link RemoteUrlValidator} that accepts every URL, so
 * transfer/import tests can point at loopback test servers without the production
 * private-address deny-list rejecting them.
 */
public final class TestRemoteUrlValidator {
    private TestRemoteUrlValidator() {}

    public static RemoteUrlValidator permissive() {
        return new RemoteUrlValidator(null, null) {
            @Override
            public boolean isAllowed(String url) {
                return true;
            }

            @Override
            public boolean permitsPrivateHosts() {
                return true;
            }
        };
    }

    /** An outbound gateway that reaches loopback test servers, as a development instance does. */
    public static OutboundHttp permissiveOutbound() {
        return new OutboundHttp(permissive());
    }
}
