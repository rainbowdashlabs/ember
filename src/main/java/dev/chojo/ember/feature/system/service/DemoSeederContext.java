/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;

/**
 * Carries the entities produced during a demo seed run from one {@link DemoSeeder} band to the next.
 *
 * <p>Holds nothing itself any more. What belongs to the instance lives on {@link DemoRunContext} and what
 * belongs to one station on {@link DemoStationContext}, and this reads both through the accessors the
 * seeders already use, so the split costs no seeder a line until it is moved across deliberately.
 *
 * <p>Every method here that names a station means the first one. That is the whole of what a second
 * station would break, and it is why the two contexts behind it exist: a seeder moved onto them says which
 * station it means, and this one cannot.
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

    public void adminMember(StationMember adminMember) {
        primary().adminMember(adminMember);
    }

    public DemoMemberSeeder.SeedResult members() {
        return primary().members();
    }

    public void members(DemoMemberSeeder.SeedResult members) {
        primary().members(members);
    }

    public DemoEventSeeder.SeedResult events() {
        return primary().events();
    }

    public void events(DemoEventSeeder.SeedResult events) {
        primary().events(events);
    }

    public DemoNewsSeeder.SeedResult news() {
        return primary().news();
    }

    public void news(DemoNewsSeeder.SeedResult news) {
        primary().news(news);
    }

    public LostAndFoundItem lostAndFoundItem() {
        return primary().lostAndFoundItem();
    }

    public void lostAndFoundItem(LostAndFoundItem lostAndFoundItem) {
        primary().lostAndFoundItem(lostAndFoundItem);
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
