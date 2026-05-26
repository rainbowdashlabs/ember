/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

/**
 * Summary information about a station's manager.
 *
 * @param email        the manager's email address
 * @param firstName    the manager's first name
 * @param lastName     the manager's last name
 * @param accountReady whether the manager's account is fully set up (has password, email verified)
 */
public record ManagerInfo(String email, String firstName, String lastName, boolean accountReady) {}
