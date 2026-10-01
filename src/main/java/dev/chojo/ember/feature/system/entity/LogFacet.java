/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.entity;

/**
 * A value the application log can be narrowed to, and how many lines carry it.
 *
 * @param value what to filter by, which is also what is shown
 * @param count how many lines match it under the filter that produced this list
 */
public record LogFacet(String value, int count) {}
