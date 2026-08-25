/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuizQuestionReport;
import dev.chojo.ember.feature.quiz.repository.QuizQuestionReportRepository;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizQuestionReportServiceTest {

    private QuizQuestionReportRepository repository;
    private QuizQuestionReportService service;

    @BeforeEach
    void setUp() {
        repository = mock(QuizQuestionReportRepository.class);
        service = new QuizQuestionReportService(repository);
        when(repository.create(anyInt(), any(), anyString()))
                .thenAnswer(i ->
                        new QuizQuestionReport(1, i.getArgument(0), "Wer auch immer", i.getArgument(2), Instant.EPOCH));
    }

    @Test
    void recordsWhatTheMemberSaid() {
        var report = service.report(42, 7, "Die Antwort ist veraltet");

        assertEquals(42, report.questionId());
        assertEquals("Die Antwort ist veraltet", report.note());
        verify(repository).create(42, 7, "Die Antwort ist veraltet");
    }

    @Test
    void trimsTheNoteBeforeStoringIt() {
        service.report(42, 7, "   Zwei Antworten passen   ");

        verify(repository).create(42, 7, "Zwei Antworten passen");
    }

    /** A note without a reason gives whoever reads it nothing to act on, so it is refused. */
    @Test
    void refusesANoteThatSaysNothing() {
        assertThrows(BadRequestResponse.class, () -> service.report(42, 7, "   "));
        assertThrows(BadRequestResponse.class, () -> service.report(42, 7, null));
        verify(repository, never()).create(anyInt(), any(), anyString());
    }

    @Test
    void cutsAnOverlongNoteToTheStoredLength() {
        service.report(42, 7, "x".repeat(5000));

        var captor = ArgumentCaptor.forClass(String.class);
        verify(repository).create(anyInt(), any(), captor.capture());
        assertEquals(2000, captor.getValue().length());
    }

    /** Somebody who is not a station member may still train, and may still say what is wrong. */
    @Test
    void acceptsANoteWithoutAMemberBehindIt() {
        service.report(42, null, "Frage ist doppelt");

        verify(repository).create(42, null, "Frage ist doppelt");
    }

    @Test
    void acknowledgingRemovesTheNote() {
        when(repository.delete(9)).thenReturn(true);

        assertTrue(service.acknowledge(9));
        verify(repository).delete(9);
    }

    @Test
    void acknowledgingANoteThatIsAlreadyGoneChangesNothing() {
        when(repository.delete(9)).thenReturn(false);

        assertFalse(service.acknowledge(9));
    }

    @Test
    void findsTheCatalogANoteBelongsTo() {
        when(repository.findCatalogOfReport(9)).thenReturn(Optional.of(5));

        assertEquals(Optional.of(5), service.findCatalogOfReport(9));
    }

    @Test
    void listsTheOpenNotesOfACatalog() {
        var open = List.of(new QuizQuestionReport(1, 42, "Nora", "Veraltet", Instant.EPOCH));
        when(repository.findByCatalog(5)).thenReturn(open);

        assertEquals(open, service.findByCatalog(5));
    }
}
