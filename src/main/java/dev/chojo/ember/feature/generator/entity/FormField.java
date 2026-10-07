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
 * <p>The name is often a generated one such as {@code Text1}. What tells a field apart for a reader is
 * the tooltip its author gave it and the text it already holds, so both are kept where the PDF has
 * them. A PDF inspected before these were kept has neither until it is uploaded again.
 *
 * @param name    the fully qualified name of the field
 * @param kind    what the field is
 * @param rect    where its first widget sits, or null where it shows nowhere
 * @param tooltip the tooltip the PDF's author gave the field, or null where it has none
 * @param value   the text a text field holds in the PDF as uploaded, or null where it is empty or no text
 *                field
 */
public record FormField(
        String name,
        FormFieldKind kind,
        @Nullable FieldRect rect,
        @Nullable String tooltip,
        @Nullable String value) {}
