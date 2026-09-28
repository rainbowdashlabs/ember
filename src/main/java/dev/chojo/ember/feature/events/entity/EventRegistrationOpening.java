/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.util.List;

/**
 * Whom a reader may sign up for one appointment: themselves, the people they answer for, both or
 * nobody.
 *
 * <p>An appointment narrowed for registration is still shown to everybody who may see it, so a reader
 * meets appointments they cannot answer. An empty list says exactly that, which is what lets a screen
 * leave out the button instead of offering one the server then refuses.
 *
 * @param eventId   the appointment
 * @param memberIds the reader and the members they answer for who may register for it, in that order
 */
public record EventRegistrationOpening(int eventId, List<Integer> memberIds) {}
