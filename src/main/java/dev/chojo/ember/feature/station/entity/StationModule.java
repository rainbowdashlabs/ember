/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

/**
 * Available feature modules that can be individually enabled or disabled per station.
 */
public enum StationModule {
    INVENTORY,
    NEWS,
    EVENTS,
    ATTENDANCE,
    FORMS,
    LOST_AND_FOUND,
    WAITING_LIST,
    QUIZ,
    KNOWLEDGE_BASE,
    TEST_PROTOCOL,
    BOARDS,
    PROCEDURES,
    /**
     * The station's document store.
     *
     * <p>Modules are held as the set a station has switched <em>off</em>, so adding one here leaves
     * every station that already keeps documents keeping them, and no migration writes anything.
     */
    DOCUMENTS
}
