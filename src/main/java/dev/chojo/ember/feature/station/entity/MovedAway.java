/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The copy a station left on this installation when it moved to another one.
 *
 * @param at      when the move finished
 * @param movedTo the address of the installation it moved to, or {@code null} where none is known
 */
public record MovedAway(Instant at, @Nullable String movedTo) {}
