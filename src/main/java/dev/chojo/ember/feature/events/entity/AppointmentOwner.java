/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * Whose setting a row is: an appointment's own, a template's, held for the appointments made from it,
 * or an event group's, handed to every one of its parts.
 *
 * <p>Attachments and attendance field defaults look the same on all three, so one service writes them
 * and only the table or column it writes to differs. The partner share looks the same on an appointment
 * and a template only; an event group is shared through a share of its own.
 */
public enum AppointmentOwner {
    /** An appointment, by its id in {@code station_event}. */
    EVENT,
    /** An appointment template, by its id in {@code event_template}. */
    TEMPLATE,
    /** An event group, by its id in {@code event_group}. */
    EVENT_GROUP
}
