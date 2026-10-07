/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * The order a list of document templates is shown in. Every order but the name puts the newest first,
 * and templates that tie stand in the order of their names.
 */
public enum TemplateSort {
    /** The template a document was generated from most recently first, those never used last. */
    LAST_USED,
    /** By name, A to Z. */
    NAME,
    /** The template created most recently first. */
    CREATED,
    /** The template changed most recently first. */
    UPDATED
}
