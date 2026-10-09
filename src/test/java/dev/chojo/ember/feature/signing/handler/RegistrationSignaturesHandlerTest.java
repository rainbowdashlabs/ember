/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.handler;

import dev.chojo.ember.event.events.EventAnswerRecorded;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.signing.service.AppointmentSignatures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** A place taken asks for the signatures of the documents to bring; a place given up withdraws them. */
class RegistrationSignaturesHandlerTest {
    private static final LocalDate DAY = LocalDate.parse("2026-10-10");

    @ParameterizedTest
    @EnumSource(
            value = RegistrationStatus.class,
            names = {"PENDING", "ACCEPTED"})
    void aPlaceTakenAsks(RegistrationStatus status) {
        var signatures = mock(AppointmentSignatures.class);
        var handler = new RegistrationSignaturesHandler(() -> signatures);

        handler.handle(new EventAnswerRecorded(1, 2, 3, DAY, status));

        verify(signatures).askOnRegistration(2, DAY, 3);
        verifyNoMoreInteractions(signatures);
        assertEquals(EventAnswerRecorded.class, handler.eventType());
    }

    @ParameterizedTest
    @EnumSource(
            value = RegistrationStatus.class,
            names = {"DENIED", "DECLINED", "WITHDRAWN"})
    void aPlaceGivenUpWithdraws(RegistrationStatus status) {
        var signatures = mock(AppointmentSignatures.class);

        new RegistrationSignaturesHandler(() -> signatures).handle(new EventAnswerRecorded(1, 2, 3, DAY, status));

        verify(signatures).withdrawOnLeaving(2, DAY, 3);
        verifyNoMoreInteractions(signatures);
    }
}
