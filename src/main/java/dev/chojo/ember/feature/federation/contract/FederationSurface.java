/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.contract;

import dev.chojo.ember.feature.federation.entity.CapabilityType;

/**
 * A versioned slice of the federation API contract. {@link #CORE} is the envelope every
 * feature rides on - handshake, version ping, webhook registration, host-change
 * announcements, sync polling and the request signing scheme. Every other surface maps
 * one-to-one onto a {@link CapabilityType}, so per-feature compatibility can be joined
 * directly onto the capability toggles a partnership already carries.
 */
public enum FederationSurface {
    CORE(null),
    KB_SHARE(CapabilityType.KB_SHARE),
    QUIZ_SHARE(CapabilityType.QUIZ_SHARE),
    PROTOCOL_SHARE(CapabilityType.PROTOCOL_SHARE),
    INVENTORY_LEND(CapabilityType.INVENTORY_LEND),
    EVENT_SHARE(CapabilityType.EVENT_SHARE),
    BOARD_SHARE(CapabilityType.BOARD_SHARE),
    NEWS_SHARE(CapabilityType.NEWS_SHARE);

    private final CapabilityType capability;

    FederationSurface(CapabilityType capability) {
        this.capability = capability;
    }

    /**
     * The capability this surface versions, or {@code null} for {@link #CORE}.
     */
    public CapabilityType capability() {
        return capability;
    }
}
