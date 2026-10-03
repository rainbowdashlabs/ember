/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.owner.Owner;

/**
 * A template that prints in a family of uploaded fonts.
 *
 * @param templateId the template
 * @param owner      the station or the association that keeps it
 * @param name       what it is called
 */
public record FontUse(int templateId, Owner owner, String name) {}
