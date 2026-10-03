/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Who generates a document in which role.
 */
public enum GenerationWay {
    /** A manager, for one member or for many in a run. */
    MANAGER,
    /** The member or their guardian, from the templates offered for self service. */
    SELF_SERVICE,
    /** A participant of an appointment or their guardian, as a document the appointment asks to bring. */
    APPOINTMENT
}
