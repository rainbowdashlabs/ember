/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.station.entity.Station;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What one seed run has that belongs to the instance rather than to a station.
 *
 * <p>The instance administrator, the password every demo account shares, and the federation band's partner
 * are each seeded once however many stations there are. The stations themselves hang off it, in the order
 * they were made, and each carries its own members, events and news in a {@link DemoStationContext}.
 *
 * <p>The fields are volatile and the station list copy-on-write because seeders inside a band run in
 * parallel; the join between bands is what publishes a band's writes to the next one.
 */
public class DemoRunContext {
    private final String passwordHash;
    private final List<DemoStationContext> stations = new CopyOnWriteArrayList<>();
    private volatile Account adminAccount;
    private volatile DemoFederationSeeder.SeedResult federation;

    /**
     * @param passwordHash the hash every demo account's password is stored as
     */
    public DemoRunContext(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Account adminAccount() {
        return adminAccount;
    }

    public void adminAccount(Account adminAccount) {
        this.adminAccount = adminAccount;
    }

    public DemoFederationSeeder.SeedResult federation() {
        return federation;
    }

    public void federation(DemoFederationSeeder.SeedResult federation) {
        this.federation = federation;
    }

    /**
     * Adds a station the run is seeding, and hands back the context everything about it goes on.
     *
     * @param profile what this station is called, where it answers, and whose addresses its people hold
     * @param station the station that was just created
     * @return its own context
     */
    public DemoStationContext addStation(DemoStationProfile profile, Station station) {
        var context = new DemoStationContext(profile, station);
        stations.add(context);
        return context;
    }

    /**
     * The station the demo association takes as its member station.
     *
     * <p>Exactly one profile joins one, and the association is nothing to look at without it, so a run
     * where none does is a mistake in {@link DemoStations} rather than a case to seed around.
     *
     * @return its context
     * @throws IllegalStateException when no station this run built is meant to join an association
     */
    public DemoStationContext clusterStation() {
        return stations.stream()
                .filter(station -> station.profile().joinsCluster())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No demo station joins the association"));
    }

    /**
     * @return every station this run is seeding, in the order they were made
     */
    public List<DemoStationContext> stations() {
        return List.copyOf(stations);
    }

    /**
     * The first station, which is what an instance-wide seeder means when it says "the demo station".
     *
     * @return its context, or {@code null} while the station band has not run
     */
    public DemoStationContext primaryStation() {
        return stations.isEmpty() ? null : stations.getFirst();
    }
}
