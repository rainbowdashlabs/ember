/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.MailProviderBlockRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MailTasksTest {

    @Test
    void theQueueIsSentEveryTenSecondsAndCleanedOnceADay() {
        var queue = mock(EmailQueueRepository.class);
        var tasks = new EmailService(
                        mock(Mailing.class),
                        mock(Api.class),
                        mock(Demo.class),
                        queue,
                        mock(MailTemplateRenderer.class),
                        mock(StationReadOnlyGuard.class),
                        mock(MailChainService.class),
                        mock(MailProviderBlockRepository.class),
                        mock(MailRetryService.class))
                .scheduledTasks();

        tasks.forEach(task -> task.work().run());

        verify(queue).fetchPending(anyInt(), anyBoolean());
        verify(queue).cleanupOldEntries(30);
        assertEquals("email-queue", tasks.get(0).name());
        assertEquals(
                Schedule.fixedDelay(Duration.ofSeconds(10), Duration.ofSeconds(10)),
                tasks.get(0).schedule());
        assertEquals("email-queue-cleanup", tasks.get(1).name());
        assertEquals(
                Schedule.fixedRate(Duration.ofHours(1), Duration.ofHours(24)),
                tasks.get(1).schedule());
    }
}
