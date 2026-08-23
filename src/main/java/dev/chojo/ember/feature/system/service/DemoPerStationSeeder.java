/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

/**
 * A seeder for the things that hang on a station: its members, its events, its gear, what it has said.
 *
 * <p>The run hands it the station to work on rather than letting it ask for "the" station, which is the
 * whole difference between a demo carrying one station and a demo carrying two. Everything that belongs to
 * the instance instead is a plain {@link DemoSeeder} and runs once however many stations there are.
 */
public interface DemoPerStationSeeder extends DemoSeeder {

    /**
     * Runs this seeder once per station, in the order the stations were made.
     *
     * <p>Sequential rather than parallel: what a band costs is the band, the stations inside it are small
     * beside it, and a seeder writing two stations at once would have to be written for that.
     */
    @Override
    default void seed(DemoRunContext run) {
        for (DemoStationContext station : run.stations()) {
            seedStation(run, station);
        }
    }

    /**
     * Seeds one station.
     *
     * @param run     what this run has that belongs to the instance rather than to a station
     * @param station the station being seeded, and everything already hung on it
     */
    void seedStation(DemoRunContext run, DemoStationContext station);
}
