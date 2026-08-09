/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The versioned federation contract of one instance: a hash for the core envelope plus one
 * hash per supported feature surface, keyed by capability name. Exchanged in the handshake
 * and the version ping, and stored per partner as the last vector that partner presented.
 * <p>
 * Feature keys are plain strings rather than {@link CapabilityType} constants so a vector
 * from a peer that already knows capabilities this build does not still parses; unknown
 * keys simply never match a local surface.
 *
 * @param core     hash of the core contract surface
 * @param features hash per feature surface, keyed by {@link CapabilityType} name
 */
public record FederationContract(String core, Map<String, String> features) {

    private static final Logger log = LoggerFactory.getLogger(FederationContract.class);

    /**
     * Normalises an absent feature map to an empty one. A peer is free to send a vector
     * without the key, and the stored column predates any given surface, so every read path
     * would otherwise have to guard the map itself.
     */
    public FederationContract {
        features = features != null ? features : Map.of();
    }

    /**
     * Parses a stored or received contract vector, returning {@code null} for blank or
     * unparsable input — an unknown vector, which compatibility checks treat as incompatible.
     */
    public static FederationContract fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return Json.CONFIG_MAPPER.readValue(json, FederationContract.class);
        } catch (Exception e) {
            log.warn("Discarding unparsable federation contract vector: {}", e.getMessage());
            return null;
        }
    }

    public String toJson() {
        return Json.CONFIG_MAPPER.writeValueAsString(this);
    }

    /**
     * The hash this contract carries for the given capability's surface, or {@code null}
     * when the capability is not supported.
     */
    public String featureHash(CapabilityType capability) {
        return features.get(capability.name());
    }
}
