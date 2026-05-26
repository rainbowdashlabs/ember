/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.time.Instant;
import java.util.Map;

public record BatchRow(String name, Instant startTime, Instant endTime, Map<String, String> fieldValues) {}
