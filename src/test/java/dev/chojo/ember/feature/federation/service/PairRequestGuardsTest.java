/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestAnswer;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestStatusQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The checks a question and an answer about a request pass before anything reads them.
 */
class PairRequestGuardsTest {
    private static final UUID ASKING = UUID.randomUUID();
    private static final UUID ASKED = UUID.randomUUID();
    private static final String LONG_KEY = "k".repeat(PairRequestGuards.MAX_KEY_LENGTH + 1);

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    private static PairRequestStatusQuery query(String nonce, String stationSignature) {
        return new PairRequestStatusQuery(ASKING, ASKED, Instant.now(), nonce, stationSignature, "instance");
    }

    private static PairRequestAnswer answer(
            PairRequestStatus status, String stationName, String publicKey, FederationContract contract) {
        return new PairRequestAnswer(
                ASKING,
                ASKED,
                status,
                stationName,
                "https://sued.example",
                publicKey,
                contract,
                Instant.now(),
                "nonce",
                null,
                "instance");
    }

    @Test
    void aQuestionMissingAPartOrCarryingTooMuchIsRefused() {
        assertDoesNotThrow(() -> PairRequestGuards.requireComplete(query("nonce", "station")));
        assertEquals(
                FederationRefusal.PAIR_REQUEST_INCOMPLETE,
                refusalOf(() -> PairRequestGuards.requireComplete(query(" ", "station"))));
        assertEquals(
                FederationRefusal.PAIR_REQUEST_TOO_LARGE,
                refusalOf(() -> PairRequestGuards.requireComplete(query("nonce", LONG_KEY))));
        assertEquals(
                FederationRefusal.PAIR_REQUEST_TOO_LARGE,
                refusalOf(() -> PairRequestGuards.requireComplete(
                        query("n".repeat(PairRequestGuards.MAX_TOKEN_LENGTH + 1), "station"))));
    }

    @Test
    void anAcceptanceHandsOverItsKeyAndContract() {
        var contract = new FederationContract("core", Map.of());

        assertDoesNotThrow(
                () -> PairRequestGuards.requireComplete(answer(PairRequestStatus.DECLINED, "Wache Süd", null, null)));
        assertDoesNotThrow(() ->
                PairRequestGuards.requireComplete(answer(PairRequestStatus.ACCEPTED, "Wache Süd", "key", contract)));
        assertEquals(
                FederationRefusal.PAIR_REQUEST_INCOMPLETE,
                refusalOf(() -> PairRequestGuards.requireComplete(
                        answer(PairRequestStatus.ACCEPTED, "Wache Süd", null, contract))));
        assertEquals(
                FederationRefusal.PAIR_REQUEST_INCOMPLETE,
                refusalOf(() -> PairRequestGuards.requireComplete(
                        answer(PairRequestStatus.ACCEPTED, "Wache Süd", "key", null))));
    }

    @Test
    void anAnswerCarryingTooMuchIsRefused() {
        assertEquals(
                FederationRefusal.PAIR_REQUEST_TOO_LARGE,
                refusalOf(() -> PairRequestGuards.requireComplete(answer(
                        PairRequestStatus.DECLINED, "W".repeat(PairRequestGuards.MAX_NAME_LENGTH + 1), null, null))));
        assertEquals(
                FederationRefusal.PAIR_REQUEST_TOO_LARGE,
                refusalOf(() -> PairRequestGuards.requireComplete(answer(
                        PairRequestStatus.ACCEPTED, "Wache Süd", LONG_KEY, new FederationContract("core", Map.of())))));
    }

    @Test
    void aMessageSignedTooLongAgoIsRefused() {
        assertDoesNotThrow(() -> PairRequestGuards.requireInTime(Instant.now()));
        assertEquals(
                FederationRefusal.PAIR_REQUEST_OUT_OF_TIME,
                refusalOf(() -> PairRequestGuards.requireInTime(
                        Instant.now().minus(PairRequestGuards.MAX_DRIFT.plus(Duration.ofMinutes(1))))));
    }
}
