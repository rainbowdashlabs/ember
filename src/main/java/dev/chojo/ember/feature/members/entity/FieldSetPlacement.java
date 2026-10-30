/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * Where a question stands within its bundle.
 *
 * @param setId    the bundle
 * @param position its place within the bundle, smallest first
 */
public record FieldSetPlacement(int setId, int position) {}
