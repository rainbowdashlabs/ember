/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Where a font family comes from, in the order a template looks for a family: its own station first,
 * then the association, then the instance, and last the fonts every installation has built in.
 */
public enum FontOrigin {
    STATION,
    ASSOCIATION,
    INSTANCE,
    BUILT_IN
}
