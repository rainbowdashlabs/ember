/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * How an appointment is linked to the ones it took dates from and gave dates to.
 *
 * @param continues     the appointment and date this one took over from, or null
 * @param continuations the appointments that took over dates of this one, earliest date first: a date
 *                      taken out on its own, or the series it goes on as
 */
public record EventLinks(@Nullable EventLink continues, List<EventLink> continuations) {}
