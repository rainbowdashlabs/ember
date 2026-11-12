/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MailRefusal;
import dev.chojo.ember.feature.mail.entity.FailedMail;
import dev.chojo.ember.feature.mail.entity.FailedMailCause;
import dev.chojo.ember.feature.mail.entity.StationFailedMails;
import dev.chojo.ember.feature.mail.entity.StationFailedMails.CauseCount;
import dev.chojo.ember.feature.mail.service.FailedMailService;
import dev.chojo.ember.feature.mail.service.FailedMailService.AcknowledgedMails;
import dev.chojo.ember.feature.mail.service.FailedMailService.FailedMailPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The failed mails over HTTP: only an instance administrator reads them, takes note of them and
 * sends them again, and a station's mail is not reached through the instance's routes.
 */
class FailedMailRoutesTest {
    private static final String BASE = PREFIX + "/admin/config/mailing/failed";
    private static final UUID NORD = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Instant SEEN = Instant.parse("2026-11-11T10:15:30.123456Z");

    private FailedMailService failedMails;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        failedMails = mock(FailedMailService.class);
        harness = RouteHarness.serving(new FailedMailRoutes(failedMails));
    }

    @Test
    void anAdministratorReadsTheInstanceListAPageAtATime() {
        var mail = new FailedMail(
                7, "a@test.com", "Hallo", Instant.EPOCH, Instant.EPOCH, FailedMailCause.HARD_BOUNCE, "550", false);
        when(failedMails.instanceMails(2, 10)).thenReturn(new FailedMailPage(List.of(mail), 21, 2, 10));

        var answer = json(harness.request(
                client -> client.get(BASE + "?page=2&size=10", harness.as(TestSessions.administrator()))));

        assertEquals(21, answer.path("total").asInt());
        assertEquals("HARD_BOUNCE", answer.path("mails").get(0).path("cause").asString());
        assertEquals("550", answer.path("mails").get(0).path("detail").asString());
    }

    @Test
    void theStationListNamesNoMail() {
        when(failedMails.stationMails())
                .thenReturn(List.of(new StationFailedMails(
                        NORD, "Nord", 3, Instant.EPOCH, SEEN, List.of(new CauseCount(FailedMailCause.SPAM, 3)))));

        var answer = json(harness.request(
                        client -> client.get(BASE + "/stations", harness.as(TestSessions.administrator()))))
                .get(0);

        assertEquals("Nord", answer.path("stationName").asString());
        assertEquals(3, answer.path("count").asInt());
        assertTrue(answer.path("recipient").isMissingNode());
        assertTrue(answer.path("subject").isMissingNode());
    }

    @Test
    void anAdministratorTakesNoteOfOneAllAndAStationUpToWhatTheySaw() {
        var admin = harness.as(TestSessions.administrator());
        when(failedMails.acknowledgeAll(SEEN, TestSessions.ACCOUNT_ID)).thenReturn(new AcknowledgedMails(4));
        when(failedMails.acknowledgeStation(NORD, SEEN, TestSessions.ACCOUNT_ID))
                .thenReturn(new AcknowledgedMails(2));

        harness.run((server, client) -> {
            assertEquals(204, client.post(BASE + "/7/acknowledge", null, admin).code());
            assertEquals(
                    4,
                    json(client.post(BASE + "/acknowledge-all", body("{\"upTo\": \"%s\"}".formatted(SEEN)), admin))
                            .path("acknowledged")
                            .asInt());
            assertEquals(
                    2,
                    json(client.post(
                                    BASE + "/stations/" + NORD + "/acknowledge",
                                    body("{\"upTo\": \"%s\"}".formatted(SEEN)),
                                    admin))
                            .path("acknowledged")
                            .asInt());
            assertEquals(204, client.post(BASE + "/7/requeue", null, admin).code());
        });

        verify(failedMails).acknowledge(7, TestSessions.ACCOUNT_ID);
        verify(failedMails).acknowledgeStation(NORD, SEEN, TestSessions.ACCOUNT_ID);
        verify(failedMails).requeue(7);
    }

    @Test
    void aStationMailIdIsRefusedAsNotFound() {
        doThrow(MailRefusal.FAILED_MAIL_NOT_HERE.raise()).when(failedMails).acknowledge(9, TestSessions.ACCOUNT_ID);
        doThrow(MailRefusal.FAILED_MAIL_NOT_HERE.raise()).when(failedMails).requeue(9);
        var admin = harness.as(TestSessions.administrator());

        harness.run((server, client) -> {
            var acknowledged = client.post(BASE + "/9/acknowledge", null, admin);
            assertEquals(404, acknowledged.code());
            assertEquals(MailRefusal.FAILED_MAIL_NOT_HERE, refusalOf(acknowledged));
            assertEquals(MailRefusal.FAILED_MAIL_NOT_HERE, refusalOf(client.post(BASE + "/9/requeue", null, admin)));
        });
    }

    @Test
    void takingNoteWithoutSayingUpToWhenIsRefused() {
        when(failedMails.acknowledgeStation(NORD, null, TestSessions.ACCOUNT_ID))
                .thenThrow(MailRefusal.FAILED_MAILS_UP_TO_MISSING.raise());
        when(failedMails.acknowledgeAll(null, TestSessions.ACCOUNT_ID))
                .thenThrow(MailRefusal.FAILED_MAILS_UP_TO_MISSING.raise());
        var admin = harness.as(TestSessions.administrator());

        harness.run((server, client) -> {
            var station = client.post(BASE + "/stations/" + NORD + "/acknowledge", body("{}"), admin);
            assertEquals(400, station.code());
            assertEquals(MailRefusal.FAILED_MAILS_UP_TO_MISSING, refusalOf(station));
            assertEquals(
                    MailRefusal.FAILED_MAILS_UP_TO_MISSING,
                    refusalOf(client.post(BASE + "/acknowledge-all", body("{}"), admin)));
        });
    }

    @Test
    void aRefusedMailIsNotSentAgain() {
        doThrow(MailRefusal.FAILED_MAIL_REFUSED_BY_RECIPIENT.raise())
                .when(failedMails)
                .requeue(5);

        var answer = harness.request(
                client -> client.post(BASE + "/5/requeue", null, harness.as(TestSessions.administrator())));

        assertEquals(409, answer.code());
        assertEquals(MailRefusal.FAILED_MAIL_REFUSED_BY_RECIPIENT, refusalOf(answer));
    }

    @Test
    void aStationManagerReachesNoneOfIt() {
        var manager = harness.as(TestSessions.member(4, StationPermission.STATION_MAIL));

        harness.run((server, client) -> {
            assertEquals(403, client.get(BASE, manager).code());
            assertEquals(403, client.get(BASE + "/stations", manager).code());
            assertEquals(
                    403, client.post(BASE + "/acknowledge-all", null, manager).code());
            assertEquals(403, client.post(BASE + "/7/requeue", null, manager).code());
        });

        verifyNoInteractions(failedMails);
    }
}
