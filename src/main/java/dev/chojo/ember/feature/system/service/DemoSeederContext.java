/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;

/**
 * What is left of the run context for the seeders that still take the whole run.
 *
 * <p>Holds nothing itself. What belongs to the instance lives on {@link DemoRunContext} and what belongs to
 * one station on {@link DemoStationContext}; everything hung on a station has moved to
 * {@link DemoPerStationSeeder} already, and what is left here is what the six instance-wide seeders read.
 *
 * <p>Every method that names a station means the first one, which is right for those six and wrong for
 * anything else. That is why it shrinks with each seeder that moves across, and why it goes when the last
 * of them does.
 */
public class DemoSeederContext {
    private final DemoRunContext run;

    /**
     * @param passwordHash the hash every demo account's password is stored as
     */
    public DemoSeederContext(String passwordHash) {
        this(new DemoRunContext(passwordHash));
    }

    public DemoSeederContext(DemoRunContext run) {
        this.run = run;
    }

    /**
     * @return what this run has that belongs to the instance rather than to a station
     */
    public DemoRunContext run() {
        return run;
    }

    public String passwordHash() {
        return run.passwordHash();
    }

    public Station station() {
        var station = run.primaryStation();
        return station == null ? null : station.station();
    }

    /**
     * Publishes the station the rest of the run builds on.
     *
     * @param station the station the station band just created
     */
    public void station(Station station) {
        run.addStation(station);
    }

    /**
     * @return the primary demo station's id
     */
    public int stationId() {
        return station().id();
    }

    public Account adminAccount() {
        return run.adminAccount();
    }

    public void adminAccount(Account adminAccount) {
        run.adminAccount(adminAccount);
    }

    /**
     * @return the primary station's manager member, used as the creator of station-scoped content
     */
    public StationMember adminMember() {
        return primary().adminMember();
    }

    public DemoMemberSeeder.SeedResult members() {
        return primary().members();
    }

    public DemoFederationSeeder.SeedResult federation() {
        return run.federation();
    }

    public void federation(DemoFederationSeeder.SeedResult federation) {
        run.federation(federation);
    }

    /**
     * The first station's context, which is the one every method here means.
     *
     * @throws IllegalStateException when the station band has not run, because a seeder reading a station
     *                               before there is one is a band ordered wrongly rather than a missing value
     */
    private DemoStationContext primary() {
        var station = run.primaryStation();
        if (station == null) throw new IllegalStateException("No station has been seeded yet");
        return station;
    }
}
