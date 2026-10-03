/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * A template that prints in a family of uploaded fonts.
 *
 * @param templateId the template
 * @param stationId  the station that keeps it
 * @param name       what it is called
 */
public record FontUse(int templateId, int stationId, String name) {}
