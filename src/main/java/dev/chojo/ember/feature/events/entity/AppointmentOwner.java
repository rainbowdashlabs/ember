/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * Whose setting a row is: an appointment's own, or a template's, held for the appointments made from
 * it.
 *
 * <p>Attachments, attendance field defaults and the partner share look the same on both, so one
 * service writes both and only the table it writes to differs.
 */
public enum AppointmentOwner {
    /** An appointment, by its id in {@code station_event}. */
    EVENT,
    /** An appointment template, by its id in {@code event_template}. */
    TEMPLATE
}
