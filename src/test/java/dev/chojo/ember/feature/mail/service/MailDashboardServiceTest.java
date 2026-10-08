/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.MailProviderBlockRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailDashboardServiceTest {
    private final EmailQueueRepository queue = mock(EmailQueueRepository.class);
    private final MailProviderBlockRepository blocks = mock(MailProviderBlockRepository.class);
    private final MailDashboardService service =
            new MailDashboardService(queue, mock(MailChainService.class), blocks, mock(MailAllowance.class));

    @Test
    void aBlockIsLiftedForTheOwnerItWasPutOn() {
        service.liftBlock(4, MailProviderType.BREVO, "example.org");

        verify(blocks).lift(4, MailProviderType.BREVO, "example.org");
    }

    @Test
    void stuckMailsAreQueuedAgain() {
        when(queue.requeueStuck(null, 9)).thenReturn(1);

        assertEquals(1, service.requeueStuck(null, 9).requeued());
    }
}
