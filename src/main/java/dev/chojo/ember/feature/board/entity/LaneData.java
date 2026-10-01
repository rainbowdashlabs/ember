/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import org.jspecify.annotations.Nullable;

public record LaneData(
        @Nullable Integer id, String name, @Nullable String color) {}
