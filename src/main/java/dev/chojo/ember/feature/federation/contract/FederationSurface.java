/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.contract;

import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.station.entity.StationModule;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A versioned slice of the federation API contract. {@link #CORE} is the envelope every
 * feature rides on - handshake, version ping, webhook registration, host-change
 * announcements, sync polling and the request signing scheme. Every other surface maps
 * one-to-one onto a {@link CapabilityType}, so per-feature compatibility can be joined
 * directly onto the capability toggles a partnership already carries.
 */
public enum FederationSurface {
    CORE,
    KB_SHARE(CapabilityType.KB_SHARE),
    QUIZ_SHARE(CapabilityType.QUIZ_SHARE),
    PROTOCOL_SHARE(CapabilityType.PROTOCOL_SHARE),
    INVENTORY_LEND(CapabilityType.INVENTORY_LEND),
    EVENT_SHARE(CapabilityType.EVENT_SHARE),
    BOARD_SHARE(CapabilityType.BOARD_SHARE),
    NEWS_SHARE(CapabilityType.NEWS_SHARE);

    private final @Nullable CapabilityType capability;

    FederationSurface() {
        this.capability = null;
    }

    FederationSurface(CapabilityType capability) {
        this.capability = capability;
    }

    /**
     * The capability this surface versions, or {@code null} for {@link #CORE}.
     */
    public @Nullable CapabilityType capability() {
        return capability;
    }

    /**
     * The module of the serving station a surface shares from. A station that switched the module off
     * serves nothing of it to its partners.
     *
     * @return the module, empty for {@link #CORE}
     */
    public Optional<StationModule> module() {
        return Optional.ofNullable(
                switch (this) {
                    case CORE -> null;
                    case KB_SHARE -> StationModule.KNOWLEDGE_BASE;
                    case QUIZ_SHARE -> StationModule.QUIZ;
                    case PROTOCOL_SHARE -> StationModule.TEST_PROTOCOL;
                    case INVENTORY_LEND -> StationModule.INVENTORY_LENDING;
                    case EVENT_SHARE -> StationModule.EVENTS;
                    case BOARD_SHARE -> StationModule.BOARDS;
                    case NEWS_SHARE -> StationModule.NEWS;
                });
    }

    /**
     * The capability a feature surface versions.
     *
     * @return the capability
     * @throws IllegalStateException for {@link #CORE}, which versions none
     */
    public CapabilityType requireCapability() {
        if (capability == null) throw new IllegalStateException(name() + " versions no capability");
        return capability;
    }
}
