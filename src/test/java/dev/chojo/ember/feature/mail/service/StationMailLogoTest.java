/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.WaitlistInvitationDetails;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.MailProviderBlockRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The mails a station sends about applications and waiting lists carry the station's logo in their header. */
class StationMailLogoTest {
    private static final String BASE = "https://ember.example.org";
    private static final int STATION = 7;
    private static final int STATION_WITHOUT_LOGO = 8;
    private static final String LOGO = BASE + "/api/v1/public/stations/abc/logo?size=128";

    private EmailQueueRepository queue;
    private EmailService email;

    @BeforeEach
    void setup() {
        var api = mock(Api.class);
        when(api.baseUrl()).thenReturn(BASE);
        var logos = mock(StationLogoService.class);
        when(logos.mailAddress(BASE, STATION)).thenReturn(Optional.of(LOGO));
        queue = mock(EmailQueueRepository.class);
        email = new EmailService(
                mock(Mailing.class),
                api,
                mock(Demo.class),
                queue,
                new MailTemplateRenderer(),
                mock(StationReadOnlyGuard.class),
                mock(MailChainService.class),
                mock(MailProviderBlockRepository.class),
                mock(MailRetryService.class),
                mock(MailAllowance.class),
                logos);
    }

    @Test
    void everyStationMailShowsTheStationLogo() {
        assertAll(sends(STATION).stream()
                .map(send -> () -> assertTrue(bodyOf(send).contains("<img src=\"" + LOGO + "\""))));
    }

    @Test
    void aStationWithoutALogoShowsNoImage() {
        assertAll(sends(STATION_WITHOUT_LOGO).stream()
                .map(send -> () -> assertFalse(bodyOf(send).contains("<img"))));
    }

    private List<Consumer<EmailService>> sends(int station) {
        return List.of(
                mail -> mail.sendApplicationAcceptedEmail("a@test.com", "Ada", "Wache", "token", "en", station),
                mail -> mail.sendWaitlistRegistrationEmail("a@test.com", "Ada", "token", "Wache", "en", station),
                mail -> mail.sendWaitlistInvitationEmail(
                        "a@test.com", "Ada", "token", "Wache", "en", station, WaitlistInvitationDetails.NONE),
                mail -> mail.sendWaitlistConfirmReminderEmail("a@test.com", "Ada", "token", "Wache", "en", station),
                mail -> mail.sendWaitlistRemovalWarningEmail("a@test.com", "Ada", "token", "Wache", "en", station),
                mail -> mail.sendWaitlistVerifyEmail("a@test.com", "Ada", "Wache", "token", "en", station));
    }

    private String bodyOf(Consumer<EmailService> send) {
        clearInvocations(queue);
        send.accept(email);
        var body = ArgumentCaptor.forClass(String.class);
        verify(queue).enqueue(eq("a@test.com"), anyString(), body.capture(), any());
        return body.getValue();
    }
}
