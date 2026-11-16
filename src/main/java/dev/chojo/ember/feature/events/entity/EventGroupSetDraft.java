/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

/**
 * A set as the event group editor sends it.
 *
 * @param id      the set it changes, or null for a new one
 * @param name    its name
 * @param minimum how many of its parts a sign-up has to choose
 */
public record EventGroupSetDraft(@Nullable Integer id, String name, int minimum) {}
