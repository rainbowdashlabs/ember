/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What an event group carries for all its appointments besides its sets and audiences: the
 * questions everybody signing up answers, and the fields every appointment shows.
 *
 * @param questions the questions, or null to leave them as they are
 * @param fields    the fields, or null to leave them as they are
 */
public record EventGroupContent(
        @Nullable List<RegistrationFieldDraft> questions,
        @Nullable List<EventFieldDraft> fields) {}
