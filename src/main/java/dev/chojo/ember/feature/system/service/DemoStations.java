/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import java.util.List;
import java.util.UUID;

/**
 * The stations the demo builds in full, and the only place that says how many there are.
 *
 * <p>Two of them, the same station twice: one answering to the demo association and one answering to
 * nobody. Every feature has two answers, one for each, and a demo carrying only the first can show only
 * half of what it does. Adding a third is a line here.
 *
 * <p>They are deliberately alike. Nothing about the second is set differently on purpose, so anything that
 * differs on screen is the association's doing and nothing else.
 */
public final class DemoStations {

    /**
     * The station the demo has always been about. It keeps its name, its identity, its address and the
     * addresses of everybody at it, so nothing that already names one of them has to change.
     */
    public static final DemoStationProfile MUSTERSTADT = new DemoStationProfile(
            "musterstadt",
            "Jugendfeuerwehr Musterstadt",
            UUID.fromString("00000000-0000-4000-a000-000000000001"),
            "jugendfeuerwehr-musterstadt",
            "",
            false);

    /** The same station again, this one inside the association. */
    public static final DemoStationProfile NORDSTADT = new DemoStationProfile(
            "nord",
            "Jugendfeuerwehr Nordstadt",
            UUID.fromString("00000000-0000-4000-a000-000000000003"),
            "jugendfeuerwehr-nordstadt",
            ".nord",
            true);

    /** In the order they are built, which is the order they are offered in. */
    public static final List<DemoStationProfile> ALL = List.of(MUSTERSTADT, NORDSTADT);

    private DemoStations() {}
}
