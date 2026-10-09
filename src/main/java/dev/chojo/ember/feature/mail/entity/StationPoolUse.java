/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import java.util.UUID;

/**
 * What one station sent through one of the instance's providers today.
 *
 * @param instancePosition where the provider sits in the instance's list
 * @param stationUid       the station
 * @param name             its name
 * @param sentToday        how many of its mails the provider sent today
 */
public record StationPoolUse(int instancePosition, UUID stationUid, String name, int sentToday) {}
