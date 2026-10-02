/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

/** One page holding a form, and how far that page itself reaches. */
public record PageUsingForm(int id, String title, PageVisibility visibility) {}
