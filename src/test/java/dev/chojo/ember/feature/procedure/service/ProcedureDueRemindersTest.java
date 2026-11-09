/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureStepKind;
import dev.chojo.ember.feature.procedure.repository.ProcedureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A procedure falling due reminds of the signatures its open document steps still wait for, once. */
class ProcedureDueRemindersTest {
    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final int PROCEDURE = 3;
    private static final Instant DUE = NOW.plus(ProcedureDueReminders.AHEAD).minusSeconds(60);
    private static final Instant ASKED_BEFORE = NOW.minus(ProcedureDueReminders.SETTLED);

    private ProcedureRepository repository;
    private DocumentStepSigning signing;
    private ProcedureDueReminders reminders;

    @BeforeEach
    void setup() {
        repository = mock(ProcedureRepository.class);
        signing = mock(DocumentStepSigning.class);
        reminders = new ProcedureDueReminders(repository, signing, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.findFallingDue(NOW, NOW.plus(ProcedureDueReminders.AHEAD), ProcedureDueReminders.MAX_PER_RUN))
                .thenReturn(List.of(procedure()));
    }

    /** Only the open document steps with a document are reminded of; ticked ones and check steps are not. */
    @Test
    void theOpenDocumentStepsAreRemindedOf() {
        when(repository.markDueReminded(PROCEDURE, DUE, NOW)).thenReturn(true);
        when(repository.findItems(PROCEDURE))
                .thenReturn(List.of(
                        step(1, ProcedureStepKind.DOCUMENT, 70, false),
                        step(2, ProcedureStepKind.DOCUMENT, 71, true),
                        step(3, ProcedureStepKind.DOCUMENT, null, false),
                        step(4, ProcedureStepKind.CHECK, null, false)));
        when(signing.remind(70, ASKED_BEFORE)).thenReturn(2);

        assertEquals(2, reminders.sweep(NOW));

        verify(signing).remind(70, ASKED_BEFORE);
        verify(signing, never()).remind(eq(71), any());
    }

    /** Only requests asked for a while ago are reminded of; a fresh one keeps its own mail. */
    @Test
    void aFreshRequestIsNotRemindedOfAtOnce() {
        when(repository.markDueReminded(PROCEDURE, DUE, NOW)).thenReturn(true);
        when(repository.findItems(PROCEDURE)).thenReturn(List.of(step(1, ProcedureStepKind.DOCUMENT, 70, false)));

        reminders.sweep(NOW);

        verify(signing).remind(70, NOW.minus(ProcedureDueReminders.SETTLED));
    }

    /** A procedure another run recorded already is not reminded of again. */
    @Test
    void aProcedureIsRemindedOfOnce() {
        when(repository.markDueReminded(PROCEDURE, DUE, NOW)).thenReturn(false);

        assertEquals(0, reminders.sweep(NOW));

        verify(signing, never()).remind(anyInt(), any());
    }

    /** A reminder that fails does not stop the run. */
    @Test
    void aFailingReminderIsLogged() {
        when(repository.markDueReminded(PROCEDURE, DUE, NOW)).thenReturn(true);
        when(repository.findItems(PROCEDURE)).thenReturn(List.of(step(1, ProcedureStepKind.DOCUMENT, 70, false)));
        when(signing.remind(70, ASKED_BEFORE)).thenThrow(new IllegalStateException("mail away"));

        assertDoesNotThrow(() -> reminders.sweep());
    }

    private static Procedure procedure() {
        return new Procedure(
                PROCEDURE,
                1,
                null,
                "Aufnahme",
                null,
                true,
                ProcedureStatus.OPEN,
                10,
                DUE,
                Instant.EPOCH,
                null,
                null,
                null,
                30,
                null);
    }

    private static ProcedureItem step(int id, ProcedureStepKind kind, Integer generationId, boolean checked) {
        return new ProcedureItem(
                id,
                PROCEDURE,
                "Schritt",
                null,
                null,
                true,
                true,
                0,
                checked,
                null,
                null,
                kind,
                kind == ProcedureStepKind.DOCUMENT ? 7 : null,
                generationId);
    }
}
