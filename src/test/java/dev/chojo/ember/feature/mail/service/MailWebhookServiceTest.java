/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.mail.entity.MailDeliveryStatus;
import dev.chojo.ember.feature.mail.repository.MailWebhookReceiptRepository;
import dev.chojo.ember.feature.mail.service.MailDeliveryService.DeliveryEvent;
import dev.chojo.ember.feature.mail.service.MailWebhookService.SignedCall;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.webhook.service.WebhookKeyService;
import dev.chojo.ember.feature.webhook.service.WebhookKeyService.WebhookScope;
import dev.chojo.ember.lifecycle.Schedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailWebhookServiceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET =
            Base64.getEncoder().encodeToString("a-signing-secret".getBytes(StandardCharsets.UTF_8));
    private static final String TIMESTAMP = "1769696506";
    private static final Instant NOW =
            Instant.ofEpochSecond(Long.parseLong(TIMESTAMP)).plusSeconds(30);

    private MailDeliveryService deliveries;
    private MailChainService chains;
    private MailWebhookReceiptRepository receipts;
    private MailWebhookService service;

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    private static String sign(String body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"));
        return "v1,"
                + Base64.getEncoder()
                        .encodeToString(mac.doFinal(("id." + TIMESTAMP + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    @BeforeEach
    void setup() {
        deliveries = mock(MailDeliveryService.class);
        chains = mock(MailChainService.class);
        when(chains.sweegoSecret(3)).thenReturn("");
        var keys = mock(WebhookKeyService.class);
        when(keys.resolve("station")).thenReturn(Optional.of(new WebhookScope(3)));
        receipts = mock(MailWebhookReceiptRepository.class);
        when(receipts.claim(eq(MailProviderType.SWEEGO), any())).thenReturn(true);
        service = new MailWebhookService(deliveries, keys, chains, receipts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SignedCall sweegoCall(String webhookId, String body) {
        return new SignedCall(webhookId, null, null, body, json(body));
    }

    @Test
    void aSweegoReportSentAgainIsAnsweredWithoutBeingRecordedTwice() {
        String body = "{\"event_type\":\"soft_bounce\",\"recipient\":\"a@b.test\"}";
        when(receipts.claim(MailProviderType.SWEEGO, "msg_1")).thenReturn(true, false);

        service.sweego("station", sweegoCall("msg_1", body));
        service.sweego("station", sweegoCall("msg_1", body));

        verify(deliveries, times(1)).record(any(), eq(3));
    }

    @Test
    void aSweegoReportThatFailsGivesItsIdBackForTheNextAttempt() {
        when(deliveries.record(any(), eq(3))).thenThrow(new IllegalStateException("database gone"));

        assertThrows(
                IllegalStateException.class,
                () -> service.sweego("station", sweegoCall("msg_2", "{\"event_type\":\"delivered\"}")));

        verify(receipts).release(MailProviderType.SWEEGO, "msg_2");
    }

    @Test
    void aSweegoReportWithoutAnIdIsRecordedWithoutAReceipt() {
        service.sweego("station", sweegoCall(" ", "{\"event_type\":\"delivered\"}"));

        verify(receipts, never()).claim(any(), any());
        verify(deliveries).record(any(), eq(3));
    }

    @Test
    void receiptsAreClearedOnceADay() {
        var tasks = service.scheduledTasks();

        tasks.forEach(task -> task.work().run());

        verify(receipts).prune(7);
        assertEquals("mail-webhook-receipt-cleanup", tasks.getFirst().name());
        assertEquals(
                Schedule.fixedRate(Duration.ofHours(1), Duration.ofHours(24)),
                tasks.getFirst().schedule());
    }

    @Test
    void aKeyThatAuthorisesNothingIsRefusedForEveryProvider() {
        assertEquals(
                Refusal.MAIL_REPORT_NOT_TAKEN,
                assertThrows(RefusalResponse.class, () -> service.brevo("guess", json("{}")))
                        .refusal());
        assertThrows(RefusalResponse.class, () -> service.sendGrid("guess", json("[]")));
        assertThrows(
                RefusalResponse.class,
                () -> service.sweego("guess", new SignedCall(null, null, null, "{}", json("{}"))));
    }

    @Test
    void brevoOutcomesAreRecordedAndReaderEventsAreSettledWithoutOne() {
        when(deliveries.record(any(), eq(3))).thenReturn(false);

        assertTrue(service.brevo("station", json("{\"event\":\"opened\"}")));
        assertTrue(service.brevo("station", json("{}")));
        assertFalse(service.brevo("station", json("{\"event\":\"hardBounce\",\"email\":\"a@b.test\"}")));

        verify(deliveries)
                .record(new DeliveryEvent(MailDeliveryStatus.HARD_BOUNCE, "a@b.test", null, null, null, null), 3);
    }

    @Test
    void everyBrevoOutcomeHasItsStatus() {
        for (var name :
                new String[] {"delivered", "soft_bounce", "invalid_email", "blocked", "spam", "deferred", "error"}) {
            service.brevo("station", json("{\"event\":\"" + name + "\"}"));
        }

        verify(deliveries, times(7)).record(any(), eq(3));
    }

    @Test
    void sendGridBatchesAreRecordedEventByEventAndAnythingElseIsIgnored() {
        service.sendGrid("station", json("{\"event\":\"bounce\"}"));
        service.sendGrid("station", json("""
                        [{"event":"delivered","email":"a@b.test","ember_id":"e1"},{"event":"open"},{"event":"bounce"},
                         {"event":"blocked"},{"event":"deferred"},{"event":"dropped"},{"event":"spamreport"},{}]"""));

        verify(deliveries, times(6)).record(any(), eq(3));
        verify(deliveries)
                .record(new DeliveryEvent(MailDeliveryStatus.DELIVERED, "a@b.test", null, "e1", null, null), 3);
    }

    @Test
    void anUnsignedSweegoReportIsTakenOnTheKeyAlone() {
        service.sweego(
                "station",
                new SignedCall(
                        null,
                        null,
                        null,
                        "",
                        json(
                                "[{\"event_type\":\"soft-bounce\",\"recipient\":\"a@b.test\"},{\"event_type\":\"email_sent\"}]")));

        verify(deliveries)
                .record(new DeliveryEvent(MailDeliveryStatus.SOFT_BOUNCE, "a@b.test", null, null, null, null), 3);
    }

    @Test
    void whereASecretIsSetOnlyAProperlySignedSweegoReportIsTaken() throws Exception {
        when(chains.sweegoSecret(3)).thenReturn(SECRET);
        String body = "{\"event_type\":\"hard_bounce\",\"headers\":{\"x-custom-header\":\"e7\"}}";

        service.sweego("station", new SignedCall("id", TIMESTAMP, sign(body), body, json(body)));
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.sweego("station", new SignedCall("id", TIMESTAMP, "v1,forged", body, json(body))));

        assertEquals(Refusal.MAIL_REPORT_NOT_TAKEN, refused.refusal());
        verify(deliveries).record(new DeliveryEvent(MailDeliveryStatus.HARD_BOUNCE, null, null, "e7", null, null), 3);
    }

    @Test
    void aSweegoComplaintAndDeliveryAreRecordedAndNoEventIsIgnored() {
        service.sweego(
                "station",
                new SignedCall(
                        null,
                        null,
                        null,
                        "",
                        json("[{\"event_type\":\"complaint\"},{\"event_type\":\"delivered\"},{}]")));

        verify(deliveries, times(2)).record(any(), eq(3));
        verify(deliveries, never()).record(any(), eq(null));
    }
}
