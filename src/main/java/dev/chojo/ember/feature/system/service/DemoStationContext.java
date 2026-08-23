/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;

/**
 * One station and everything a seed run hangs on it.
 *
 * <p>Held apart from {@link DemoRunContext} because these are the fields a second station would need a
 * second copy of: its own members, its own events, its own news. What belongs to the instance rather than
 * to a station stays on the run.
 *
 * <p>The station itself is final, because a station context exists exactly because its station does. The
 * rest is written in one band and read in later ones, and is volatile for the same reason the run's fields
 * are: seeders inside a band run in parallel, and the join between bands publishes what they wrote.
 */
public class DemoStationContext {
    private final DemoStationProfile profile;
    private final Station station;
    private volatile StationMember adminMember;
    private volatile DemoMemberSeeder.SeedResult members;
    private volatile DemoEventSeeder.SeedResult events;
    private volatile DemoNewsSeeder.SeedResult news;
    private volatile LostAndFoundItem lostAndFoundItem;

    public DemoStationContext(DemoStationProfile profile, Station station) {
        this.profile = profile;
        this.station = station;
    }

    /**
     * @return what this station is called, where it answers, and whose addresses its people hold
     */
    public DemoStationProfile profile() {
        return profile;
    }

    public Station station() {
        return station;
    }

    public int stationId() {
        return station.id();
    }

    /**
     * @return the station's manager member, named as the creator of everything seeded onto it
     */
    public StationMember adminMember() {
        return adminMember;
    }

    public void adminMember(StationMember adminMember) {
        this.adminMember = adminMember;
    }

    public DemoMemberSeeder.SeedResult members() {
        return members;
    }

    public void members(DemoMemberSeeder.SeedResult members) {
        this.members = members;
    }

    public DemoEventSeeder.SeedResult events() {
        return events;
    }

    public void events(DemoEventSeeder.SeedResult events) {
        this.events = events;
    }

    public DemoNewsSeeder.SeedResult news() {
        return news;
    }

    public void news(DemoNewsSeeder.SeedResult news) {
        this.news = news;
    }

    public LostAndFoundItem lostAndFoundItem() {
        return lostAndFoundItem;
    }

    public void lostAndFoundItem(LostAndFoundItem lostAndFoundItem) {
        this.lostAndFoundItem = lostAndFoundItem;
    }
}
