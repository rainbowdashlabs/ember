/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailDeliveryStatus;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.ProviderSecretRepository;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a provider's report finds the mail it belongs to.
 *
 * <p>Two things matter here. A report has to reach the right mail, by our own token where the
 * provider carried it and by recipient and subject where it did not. And a report authorised by one
 * station's key must never reach another station's mail - the address a station hands to its own
 * provider is not a way into everybody else's post.
 */
class MailDeliveryServiceTest extends RepositoryTestBase {

    private static final EmailQueueRepository queue = new EmailQueueRepository();
    private static MailDeliveryService service;
    private static Station stationA;
    private static Station stationB;

    @BeforeAll
    static void setup() {
        var mailing = new Mailing();
        var chainService =
                new MailChainService(mailing, new StationMailProviderRepository(), new ProviderSecretRepository());
        service = new MailDeliveryService(queue, chainService);
        stationA = stationRepo.create("Delivery Station A");
        stationB = stationRepo.create("Delivery Station B");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
    }

    /**
     * Queues a mail and takes it out again, which is the only way to learn the id the queue gave it.
     */
    private static int queued(String recipient, String subject, Integer stationId) {
        queue.enqueue(recipient, subject, "<p>body</p>", stationId);
        return queue.fetchPending(50, true).stream()
                .filter(mail -> mail.recipient().equals(recipient))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static MailDeliveryService.DeliveryEvent event(
            MailDeliveryStatus status, String recipient, String subject, String token) {
        return new MailDeliveryService.DeliveryEvent(status, recipient, subject, token, "brevo-1", "on a block list");
    }

    @Test
    void ourOwnTokenFindsTheMail() {
        int id = queued("token@delivery.test", "Einladung", null);

        assertTrue(service.record(
                event(MailDeliveryStatus.SOFT_BOUNCE, "token@delivery.test", "Einladung", "" + id), null));
    }

    /**
     * Brevo does not always carry the header through on the relay, so the recipient and the subject
     * have to be enough on their own.
     */
    @Test
    void withoutATokenTheRecipientAndSubjectAreEnough() {
        queued("plain@delivery.test", "Erinnerung", null);

        assertTrue(
                service.record(event(MailDeliveryStatus.DELIVERED, "plain@delivery.test", "Erinnerung", null), null));
    }

    @Test
    void anEventForNobodyWeWroteToIsDropped() {
        assertFalse(
                service.record(event(MailDeliveryStatus.HARD_BOUNCE, "stranger@delivery.test", "Nichts", null), null));
    }

    /**
     * The point of a per-station key: a report authorised by station B cannot touch mail that
     * station A sent, however precisely it names it.
     */
    @Test
    void aStationKeyCannotReachAnotherStationsMail() {
        int id = queued("shared@delivery.test", "Wachenpost", stationA.id());

        assertFalse(service.record(
                event(MailDeliveryStatus.SOFT_BOUNCE, "shared@delivery.test", "Wachenpost", "" + id), stationB.id()));
        assertTrue(service.record(
                event(MailDeliveryStatus.SOFT_BOUNCE, "shared@delivery.test", "Wachenpost", "" + id), stationA.id()));
    }
}
