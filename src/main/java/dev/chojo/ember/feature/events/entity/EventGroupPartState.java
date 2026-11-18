/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A part of an event group as the event group page lists it.
 *
 * @param eventId   the appointment
 * @param setId     the set it belongs to
 * @param name      the appointment's name
 * @param startTime when it begins
 * @param endTime   when it ends
 * @param calledOff whether it was called off
 * @param tookPlace whether its day lies before today
 * @param required  whether its set requires every sign-up to hold it
 * @param places    how many places it has, its own or the event group's places per part, or null for no limit
 * @param accepted  how many accepted registrations hold one of them
 */
public record EventGroupPartState(
        int eventId,
        int setId,
        String name,
        Instant startTime,
        Instant endTime,
        boolean calledOff,
        boolean tookPlace,
        boolean required,
        @Nullable Integer places,
        int accepted) {}
