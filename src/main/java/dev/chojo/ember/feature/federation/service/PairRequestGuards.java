/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.auth.signing.DatabaseReplayStore;
import dev.chojo.ember.auth.signing.SignedRequests;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.PairRequestDirection;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.PairRequestRepository;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestAnswer;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestMessage;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestStatusQuery;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * The checks every message about a request to federate passes, whichever side receives it.
 *
 * <p>A message comes from a stranger, so it is refused when a part is missing or longer than any honest
 * one would be, before anything reads it: an oversized value never reaches a key decoder or the database.
 * It has to be signed within {@link #MAX_DRIFT} of now, and its nonce may be seen once. A request is not
 * sent or taken while the two stations are partners, a request between them waits, or the last one was
 * declined within the cooldown; each side names those three cases with refusals of its own.
 */
@Singleton
public class PairRequestGuards {

    /** How far the moment a message was signed may lie from now, either way. */
    static final Duration MAX_DRIFT = Duration.ofMinutes(5);

    static final int MAX_NAME_LENGTH = 200;
    static final int MAX_KEY_LENGTH = 2048;
    static final int MAX_URL_LENGTH = 500;
    static final int MAX_TOKEN_LENGTH = 128;
    static final int MAX_CONTRACT_FEATURES = 64;

    private final DatabaseReplayStore replayStore;
    private final FederationRepository partners;
    private final PairRequestRepository requests;

    @Inject
    public PairRequestGuards(
            DatabaseReplayStore replayStore, FederationRepository partners, PairRequestRepository requests) {
        this.replayStore = replayStore;
        this.partners = partners;
        this.requests = requests;
    }

    /**
     * Refuses a request that misses a part, asks its own station, or carries a part that is too long.
     *
     * @param message the request
     */
    static void requireComplete(PairRequestMessage message) {
        FederationContract contract = message.contract();
        if (message.requesterStationUid() == null
                || message.targetStationUid() == null
                || message.requesterStationUid().equals(message.targetStationUid())
                || contract == null
                || contract.core() == null
                || message.issuedAt() == null
                || anyBlank(
                        message.requesterStationName(),
                        message.requesterPublicKey(),
                        message.requesterBaseUrl(),
                        message.requesterInstanceKey(),
                        message.nonce(),
                        message.stationSignature(),
                        message.instanceSignature())) {
            throw FederationRefusal.PAIR_REQUEST_INCOMPLETE.raise();
        }
        if (longer(MAX_NAME_LENGTH, message.requesterStationName())
                || message.requesterStationName().chars().anyMatch(Character::isISOControl)
                || longer(
                        MAX_KEY_LENGTH,
                        message.requesterPublicKey(),
                        message.stationSignature(),
                        message.instanceSignature())
                || longer(MAX_URL_LENGTH, message.requesterBaseUrl())
                || longer(MAX_TOKEN_LENGTH, message.requesterInstanceKey(), message.nonce(), contract.core())
                || contract.features().size() > MAX_CONTRACT_FEATURES) {
            throw FederationRefusal.PAIR_REQUEST_TOO_LARGE.raise();
        }
    }

    /**
     * Refuses a question about a request that misses a part or carries a part that is too long.
     *
     * @param query the question
     */
    static void requireComplete(PairRequestStatusQuery query) {
        if (query.requesterStationUid() == null
                || query.targetStationUid() == null
                || query.issuedAt() == null
                || anyBlank(query.nonce(), query.stationSignature(), query.instanceSignature())) {
            throw FederationRefusal.PAIR_REQUEST_INCOMPLETE.raise();
        }
        if (longer(MAX_TOKEN_LENGTH, query.nonce())
                || longer(MAX_KEY_LENGTH, query.stationSignature(), query.instanceSignature())) {
            throw FederationRefusal.PAIR_REQUEST_TOO_LARGE.raise();
        }
    }

    /**
     * Refuses an answer that misses a part, an acceptance without the key and contract it hands over, or
     * an answer carrying a part that is too long.
     *
     * @param answer the answer
     */
    static void requireComplete(PairRequestAnswer answer) {
        boolean accepted = answer.status() == PairRequestStatus.ACCEPTED;
        if (answer.requesterStationUid() == null
                || answer.targetStationUid() == null
                || answer.status() == null
                || answer.issuedAt() == null
                || anyBlank(answer.stationName(), answer.baseUrl(), answer.nonce(), answer.instanceSignature())
                || (accepted && (anyBlank(answer.publicKey()) || answer.contract() == null))) {
            throw FederationRefusal.PAIR_REQUEST_INCOMPLETE.raise();
        }
        if (longer(MAX_NAME_LENGTH, answer.stationName())
                || longer(MAX_URL_LENGTH, answer.baseUrl())
                || longer(MAX_TOKEN_LENGTH, answer.nonce())
                || longer(MAX_KEY_LENGTH, answer.instanceSignature(), answer.publicKey(), answer.stationSignature())) {
            throw FederationRefusal.PAIR_REQUEST_TOO_LARGE.raise();
        }
    }

    /**
     * Refuses a message signed further from now than {@link #MAX_DRIFT}.
     *
     * @param issuedAt when the message was signed
     */
    static void requireInTime(Instant issuedAt) {
        if (!SignedRequests.withinDrift(issuedAt, Instant.now(), MAX_DRIFT)) {
            throw FederationRefusal.PAIR_REQUEST_OUT_OF_TIME.raise();
        }
    }

    /**
     * Refuses a message whose nonce was seen before. The nonce is kept for as long as a message signed
     * with it could still arrive in time.
     *
     * @param scope    which kind of message the nonce belongs to
     * @param nonce    the message's nonce
     * @param issuedAt when the message was signed
     */
    void requireFirstSighting(String scope, String nonce, Instant issuedAt) {
        if (!replayStore.firstSighting(scope, nonce, issuedAt.plus(MAX_DRIFT).plus(MAX_DRIFT))) {
            throw FederationRefusal.PAIR_REQUEST_OUT_OF_TIME.raise();
        }
    }

    /**
     * Refuses a request between two stations that are partners already, that have one waiting in the
     * given direction, or whose last request in that direction was declined within the cooldown.
     *
     * @param stationId        the station here
     * @param direction        which side the station here is on
     * @param remoteStationUid the station of the other instance
     * @param refusals         what each case is refused with on this side
     */
    void requireNothingStanding(
            int stationId, PairRequestDirection direction, UUID remoteStationUid, StandingRefusals refusals) {
        if (partners.findPartnerByStationAndRemoteUid(stationId, remoteStationUid)
                .isPresent()) {
            throw refusals.partners().raise();
        }
        var earlier = requests.find(stationId, direction, remoteStationUid);
        if (earlier.isEmpty()) return;
        if (earlier.get().status() == PairRequestStatus.PENDING) {
            throw refusals.waiting().raise();
        }
        if (earlier.get().coolingDown(Instant.now())) {
            throw FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY.raise();
        }
    }

    private static boolean anyBlank(@Nullable String... values) {
        return Arrays.stream(values).anyMatch(value -> value == null || value.isBlank());
    }

    private static boolean longer(int max, @Nullable String... values) {
        return Arrays.stream(values).anyMatch(value -> value != null && value.length() > max);
    }

    /**
     * What a side refuses a request with when something already stands between the two stations.
     *
     * @param partners the stations are partners already
     * @param waiting  a request between them waits for its answer
     */
    record StandingRefusals(Refusal partners, Refusal waiting) {}
}
