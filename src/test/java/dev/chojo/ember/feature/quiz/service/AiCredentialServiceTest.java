/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.AccountAiCredential;
import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.feature.quiz.entity.StationAiProvider;
import dev.chojo.ember.feature.quiz.repository.AccountAiCredentialRepository;
import dev.chojo.ember.feature.quiz.repository.AiProviderRepository;
import dev.chojo.ember.feature.quiz.service.AiCredentialService.AiCredentialSummary;
import dev.chojo.ember.feature.quiz.service.AiCredentialService.SaveOutcome;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A person's AI key is stored sealed, shown only by its ending, used only for its own provider, and
 * counts as missing once it no longer opens.
 */
class AiCredentialServiceTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final AccountAiCredentialRepository repository = mock(AccountAiCredentialRepository.class);
    private final AiProviderRepository stations = mock(AiProviderRepository.class);
    private final CredentialCipher cipher = new CredentialCipher(KEY);
    private AiCredentialService service;

    @BeforeEach
    void setUp() {
        service = new AiCredentialService(repository, stations, cipher);
    }

    @Test
    void aStationKeyIsWrittenSealed() {
        service.saveStationKey(3, AiVendor.OPENAI, " sk-station ", "gpt-4o");

        var sealed = ArgumentCaptor.forClass(String.class);
        verify(stations).upsert(eq(3), eq(AiVendor.OPENAI), sealed.capture(), eq("gpt-4o"));
        assertEquals("sk-station", cipher.unseal(sealed.getValue()));
    }

    @Test
    void aStationKeyIsReadWhetherSealedOrFromBeforeEncryption() {
        when(stations.findByProvider(3, AiVendor.OPENAI))
                .thenReturn(Optional.of(new StationAiProvider(1, 3, AiVendor.OPENAI, cipher.seal("sk-sealed"), null)));
        when(stations.findByProvider(4, AiVendor.OPENAI))
                .thenReturn(Optional.of(new StationAiProvider(2, 4, AiVendor.OPENAI, "sk-plain", null)));
        when(stations.findByProvider(5, AiVendor.OPENAI))
                .thenReturn(Optional.of(new StationAiProvider(3, 5, AiVendor.OPENAI, "enc:v1:broken", null)));

        assertEquals("sk-sealed", service.stationKey(3, AiVendor.OPENAI).orElseThrow());
        assertEquals("sk-plain", service.stationKey(4, AiVendor.OPENAI).orElseThrow());
        assertTrue(service.stationKey(5, AiVendor.OPENAI).isEmpty());
        assertTrue(service.stationKey(6, AiVendor.OPENAI).isEmpty());
    }

    @Test
    void plaintextStationKeysAreSealedOnceAndOnlyWhileUnchanged() {
        when(stations.findWithoutPrefix(CredentialCipher.SEALED_PREFIX))
                .thenReturn(List.of(
                        new StationAiProvider(1, 3, AiVendor.OPENAI, "sk-one", null),
                        new StationAiProvider(2, 4, AiVendor.CLAUDE, "sk-two", null)));
        when(stations.replaceKeyIfUnchanged(eq(1), eq("sk-one"), anyString())).thenReturn(true);
        when(stations.replaceKeyIfUnchanged(eq(2), eq("sk-two"), anyString())).thenReturn(false);

        assertEquals(1, service.sealLegacyStationKeys());

        var sealed = ArgumentCaptor.forClass(String.class);
        verify(stations).replaceKeyIfUnchanged(eq(1), eq("sk-one"), sealed.capture());
        assertEquals("sk-one", cipher.unseal(sealed.getValue()));
    }

    @Test
    void nothingToSealWritesNothing() {
        assertEquals(0, service.sealLegacyStationKeys());
    }

    @Test
    void aNewKeyIsWrittenSealedWithoutItsSurroundingSpace() {
        assertEquals(SaveOutcome.SAVED, service.save(7, AiVendor.OPENAI, " gpt-4o ", " sk-secret-1234 "));

        var sealed = ArgumentCaptor.forClass(String.class);
        verify(repository).save(eq(7), eq(AiVendor.OPENAI), eq("gpt-4o"), sealed.capture());
        assertTrue(CredentialCipher.isSealed(sealed.getValue()));
        assertEquals("sk-secret-1234", cipher.unseal(sealed.getValue()));
    }

    @Test
    void withoutANewKeyTheStoredOneIsKeptForTheSameProviderOnly() {
        String stored = cipher.seal("sk-kept-5678");
        when(repository.find(7))
                .thenReturn(Optional.of(new AccountAiCredential(7, AiVendor.OPENAI, null, stored, Instant.now())));

        assertEquals(SaveOutcome.SAVED, service.save(7, AiVendor.OPENAI, "", null));
        verify(repository).save(7, AiVendor.OPENAI, null, stored);

        assertEquals(SaveOutcome.KEY_MISSING, service.save(7, AiVendor.CLAUDE, "", " "));
        assertEquals(SaveOutcome.KEY_MISSING, service.save(8, AiVendor.OPENAI, "", null));
    }

    @Test
    void theSummaryShowsOnlyTheEnd() {
        when(repository.find(7))
                .thenReturn(Optional.of(new AccountAiCredential(
                        7, AiVendor.CLAUDE, "sonnet", cipher.seal("sk-ant-9876"), Instant.now())));
        when(repository.find(8))
                .thenReturn(Optional.of(
                        new AccountAiCredential(8, AiVendor.CLAUDE, null, cipher.seal("abc"), Instant.now())));

        assertEquals(
                new AiCredentialSummary(AiVendor.CLAUDE, "sonnet", true, "9876"),
                service.summary(7).orElseThrow());
        assertEquals("", service.summary(8).orElseThrow().keyEnding());
        assertTrue(service.summary(9).isEmpty());
    }

    @Test
    void aKeyThatNoLongerOpensCountsAsMissing() {
        String sealedElsewhere = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[] {
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,
                    28, 29, 30, 31, 32
                }))
                .seal("sk-lost");
        when(repository.find(7))
                .thenReturn(
                        Optional.of(new AccountAiCredential(7, AiVendor.OPENAI, null, sealedElsewhere, Instant.now())));

        assertEquals(
                new AiCredentialSummary(AiVendor.OPENAI, null, false, null),
                service.summary(7).orElseThrow());
        assertTrue(service.keyFor(7, AiVendor.OPENAI).isEmpty());
        assertEquals(SaveOutcome.KEY_MISSING, service.save(7, AiVendor.OPENAI, null, null));
    }

    @Test
    void theKeyIsUsedForItsOwnProviderOnly() {
        when(repository.find(7))
                .thenReturn(Optional.of(
                        new AccountAiCredential(7, AiVendor.GEMINI, null, cipher.seal("g-key"), Instant.now())));

        assertEquals("g-key", service.keyFor(7, AiVendor.GEMINI).orElseThrow());
        assertTrue(service.keyFor(7, AiVendor.OPENAI).isEmpty());
        assertNotEquals(Optional.of("g-key"), service.keyFor(8, AiVendor.GEMINI));
    }

    @Test
    void forgettingIsTheRepositorysAnswer() {
        when(repository.delete(7)).thenReturn(true);

        assertTrue(service.delete(7));
    }
}
