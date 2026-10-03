/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Who uploaded a font, in the order a template looks for a family: its own station first, then the
 * association, then the instance.
 */
public enum FontOrigin {
    STATION,
    ASSOCIATION,
    INSTANCE
}
