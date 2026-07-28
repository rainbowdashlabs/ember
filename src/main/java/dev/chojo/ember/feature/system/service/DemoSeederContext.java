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
 * Carries the entities produced during a demo seed run from one {@link DemoSeeder} band to the
 * next. Every field is written in one band and read in later ones; the join between bands
 * publishes the writes, and the volatile fields keep that visible for the parallel readers
 * inside a band.
 */
public class DemoSeederContext {
    private final String passwordHash;
    private volatile Station station;
    private volatile Account adminAccount;
    private volatile StationMember adminMember;
    private volatile DemoMemberSeeder.SeedResult members;
    private volatile DemoEventSeeder.SeedResult events;
    private volatile DemoNewsSeeder.SeedResult news;
    private volatile LostAndFoundItem lostAndFoundItem;
    private volatile DemoFederationSeeder.SeedResult federation;

    /**
     * @param passwordHash the hash every demo account's password is stored as
     */
    public DemoSeederContext(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Station station() {
        return station;
    }

    public void station(Station station) {
        this.station = station;
    }

    /**
     * @return the primary demo station's id
     */
    public int stationId() {
        return station.id();
    }

    public Account adminAccount() {
        return adminAccount;
    }

    public void adminAccount(Account adminAccount) {
        this.adminAccount = adminAccount;
    }

    /**
     * @return the primary station's manager member, used as the creator of station-scoped content
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

    public DemoFederationSeeder.SeedResult federation() {
        return federation;
    }

    public void federation(DemoFederationSeeder.SeedResult federation) {
        this.federation = federation;
    }
}
