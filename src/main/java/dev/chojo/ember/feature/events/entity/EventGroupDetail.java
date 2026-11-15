/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.restriction.RestrictionAudience;

import java.util.List;

/**
 * An event group with everything its page shows.
 *
 * @param eventGroup   the event group
 * @param sets         its sets in order
 * @param parts        its parts in date order
 * @param registration who may sign up
 * @param view         who may see it
 * @param questions    what it asks of everybody signing up, in order
 * @param fields       what every appointment of it shows, such as its place, in order
 * @param household    the sign-up of the reader and of everybody they look after who may see it
 */
public record EventGroupDetail(
        EventGroupSummary eventGroup,
        List<EventGroupSet> sets,
        List<EventGroupPartState> parts,
        RestrictionAudience registration,
        RestrictionAudience view,
        List<EventRegistrationField> questions,
        List<AppointmentField> fields,
        List<EventGroupSignUp> household) {}
