/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.TemplateSort;
import dev.chojo.ember.feature.generator.service.TemplateQuery;
import io.javalin.http.Context;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Reads what a list of document templates is asked for from a request: {@code q}, {@code kind},
 * {@code forAppointments}, {@code sort}, {@code page} and {@code size}, every one optional.
 */
final class TemplateQueries {
    private TemplateQueries() {}

    /**
     * @param ctx the request
     * @return the query it asks
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when a kind or an order is named that does not
     *                                                     exist
     */
    static TemplateQuery of(Context ctx) {
        return new TemplateQuery(
                ctx.queryParam("q"),
                named(DocumentTemplateKind.class, ctx.queryParam("kind")),
                flag(ctx, "forAppointments"),
                Objects.requireNonNullElse(named(TemplateSort.class, ctx.queryParam("sort")), TemplateSort.LAST_USED),
                ctx.queryParamAsClass("page", Integer.class).getOrDefault(0),
                ctx.queryParamAsClass("size", Integer.class).getOrDefault(TemplateQuery.DEFAULT_SIZE));
    }

    private static @Nullable Boolean flag(Context ctx, String name) {
        if (ctx.queryParam(name) == null) return null;
        return ctx.queryParamAsClass(name, Boolean.class).getOrDefault(false);
    }

    private static <E extends Enum<E>> @Nullable E named(Class<E> type, @Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_LIST_UNKNOWN_CHOICE.raise();
        }
    }
}
