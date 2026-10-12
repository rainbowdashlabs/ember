/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import io.javalin.http.Context;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The replacements chosen for the fonts of a Word form, sent beside the file as the form field
 * {@code fonts}, written as JSON.
 *
 * @param fonts the family each missing one is drawn in, empty for the default font, by the family the
 *              form names
 */
public record FormFontChoices(@Nullable Map<String, String> fonts) {

    /**
     * @param ctx a request carrying a Word form
     * @return the replacements it chose, none where it sent none
     */
    static Map<String, String> of(Context ctx) {
        String written = ctx.formParam("fonts");
        if (written == null || written.isBlank()) return Map.of();
        FormFontChoices choices = ctx.jsonMapper().fromJsonString(written, FormFontChoices.class);
        var fonts = choices.fonts();
        return fonts == null ? Map.of() : fonts;
    }
}
