/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.TemplateSort;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateSummary;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * What a list of document templates is asked for: a search, a kind, an order and a page.
 *
 * <p>The search, the order and the page are applied to every template the place offers, before a page
 * is cut, so the first page sorted by name starts with the first name of all of them and not of those
 * that happened to come first. A station keeps tens of templates, not thousands, so the list is put
 * together once and then searched, sorted and cut here.
 *
 * @param search          what the name contains, ignoring case; blank for every template
 * @param kind            only templates of this kind, or null for both
 * @param forAppointments only templates for appointments when true, only the others when false, both
 *                        when null
 * @param sort            the order
 * @param page            the page, counted from 0
 * @param size            how many templates a page holds
 */
public record TemplateQuery(
        @Nullable String search,
        @Nullable DocumentTemplateKind kind,
        @Nullable Boolean forAppointments,
        TemplateSort sort,
        int page,
        int size) {
    /** How many templates a page holds where nothing else was asked for. */
    public static final int DEFAULT_SIZE = 24;

    /** The most templates one page holds. */
    public static final int MAX_SIZE = 100;

    public TemplateQuery {
        sort = Objects.requireNonNullElse(sort, TemplateSort.LAST_USED);
        page = Math.max(page, 0);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    /**
     * One page of the templates that match, in the order asked for.
     *
     * @param templates every template the place offers
     * @return the page and how many match in all
     */
    public TemplatePage pageOf(List<DocumentTemplateSummary> templates) {
        var matching =
                templates.stream().filter(this::matches).sorted(comparator()).toList();
        int from = Math.min(page * size, matching.size());
        int to = Math.min(from + size, matching.size());
        return new TemplatePage(matching.subList(from, to), matching.size(), page, size);
    }

    private boolean matches(DocumentTemplateSummary template) {
        if (kind != null && template.kind() != kind) return false;
        if (forAppointments != null && template.forAppointments() != forAppointments) return false;
        if (search == null || search.isBlank()) return true;
        return template.name().toLowerCase(Locale.ROOT).contains(search.strip().toLowerCase(Locale.ROOT));
    }

    private Comparator<DocumentTemplateSummary> comparator() {
        Comparator<DocumentTemplateSummary> byName =
                Comparator.comparing(DocumentTemplateSummary::name, String.CASE_INSENSITIVE_ORDER);
        Comparator<DocumentTemplateSummary> chosen =
                switch (sort) {
                    case NAME -> byName;
                    case CREATED -> newestFirst(DocumentTemplateSummary::createdAt);
                    case UPDATED -> newestFirst(DocumentTemplateSummary::updatedAt);
                    case LAST_USED ->
                        Comparator.comparing(
                                DocumentTemplateSummary::lastUsedAt,
                                Comparator.nullsLast(Comparator.<Instant>reverseOrder()));
                };
        return chosen.thenComparing(byName);
    }

    private static Comparator<DocumentTemplateSummary> newestFirst(Function<DocumentTemplateSummary, Instant> at) {
        return Comparator.comparing(at, Comparator.reverseOrder());
    }

    /**
     * A page of templates.
     *
     * @param items the templates on it
     * @param total how many match in all, over every page
     * @param page  the page, counted from 0
     * @param size  how many a page holds
     */
    public record TemplatePage(List<DocumentTemplateSummary> items, int total, int page, int size) {}
}
