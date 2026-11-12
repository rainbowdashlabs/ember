/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * A part of an appointment a change notice names as changed. It names the part, never the old or new
 * value, except for the start time, which the notice carries on its own.
 */
public enum EventChangePart {
    /** When it starts or ends on its day. */
    TIME,
    /** The day it falls on, or the days a series falls on. */
    DATE,
    /** Its name. */
    NAME,
    /** Its description. */
    DESCRIPTION,
    /** The details it lists, such as the place or what to bring. */
    FIELDS,
    /** The questions asked when signing up. */
    REGISTRATION_QUESTIONS,
    /** The files attached to it. */
    ATTACHMENTS
}
