/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import org.jspecify.annotations.Nullable;

public record UrlMetadata(@Nullable String title, @Nullable String description) {}
