/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

/**
 * One step of the demo data seed run. Implementations are collected through a Guice multibinding
 * and executed by {@link DemoService} in ascending {@link #order()}; steps sharing an order value
 * run in parallel, so a step may only depend on data produced by a strictly lower order.
 *
 * <p>The order constants name the bands the seed run walks. Foreign-key dependencies are expressed
 * by picking a higher band, never by relying on the iteration order of the injected set.
 */
public interface DemoSeeder {
    /**
     * The plaintext password every seeded demo account shares.
     */
    String PASSWORD = "demo";

    /**
     * The station and its administrator account.
     */
    int STATION = 0;

    /**
     * Members, groups, tags and profile data every other step builds on.
     */
    int MEMBERS = 10;

    /**
     * The mirrored second station federated with the primary one.
     */
    int MIRROR_STATION = 20;

    /**
     * Events and event templates.
     */
    int EVENTS = 30;

    /**
     * News articles.
     */
    int NEWS = 40;

    /**
     * Lost and found items.
     */
    int LOST_AND_FOUND = 50;

    /**
     * Independent feature modules; everything in this band runs in parallel.
     */
    int MODULES = 60;

    /**
     * Modules that consume the federation partner created in {@link #MODULES}.
     */
    int FEDERATED_MODULES = 70;

    /**
     * The notification showcase, which references entities from every earlier band.
     */
    int SHOWCASE = 80;

    /**
     * Final setup state: completing the primary station's wizard and adding the un-set-up station.
     */
    int SETUP_STATE = 90;

    /**
     * The band this seeder belongs to. Lower values run first; equal values run in parallel.
     *
     * @return the order value
     */
    int order();

    /**
     * Seeds this step's data.
     *
     * @param context the shared run context carrying the results of earlier bands
     */
    void seed(DemoSeederContext context);
}
