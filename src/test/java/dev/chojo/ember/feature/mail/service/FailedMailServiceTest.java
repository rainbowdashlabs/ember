/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.api.refusal.MailRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.mail.entity.FailedMail;
import dev.chojo.ember.feature.mail.entity.FailedMailCause;
import dev.chojo.ember.feature.mail.entity.MailAttachment;
import dev.chojo.ember.feature.mail.entity.MailDeliveryStatus;
import dev.chojo.ember.feature.mail.entity.MailFailureReason;
import dev.chojo.ember.feature.mail.entity.StationFailedMails;
import dev.chojo.ember.feature.mail.entity.StationFailedMails.CauseCount;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mails that did not arrive, as the instance's administrator reads them, takes note of them and
 * sends them again: the instance's own in full, the stations' only counted.
 */
class FailedMailServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();
    /** A moment after every failure a test makes, for taking note of all of them. */
    private static final Instant LATER = Instant.now().plusSeconds(3600);

    private static FailedMailService service;
    private static int admin;

    @BeforeAll
    static void init() {
        service = new FailedMailService(emailQueueRepo, stationRepo);
    }

    /**
     * Each case starts with an empty queue and an administrator of its own: deleting a station sweeps
     * accounts that belong to no station, which takes a shared one along.
     */
    @BeforeEach
    void emptyQueueAndFreshAdministrator() {
        query("DELETE FROM email_queue;").single(call()).delete();
        admin = accountRepo
                .create("failed-admin-" + NAMES.incrementAndGet() + "@test.com", "Ad", "Min")
                .id();
    }

    @Test
    void theInstanceListShowsGiveUpsAndRefusalsButNeitherDeferralsNorStationMails() {
        var station = station();
        int givenUp = givenUp("given-up@test.com", null, MailFailureReason.PROVIDER_REFUSED);
        int bounced = reported("bounced@test.com", null, MailDeliveryStatus.HARD_BOUNCE, "550 no such user");
        reported("soft@test.com", null, MailDeliveryStatus.SOFT_BOUNCE, "mailbox full");
        reported("delivered@test.com", null, MailDeliveryStatus.DELIVERED, null);
        givenUp("station@test.com", station.id(), MailFailureReason.NO_PROVIDER);

        var page = service.instanceMails(0, 25);

        assertEquals(2, page.total());
        var byId = page.mails().stream().collect(Collectors.toMap(FailedMail::id, mail -> mail));
        assertEquals(FailedMailCause.PROVIDER_REFUSED, byId.get(givenUp).cause());
        assertNull(byId.get(givenUp).detail(), "a give-up has no provider text");
        assertTrue(byId.get(givenUp).requeueable());
        assertEquals(FailedMailCause.HARD_BOUNCE, byId.get(bounced).cause());
        assertEquals("550 no such user", byId.get(bounced).detail());
        assertFalse(byId.get(bounced).requeueable(), "a refused mail would be refused again");
        assertEquals(3, service.unacknowledgedCount(), "the count takes the stations' mails in too");
    }

    @Test
    void theInstanceListIsPagedWithTheLatestFailureFirst() {
        int older = givenUp("older@test.com", null, MailFailureReason.RETRIES_EXHAUSTED);
        int newer = givenUp("newer@test.com", null, MailFailureReason.RETRIES_EXHAUSTED);
        failedAt(older, "2 hours");
        failedAt(newer, "1 hour");

        var first = service.instanceMails(0, 1);
        var second = service.instanceMails(1, 1);

        assertEquals(List.of(newer), first.mails().stream().map(FailedMail::id).toList());
        assertEquals(List.of(older), second.mails().stream().map(FailedMail::id).toList());
        assertEquals(2, first.total());
        assertEquals(1, service.instanceMails(-3, 0).size(), "out of range is brought back into it");
    }

    @Test
    void stationMailsAreCountedPerStationWithTheirCausesOnly() {
        var nord = station();
        var sued = station();
        int first = givenUp("a@nord.test", nord.id(), MailFailureReason.NO_PROVIDER);
        int second = givenUp("b@nord.test", nord.id(), MailFailureReason.NO_PROVIDER);
        int third = reported("c@nord.test", nord.id(), MailDeliveryStatus.SPAM, "c@nord.test marked it");
        givenUp("d@sued.test", sued.id(), MailFailureReason.NO_PROVIDER_LEFT);
        failedAt(first, "3 hours");
        failedAt(second, "2 hours");
        reportedAgo(third, "1 hour");

        var stations = service.stationMails();

        assertEquals(2, stations.size());
        StationFailedMails group = stations.stream()
                .filter(entry -> entry.stationUid().equals(nord.uid()))
                .findFirst()
                .orElseThrow();
        assertEquals(nord.name(), group.stationName());
        assertEquals(3, group.count());
        assertEquals(
                List.of(new CauseCount(FailedMailCause.NO_PROVIDER, 2), new CauseCount(FailedMailCause.SPAM, 1)),
                group.causes());
        assertTrue(group.oldest().isBefore(group.newest()));
        assertFalse(group.toString().contains("nord.test"), "nothing names a recipient or what the provider said");
    }

    @Test
    void takingNoteOfAStationGoesUpToTheLatestFailureSeen() {
        var station = station();
        int seen = givenUp("seen@test.com", station.id(), MailFailureReason.NO_PROVIDER);
        failedAt(seen, "1 hour");
        Instant newestSeen = service.stationMails().getFirst().newest();
        givenUp("later@test.com", station.id(), MailFailureReason.NO_PROVIDER);

        var acknowledged = service.acknowledgeStation(station.uid(), newestSeen, admin);

        assertEquals(1, acknowledged.acknowledged());
        var left = service.stationMails();
        assertEquals(1, left.getFirst().count(), "the failure that came in meanwhile stays");
        assertEquals(1, service.unacknowledgedCount());
    }

    @Test
    void aMailClaimedBeforeTheAdministratorLookedButFailingAfterStays() {
        var station = station();
        int slow = claimed("slow@test.com", station.id());
        query("UPDATE email_queue SET claimed_at = now() - INTERVAL '2 hours' WHERE id = :id;")
                .single(call().bind("id", slow))
                .update();
        int seen = givenUp("seen@test.com", station.id(), MailFailureReason.NO_PROVIDER);
        failedAt(seen, "1 hour");
        Instant newestSeen = service.stationMails().getFirst().newest();
        emailQueueRepo.markFailed(slow, MailFailureReason.RETRIES_EXHAUSTED);

        assertEquals(
                1, service.acknowledgeStation(station.uid(), newestSeen, admin).acknowledged());

        assertEquals(1, service.stationMails().getFirst().count(), "it failed after the list was read");
    }

    @Test
    void takingNoteOfAllGoesUpToTheLatestFailureSeen() {
        int seen = givenUp("seen@test.com", null, MailFailureReason.NO_PROVIDER);
        failedAt(seen, "1 hour");
        Instant newestSeen = service.instanceMails(0, 25).mails().getFirst().failedAt();
        givenUp("later@test.com", null, MailFailureReason.NO_PROVIDER);

        assertEquals(1, service.acknowledgeAll(newestSeen, admin).acknowledged());

        assertEquals(1, service.instanceMails(0, 25).total());
        assertRefused(MailRefusal.FAILED_MAILS_UP_TO_MISSING, () -> service.acknowledgeAll(null, admin));
    }

    @Test
    void aDeletedStationTakesItsMailsAlong() {
        var station = station();
        int stationMail = givenUp("gone@test.com", station.id(), MailFailureReason.NO_PROVIDER);

        stationRepo.delete(station.id());

        assertEquals(0, service.instanceMails(0, 25).total(), "they do not turn into the instance's own");
        assertEquals(List.of(), service.stationMails());
        assertRefused(MailRefusal.FAILED_MAIL_NOT_HERE, () -> service.requeue(stationMail));
    }

    @Test
    void takingNoteOfAStationNeedsTheStationAndAMoment() {
        var station = station();

        assertRefused(
                MailRefusal.STATION_NOT_HERE_FOR_FAILED_MAILS,
                () -> service.acknowledgeStation(UUID.randomUUID(), Instant.now(), admin));
        assertRefused(
                MailRefusal.FAILED_MAILS_UP_TO_MISSING, () -> service.acknowledgeStation(station.uid(), null, admin));
    }

    @Test
    void takingNoteOfOneOrAllEndsTheWarningAndKeepsTheMail() {
        int one = givenUp("one@test.com", null, MailFailureReason.PROVIDER_BROKEN);
        reported("two@test.com", null, MailDeliveryStatus.BLOCKED, "relay refused");
        givenUp("three@test.com", null, MailFailureReason.NO_PROVIDER_LEFT);

        service.acknowledge(one, admin);
        service.acknowledge(one, admin);

        assertEquals(2, service.instanceMails(0, 25).total());
        assertEquals(2, service.acknowledgeAll(LATER, admin).acknowledged());
        assertEquals(0, service.instanceMails(0, 25).total());
        assertEquals(0, service.unacknowledgedCount());
        assertEquals(3, (int) query("SELECT count(*) AS n FROM email_queue WHERE acknowledged_by = :by;")
                .single(call().bind("by", admin))
                .map(row -> row.getInt("n"))
                .first()
                .orElseThrow());
    }

    @Test
    void aStationMailIsNotReachedThroughTheInstanceActions() {
        var station = station();
        int stationMail = givenUp("station@test.com", station.id(), MailFailureReason.NO_PROVIDER);
        int arrived = reported("arrived@test.com", null, MailDeliveryStatus.DELIVERED, null);

        assertRefused(MailRefusal.FAILED_MAIL_NOT_HERE, () -> service.acknowledge(stationMail, admin));
        assertRefused(MailRefusal.FAILED_MAIL_NOT_HERE, () -> service.requeue(stationMail));
        assertRefused(MailRefusal.FAILED_MAIL_NOT_HERE, () -> service.acknowledge(arrived, admin));
        assertRefused(MailRefusal.FAILED_MAIL_NOT_HERE, () -> service.requeue(0));
        assertEquals(
                0, service.acknowledgeAll(LATER, admin).acknowledged(), "taking note of all leaves stations alone");
        assertEquals(1, service.stationMails().getFirst().count());
    }

    @Test
    void aGiveUpGoesBackToTheFirstProviderWithItsFiles() {
        emailQueueRepo.enqueueWithAttachments(
                "files@test.com", "Files", "Body", List.of(new MailAttachment("a.pdf", MailAttachment.PDF, new byte[] {1
                })));
        var mail = emailQueueRepo.fetchPending(1, true).getFirst();
        emailQueueRepo.advanceProvider(mail.id());
        emailQueueRepo.markFailed(mail.id(), MailFailureReason.RETRIES_EXHAUSTED);
        service.acknowledge(mail.id(), admin);

        service.requeue(mail.id());

        var again = emailQueueRepo.fetchPending(1, true).getFirst();
        assertEquals(mail.id(), again.id());
        assertEquals(0, again.providerPosition());
        assertEquals(0, again.attempts());
        assertEquals(1, emailQueueRepo.attachmentsOf(mail.id()).size());
        assertEquals(
                "UNKNOWN",
                query("SELECT delivery_status FROM email_queue WHERE id = :id;")
                        .single(call().bind("id", mail.id()))
                        .map(row -> row.getString("delivery_status"))
                        .first()
                        .orElseThrow(),
                "a mail sent again starts without a report");
    }

    @Test
    void aRefusedMailOrOneThatFailedBeforeReasonsWereKeptIsNotSentAgain() {
        int bounced = reported("bounced@test.com", null, MailDeliveryStatus.SPAM, null);
        int old = givenUp("old@test.com", null, MailFailureReason.RETRIES_EXHAUSTED);
        query("UPDATE email_queue SET failure_reason = NULL WHERE id = :id;")
                .single(call().bind("id", old))
                .update();

        assertRefused(MailRefusal.FAILED_MAIL_REFUSED_BY_RECIPIENT, () -> service.requeue(bounced));
        assertRefused(MailRefusal.FAILED_MAIL_REASON_NOT_RECORDED, () -> service.requeue(old));
        var unrecorded = service.instanceMails(0, 25).mails().stream()
                .filter(mail -> mail.id() == old)
                .findFirst()
                .orElseThrow();
        assertEquals(FailedMailCause.UNRECORDED, unrecorded.cause());
        assertFalse(unrecorded.requeueable());
    }

    private static Station station() {
        return stationRepo.create("Failed Mail Station " + NAMES.incrementAndGet());
    }

    private static int claimed(String recipient, Integer stationId) {
        if (stationId == null) {
            emailQueueRepo.enqueue(recipient, "Subject", "Body");
        } else {
            emailQueueRepo.enqueue(recipient, "Subject", "Body", stationId);
        }
        return emailQueueRepo.fetchPending(1, true).getFirst().id();
    }

    private static int givenUp(String recipient, Integer stationId, MailFailureReason reason) {
        int id = claimed(recipient, stationId);
        emailQueueRepo.markFailed(id, reason);
        return id;
    }

    private static int reported(String recipient, Integer stationId, MailDeliveryStatus status, String detail) {
        int id = claimed(recipient, stationId);
        emailQueueRepo.markSent(id);
        emailQueueRepo.recordDelivery(id, status, detail, null);
        return id;
    }

    private static void failedAt(int id, String ago) {
        query("UPDATE email_queue SET failed_at = now() - CAST(:ago AS INTERVAL) WHERE id = :id;")
                .single(call().bind("id", id).bind("ago", ago))
                .update();
    }

    private static void reportedAgo(int id, String ago) {
        query("UPDATE email_queue SET delivery_updated_at = now() - CAST(:ago AS INTERVAL) WHERE id = :id;")
                .single(call().bind("id", id).bind("ago", ago))
                .update();
    }

    private static void assertRefused(Refusal expected, Executable action) {
        assertEquals(expected, assertThrows(RefusalResponse.class, action).refusal());
    }
}
