/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import java.util.UUID;

/**
 * Everything that has to differ between two stations the demo builds the same way.
 *
 * <p>The seeders behind it are the same seeders and the data is the same data, so what a profile carries is
 * only what two stations cannot share: what they are called, where they answer, and whose address is whose.
 *
 * @param key           short name for the station, used where a stable identifier is wanted in a log or a path
 * @param name          what it is called
 * @param uid           its identity, fixed so a reset writes to the same storage paths as the run before
 * @param publicSlug    where its public pages answer, which no two stations may share
 * @param addressSuffix goes into the domain of everybody seeded here, empty for the first station so its
 *                      people keep the addresses they have always had
 * @param joinsCluster  whether the demo association takes this station as its member station
 */
public record DemoStationProfile(
        String key, String name, UUID uid, String publicSlug, String addressSuffix, boolean joinsCluster) {

    /**
     * The address a person seeded at this station holds.
     *
     * <p>The same person exists at both stations, so the suffix is what keeps them two accounts rather than
     * one collision: {@code max@mustermann.local} and {@code max@mustermann.nord.local}.
     */
    public String address(String firstName, String lastName) {
        return firstName.toLowerCase() + "@" + lastName.toLowerCase() + addressSuffix + ".local";
    }
}
