/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import java.util.List;

public record SectionEntry(String title, String description, List<SourceEntry> sources) {}
