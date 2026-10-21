/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.feature.generator.service.WordFormImportService;
import dev.chojo.ember.feature.generator.service.WordFormImportService.ChosenFont;
import dev.chojo.ember.feature.generator.service.WordFormImportService.OriginalFont;
import dev.chojo.ember.feature.generator.service.WordFormImportService.PreviewVersion;
import dev.chojo.ember.owner.Owner;
import io.javalin.http.Context;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The replacements chosen for the fonts of a Word form, sent beside the file as the form field
 * {@code fonts}, written as JSON.
 *
 * @param fonts the family and size each missing one is drawn in, by the family the form names
 */
public record FormFontChoices(@Nullable Map<String, ChosenFont> fonts) {

    /**
     * @param ctx a request carrying a Word form
     * @return the replacements it chose, none where it sent none
     */
    static Map<String, ChosenFont> of(Context ctx) {
        String written = ctx.formParam("fonts");
        if (written == null || written.isBlank()) return Map.of();
        FormFontChoices choices = ctx.jsonMapper().fromJsonString(written, FormFontChoices.class);
        var fonts = choices.fonts();
        return fonts == null ? Map.of() : fonts;
    }

    /**
     * Answers a request for the preview of a Word form with its PDF. The form field {@code version} names the
     * drawing: {@code original} for the form in its own fonts, anything else for the form with the
     * replacements of {@code fonts}. The original takes font files as {@code originalFonts}, each for the
     * family named at the same place of {@code originalFamilies}, a JSON list; a file without a family is
     * left out.
     *
     * @param ctx   a request carrying a Word form
     * @param forms the import of Word forms
     * @param owner the station or the association that would keep the template
     */
    static void preview(Context ctx, WordFormImportService forms, Owner owner) {
        var version = "original".equals(ctx.formParam("version")) ? PreviewVersion.ORIGINAL : PreviewVersion.REPLACED;
        byte[] pdf = forms.preview(owner, ctx.uploadedFile("file"), of(ctx), version, originalFontsOf(ctx));
        ctx.contentType("application/pdf").result(pdf);
    }

    private static List<OriginalFont> originalFontsOf(Context ctx) {
        var files = ctx.uploadedFiles("originalFonts");
        String written = ctx.formParam("originalFamilies");
        if (files.isEmpty() || written == null || written.isBlank()) return List.of();
        String[] families = ctx.jsonMapper().fromJsonString(written, String[].class);
        var fonts = new ArrayList<OriginalFont>();
        for (int index = 0; index < Math.min(files.size(), families.length); index++) {
            fonts.add(new OriginalFont(families[index], files.get(index)));
        }
        return fonts;
    }
}
