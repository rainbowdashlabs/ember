/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.feature.quiz.repository.AccountAiCredentialRepository;
import dev.chojo.ember.feature.quiz.repository.AiProviderRepository;
import dev.chojo.ember.feature.quiz.service.AiCredentialService.StationKey;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.CredentialCipherException;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AI keys of a transferred station: they leave sealed with the transfer token, never in
 * plaintext and never in the source instance's own encryption, and the destination stores them
 * under its own key so generation keeps working after the move.
 */
class StationAiKeyTransferTest extends RepositoryTestBase {
    private static final String TOKEN = "transfer-token-" + UUID.randomUUID();

    private final AiCredentialService source = credentials('s');
    private final AiCredentialService destination = credentials('d');

    private static AiCredentialService credentials(char keyByte) {
        var cipher = new CredentialCipher(Base64.getEncoder()
                .encodeToString(String.valueOf(keyByte).repeat(32).getBytes()));
        return new AiCredentialService(new AccountAiCredentialRepository(), new AiProviderRepository(), cipher);
    }

    private int newStation() {
        return stationRepo.create("AiKeyTransfer" + UUID.randomUUID()).id();
    }

    @Test
    void theDestinationCallsWithTheSameKeysAfterTheTransfer() {
        int sourceStation = newStation();
        source.saveStationKey(sourceStation, AiVendor.OPENAI, "sk-open", "gpt-4o");
        source.saveStationKey(sourceStation, AiVendor.CLAUDE, "sk-claude", null);
        String sealed =
                new StationAiKeyTransfer(source).seal(sourceStation, TOKEN).orElseThrow();

        int destinationStation = newStation();
        int adopted = new StationAiKeyTransfer(destination)
                .adopt(destinationStation, Map.of(StationAiKeyTransfer.FIELD, sealed), TOKEN);

        assertEquals(2, adopted);
        assertEquals(
                List.of(
                        new StationKey(AiVendor.CLAUDE, null, "sk-claude"),
                        new StationKey(AiVendor.OPENAI, "gpt-4o", "sk-open")),
                destination.stationKeys(destinationStation));
        assertTrue(
                source.stationKey(destinationStation, AiVendor.OPENAI).isEmpty(), "stored under the destination's key");
    }

    @Test
    void theSealedKeysAreNeitherPlaintextNorTheStoredValue() {
        int stationId = newStation();
        source.saveStationKey(stationId, AiVendor.OPENAI, "sk-very-secret", null);
        String stored = new AiProviderRepository()
                .findByProvider(stationId, AiVendor.OPENAI)
                .orElseThrow()
                .apiKey();

        String sealed = new StationAiKeyTransfer(source).seal(stationId, TOKEN).orElseThrow();

        assertFalse(sealed.contains("sk-very-secret"));
        assertFalse(sealed.contains(stored));
    }

    @Test
    void anotherTokenCannotOpenThem() {
        int stationId = newStation();
        source.saveStationKey(stationId, AiVendor.OPENAI, "sk-open", null);
        String sealed = new StationAiKeyTransfer(source).seal(stationId, TOKEN).orElseThrow();

        assertThrows(CredentialCipherException.class, () -> new StationAiKeyTransfer(destination)
                .adopt(newStation(), Map.of(StationAiKeyTransfer.FIELD, sealed), "another token"));
    }

    @Test
    void aKeyThatNoLongerOpensIsNotCarried() {
        int stationId = newStation();
        destination.saveStationKey(stationId, AiVendor.OPENAI, "sk-elsewhere", null);

        assertTrue(new StationAiKeyTransfer(source).seal(stationId, TOKEN).isEmpty());
    }

    @Test
    void theProviderTravelsUnderItsStoredKeySoEveryInstanceReadsIt() {
        int stationId = newStation();
        source.saveStationKey(stationId, AiVendor.OPENAI, "sk-open", null);

        String sealed = new StationAiKeyTransfer(source).seal(stationId, TOKEN).orElseThrow();

        assertTrue(StationAiKeyTransfer.cipher(TOKEN).unseal(sealed).contains("\"provider\":\"openai\""));
    }

    @Test
    void aKeyForAProviderThisInstanceDoesNotKnowIsLeftBehind() {
        String sealed = StationAiKeyTransfer.cipher(TOKEN).seal("""
                [{"provider":"mystery","model":null,"key":"sk-x"},
                 {"provider":"gemini","model":null,"key":"sk-g"}]""");
        int stationId = newStation();

        int adopted = new StationAiKeyTransfer(destination)
                .adopt(stationId, Map.of(StationAiKeyTransfer.FIELD, sealed), TOKEN);

        assertEquals(1, adopted);
        assertEquals(List.of(new StationKey(AiVendor.GEMINI, null, "sk-g")), destination.stationKeys(stationId));
    }

    @Test
    void aPageWithoutKeysLeavesTheStationWithout() {
        int stationId = newStation();

        assertEquals(0, new StationAiKeyTransfer(destination).adopt(stationId, Map.of(), TOKEN));
        assertTrue(destination.stationKeys(stationId).isEmpty());
    }
}
