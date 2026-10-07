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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Searching, sorting and paging a list of templates, always over all of them before a page is cut. */
class TemplateQueryTest {
    private static final List<DocumentTemplateSummary> TEMPLATES = List.of(
            template(1, "Zustimmung", DocumentTemplateKind.PDF, true, 10, 30, 5),
            template(2, "anmeldung", DocumentTemplateKind.LETTER, false, 20, 20, null),
            template(3, "Bescheinigung", DocumentTemplateKind.LETTER, false, 30, 10, 50),
            template(4, "Mitgliedsantrag", DocumentTemplateKind.PDF, false, 40, 40, null));

    private static DocumentTemplateSummary template(
            int id,
            String name,
            DocumentTemplateKind kind,
            boolean forAppointments,
            int created,
            int updated,
            @Nullable Integer used) {
        return new DocumentTemplateSummary(
                id,
                name,
                kind,
                false,
                forAppointments,
                false,
                false,
                1,
                Instant.ofEpochSecond(created),
                Instant.ofEpochSecond(updated),
                used == null ? null : Instant.ofEpochSecond(used),
                null);
    }

    private static TemplateQuery sorted(TemplateSort sort, int page, int size) {
        return new TemplateQuery(null, null, null, sort, page, size);
    }

    private static List<String> names(TemplateQuery query) {
        return query.pageOf(TEMPLATES).items().stream()
                .map(DocumentTemplateSummary::name)
                .toList();
    }

    @Test
    void theFirstPageByNameStartsWithTheFirstNameOfAll() {
        var query = sorted(TemplateSort.NAME, 0, 2);

        assertEquals(List.of("anmeldung", "Bescheinigung"), names(query));
        assertEquals(4, query.pageOf(TEMPLATES).total());
    }

    @Test
    void theSecondPageHoldsWhatFollows() {
        assertEquals(List.of("Mitgliedsantrag", "Zustimmung"), names(sorted(TemplateSort.NAME, 1, 2)));
    }

    @Test
    void aPageBeyondTheLastIsEmpty() {
        assertEquals(List.of(), names(sorted(TemplateSort.NAME, 5, 2)));
    }

    @Test
    void lastUsedPutsTheNeverUsedLastByName() {
        assertEquals(
                List.of("Bescheinigung", "Zustimmung", "anmeldung", "Mitgliedsantrag"),
                names(sorted(TemplateSort.LAST_USED, 0, 10)));
    }

    @Test
    void createdAndUpdatedPutTheNewestFirst() {
        assertEquals(
                List.of("Mitgliedsantrag", "Bescheinigung", "anmeldung", "Zustimmung"),
                names(sorted(TemplateSort.CREATED, 0, 10)));
        assertEquals(
                List.of("Mitgliedsantrag", "Zustimmung", "anmeldung", "Bescheinigung"),
                names(sorted(TemplateSort.UPDATED, 0, 10)));
    }

    @Test
    void theSearchIgnoresCaseAndCountsOnlyWhatMatches() {
        var query = new TemplateQuery(" UNG ", null, null, TemplateSort.NAME, 0, 10);

        assertEquals(List.of("anmeldung", "Bescheinigung", "Zustimmung"), names(query));
        assertEquals(3, query.pageOf(TEMPLATES).total());
    }

    @Test
    void theKindNarrowsTheList() {
        assertEquals(
                List.of("Mitgliedsantrag", "Zustimmung"),
                names(new TemplateQuery(null, DocumentTemplateKind.PDF, null, TemplateSort.NAME, 0, 10)));
    }

    @Test
    void templatesForAppointmentsAreLeftOutOrKeptAlone() {
        assertEquals(
                List.of("anmeldung", "Bescheinigung", "Mitgliedsantrag"),
                names(new TemplateQuery(null, null, false, TemplateSort.NAME, 0, 10)));
        assertEquals(List.of("Zustimmung"), names(new TemplateQuery(null, null, true, TemplateSort.NAME, 0, 10)));
    }

    @Test
    void aMissingOrderAndAnOddPageAreTakenAsTheDefaults() {
        var query = new TemplateQuery(null, null, null, null, -3, 0);

        assertEquals(TemplateSort.LAST_USED, query.sort());
        assertEquals(0, query.page());
        assertEquals(TemplateQuery.DEFAULT_SIZE, query.size());
        assertEquals(TemplateQuery.MAX_SIZE, sorted(TemplateSort.NAME, 0, 5000).size());
    }
}
