/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import java.util.UUID;

/**
 * The station a station chat belongs to, as a reader sees it beside the chat.
 *
 * @param id   the station
 * @param uid  its identity
 * @param name its name
 */
public record StationRef(int id, UUID uid, String name) {}
