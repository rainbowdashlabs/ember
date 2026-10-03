/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Which column of pronouns a member is written with: the masculine, the feminine, or their official
 * first name wherever a pronoun would stand.
 */
public enum PronounForm {
    /** Er, ihn, ihm, sein. */
    ER,
    /** Sie, sie, ihr, ihr. */
    SIE,
    /** The official first name, and its genitive for the possessive. */
    NAME
}
