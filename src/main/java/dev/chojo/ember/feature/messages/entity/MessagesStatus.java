/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

/**
 * What the header's messages button needs to know about the reader.
 *
 * @param available whether the reader can use messages at all; the button is hidden otherwise
 * @param unread    how many messages wait unread, muted conversations left out
 * @param mail          whether the reader is sent the daily mail about messages left unread
 * @param stationDigest what that mail takes from station chats: mentions of the reader, or everything
 */
public record MessagesStatus(boolean available, int unread, boolean mail, StationDigest stationDigest) {}
