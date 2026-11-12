/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.util.UUID;

/**
 * A person's member card at one station the reader shares with them.
 *
 * @param stationUid  the station
 * @param stationName its name, which tells the cards of several stations apart
 * @param card        the card as a reader at that station sees it
 */
public record SharedMemberCard(UUID stationUid, String stationName, MemberCard card) {}
