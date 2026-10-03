/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Why somebody's data is in a generated document.
 */
public enum SubjectRole {
    /** The member the document is about. */
    MEMBER,
    /** A guardian of that member whom the document names. */
    GUARDIAN
}
