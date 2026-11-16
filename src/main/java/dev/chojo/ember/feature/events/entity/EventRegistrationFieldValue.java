/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * One answer to one registration question. Deliberately the same shape as the attendance and
 * batch field entries, so every custom field payload in the API reads alike.
 */
public record EventRegistrationFieldValue(int fieldId, String value) {}
