/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

/**
 * A form field an uploaded PDF brings, as the editor lists it.
 *
 * @param name the fully qualified name of the field
 * @param kind what the field is
 * @param rect where its first widget sits, or null where it shows nowhere
 */
public record FormField(
        String name, FormFieldKind kind, @Nullable FieldRect rect) {}
