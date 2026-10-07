/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One value a template can name, as the catalogue offers it.
 *
 * @param key       the stable key a template writes as {@code {{key}}}
 * @param label     what the editor shows for it, in the station's language
 * @param category  whom or what it is about, the first step the picker offers it under
 * @param path      the steps the picker offers it under in the station's language, from the word of its
 *                  category to its own name there. The name can be shorter than the label, because the
 *                  steps before it already say the rest: "Vorname" under "Erziehungsberechtigte 1"
 * @param informal  whether only informal templates may use it, which is the case for the name a member
 *                  is called by: a legal document names people by their official names
 * @param eventOnly whether it has a value only where a document is generated for an appointment
 * @param dateKind  what kind of date it holds, which decides the formats it can print in, or null where
 *                  it holds no date and takes no format
 */
public record Placeholder(
        String key,
        String label,
        PlaceholderCategory category,
        List<String> path,
        boolean informal,
        boolean eventOnly,
        @Nullable DateKind dateKind) {}
