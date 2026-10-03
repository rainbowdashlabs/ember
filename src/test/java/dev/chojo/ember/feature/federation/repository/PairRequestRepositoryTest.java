/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.entity.PairRequestDirection;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The requests to federate a station here has with stations of other instances, as they are stored:
 * written once per pair and direction, answered once, asked about, listed and swept away.
 */
class PairRequestRepositoryTest extends RepositoryTestBase {
    private static final String BASE_URL = "https://peer.example";
    private static final String INSTANCE_KEY = "instance-key";

    private final PairRequestRepository repository = new PairRequestRepository();
    private Station station;

    @BeforeEach
    void setup() {
        station = stationRepo.create("PairRequestRepo " + UUID.randomUUID());
    }

    @AfterEach
    void cleanup() {
        stationRepo.delete(station.id());
    }

    private PairRequest incoming(UUID remote) {
        return repository.recordIncoming(
                station.id(),
                remote,
                "Wache Nord",
                BASE_URL,
                INSTANCE_KEY,
                "station-key",
                FederationContractVersions.current());
    }

    private PairRequest outgoing(UUID remote) {
        return repository.recordOutgoing(station.id(), remote, "Wache Süd", BASE_URL, INSTANCE_KEY);
    }

    @Test
    void anIncomingRequestKeepsWhatItCarried() {
        UUID remote = UUID.randomUUID();

        var request = incoming(remote);

        assertEquals(PairRequestDirection.INCOMING, request.direction());
        assertEquals(PairRequestStatus.PENDING, request.status());
        assertEquals("station-key", request.requireRemotePublicKey());
        assertEquals(FederationContractVersions.current(), request.remoteContract());
        assertEquals(request, repository.findById(request.id()).orElseThrow());
        assertEquals(
                request,
                repository
                        .find(station.id(), PairRequestDirection.INCOMING, remote)
                        .orElseThrow());
        assertTrue(repository
                .find(station.id(), PairRequestDirection.OUTGOING, remote)
                .isEmpty());
        assertTrue(repository.findById(-1).isEmpty());
    }

    @Test
    void aRequestSentAgainStartsOverAsPending() {
        UUID remote = UUID.randomUUID();
        var first = outgoing(remote);
        repository.answer(first.id(), PairRequestStatus.DECLINED);
        repository.markChecked(first.id());

        var again = outgoing(remote);

        assertEquals(first.id(), again.id());
        assertEquals(PairRequestStatus.PENDING, again.status());
        assertNull(again.answeredAt());
        assertNull(again.checkedAt());
        assertNull(again.remotePublicKey());
    }

    @Test
    void aRequestKeepsItsFirstAnswer() {
        var request = incoming(UUID.randomUUID());

        assertTrue(repository.answer(request.id(), PairRequestStatus.DECLINED));
        assertFalse(repository.answer(request.id(), PairRequestStatus.ACCEPTED));

        var answered = repository.findById(request.id()).orElseThrow();
        assertEquals(PairRequestStatus.DECLINED, answered.status());
        assertNotNull(answered.answeredAt());
        assertTrue(answered.coolingDown(Instant.now()));
        assertFalse(answered.coolingDown(Instant.now().plus(PairRequest.DECLINE_COOLDOWN)));
    }

    @Test
    void onlyPendingIncomingRequestsWaitForAnAnswer() {
        var waiting = incoming(UUID.randomUUID());
        var declined = incoming(UUID.randomUUID());
        repository.answer(declined.id(), PairRequestStatus.DECLINED);
        outgoing(UUID.randomUUID());

        var pending = repository.findPendingIncoming(station.id());

        assertEquals(1, pending.size());
        assertEquals(waiting.id(), pending.getFirst().id());
    }

    @Test
    void theOutgoingListShowsWhatWaitsAndWhatWasAnsweredSince() {
        var waiting = outgoing(UUID.randomUUID());
        var declined = outgoing(UUID.randomUUID());
        repository.answer(declined.id(), PairRequestStatus.DECLINED);
        incoming(UUID.randomUUID());

        var recent = repository.findOutgoing(station.id(), Instant.now().minus(Duration.ofDays(1)));
        var onlyWaiting = repository.findOutgoing(station.id(), Instant.now().plus(Duration.ofDays(1)));

        assertEquals(2, recent.size());
        assertEquals(1, onlyWaiting.size());
        assertEquals(waiting.id(), onlyWaiting.getFirst().id());
    }

    @Test
    void aRequestAskedAboutLatelyIsNotDue() {
        var unasked = outgoing(UUID.randomUUID());
        var asked = outgoing(UUID.randomUUID());
        repository.markChecked(asked.id());
        var answered = outgoing(UUID.randomUUID());
        repository.answer(answered.id(), PairRequestStatus.DECLINED);

        var dueNow = repository.findPendingOutgoingDue(Instant.now().minus(Duration.ofMinutes(10))).stream()
                .filter(request -> request.stationId() == station.id())
                .map(PairRequest::id)
                .toList();
        var dueLater = repository.findPendingOutgoingDue(Instant.now().plus(Duration.ofMinutes(10))).stream()
                .filter(request -> request.stationId() == station.id())
                .map(PairRequest::id)
                .toList();

        assertEquals(List.of(unasked.id()), dueNow);
        assertEquals(List.of(unasked.id(), asked.id()), dueLater);
        assertNotNull(repository.findById(asked.id()).orElseThrow().checkedAt());
    }

    @Test
    void answeredRequestsAreSweptOnceTheirTimeIsOver() {
        var waiting = outgoing(UUID.randomUUID());
        var accepted = incoming(UUID.randomUUID());
        repository.answer(accepted.id(), PairRequestStatus.ACCEPTED);
        var declined = outgoing(UUID.randomUUID());
        repository.answer(declined.id(), PairRequestStatus.DECLINED);

        repository.deleteAnsweredBefore(Instant.now().minus(Duration.ofDays(1)));
        assertTrue(repository.findById(declined.id()).isPresent(), "an answer within the time stays");

        assertTrue(repository.deleteAnsweredBefore(Instant.now().plus(Duration.ofMinutes(1))) >= 2);

        assertTrue(repository.findById(accepted.id()).isEmpty());
        assertTrue(repository.findById(declined.id()).isEmpty());
        assertTrue(repository.findById(waiting.id()).isPresent(), "a request still waiting is never swept");
    }

    @Test
    void aRequestTurnedIntoAPartnershipIsRemoved() {
        var request = outgoing(UUID.randomUUID());

        assertTrue(repository.delete(request.id()));
        assertFalse(repository.delete(request.id()));
        assertTrue(repository.findById(request.id()).isEmpty());
    }
}
