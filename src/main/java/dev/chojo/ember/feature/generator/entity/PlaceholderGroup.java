/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Where the value of a placeholder comes from, which is how the editor's picker groups them.
 */
public enum PlaceholderGroup {
    /** The member the document is about. */
    MEMBER,
    /** One of the station's profile questions, answered by the member. */
    PROFILE,
    /** The first or second guardian of the member. */
    GUARDIAN,
    /** The station that files the document. */
    STATION,
    /** The appointment a document is generated for, where it is generated for one. */
    EVENT,
    /** The document itself: the day it is generated and who generates it. */
    DOCUMENT,
    /** A pronoun for the member, following the template's pronoun source. */
    PRONOUN
}
