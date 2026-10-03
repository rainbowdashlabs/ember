/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery;

import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Builds station cards for tests: a card that publishes nothing beyond its identifier and name, with
 * the parts a test cares about set on top.
 */
public final class TestDiscoveryCards {
    private final String stationUid;
    private String name = "Wache";
    private @Nullable String slogan;
    private @Nullable String logoUrl;
    private @Nullable String country = "DE";
    private @Nullable String city;
    private @Nullable String contactUrl;
    private @Nullable String publicSlug;
    private boolean acceptsFederation;

    private TestDiscoveryCards(String stationUid) {
        this.stationUid = stationUid;
    }

    /**
     * A card for the given station.
     *
     * @param stationUid the station's identifier as the card names it
     * @return the builder
     */
    public static TestDiscoveryCards card(String stationUid) {
        return new TestDiscoveryCards(stationUid);
    }

    public TestDiscoveryCards name(String name) {
        this.name = name;
        return this;
    }

    public TestDiscoveryCards slogan(@Nullable String slogan) {
        this.slogan = slogan;
        return this;
    }

    public TestDiscoveryCards logoUrl(@Nullable String logoUrl) {
        this.logoUrl = logoUrl;
        return this;
    }

    public TestDiscoveryCards country(@Nullable String country) {
        this.country = country;
        return this;
    }

    public TestDiscoveryCards city(@Nullable String city) {
        this.city = city;
        return this;
    }

    public TestDiscoveryCards contactUrl(@Nullable String contactUrl) {
        this.contactUrl = contactUrl;
        return this;
    }

    public TestDiscoveryCards publicSlug(@Nullable String publicSlug) {
        this.publicSlug = publicSlug;
        return this;
    }

    public TestDiscoveryCards acceptsFederation(boolean acceptsFederation) {
        this.acceptsFederation = acceptsFederation;
        return this;
    }

    /**
     * The card, as a peer that publishes no public offers would send it, taking requests only where
     * the test said so.
     *
     * @return the card
     */
    public DiscoveryStationCard build() {
        return new DiscoveryStationCard(
                stationUid,
                name,
                slogan,
                logoUrl,
                country,
                null,
                city,
                contactUrl,
                List.of(),
                "<10",
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                publicSlug,
                false,
                false,
                false,
                false,
                acceptsFederation);
    }
}
