/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.GenerationLogEntry;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.service.GenerationLogService.GeneratedDocumentEntry;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The list of generated documents names the member and whoever generated each, and leaves the name
 * empty for somebody who is gone.
 */
class GenerationLogServiceTest {

    @Test
    void anEntryCarriesTheNamesOfThePeopleStillThere() {
        var generations = mock(DocumentGenerationRepository.class);
        var names = mock(MemberNameResolver.class);
        when(names.identified(11)).thenReturn("Erika Muster");
        when(names.identified(12)).thenReturn("Nora Fülling");
        var at = Instant.parse("2026-10-01T08:00:00Z");
        when(generations.forStation(3))
                .thenReturn(List.of(
                        new GenerationLogEntry(1, at, 8, "Bescheinigung", 2, false, 11, 12, false, 21),
                        new GenerationLogEntry(2, at, 9, "Einverständnis", 1, true, null, 11, true, null)));

        var listed = new GenerationLogService(generations, names).list(3);

        assertEquals(
                List.of(
                        new GeneratedDocumentEntry(
                                1, at, 8, "Bescheinigung", 2, false, 11, "Erika Muster", "Nora Fülling", false, 21),
                        new GeneratedDocumentEntry(
                                2, at, 9, "Einverständnis", 1, true, null, null, "Erika Muster", true, null)),
                listed);
    }
}
