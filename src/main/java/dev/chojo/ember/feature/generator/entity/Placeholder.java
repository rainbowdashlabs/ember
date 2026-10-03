/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * One value a template can name, as the catalogue offers it.
 *
 * @param key       the stable key a template writes as {@code {{key}}}
 * @param label     what the editor shows for it, in the station's language
 * @param group     where its value comes from
 * @param informal  whether only informal templates may use it, which is the case for the name a member
 *                  is called by: a legal document names people by their official names
 * @param eventOnly whether it has a value only where a document is generated for an appointment
 */
public record Placeholder(String key, String label, PlaceholderGroup group, boolean informal, boolean eventOnly) {}
