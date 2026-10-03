/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * A placeholder a template names that has no value for one member.
 *
 * @param key   the placeholder
 * @param label what the station calls it
 */
public record MissingValue(String key, String label) {}
