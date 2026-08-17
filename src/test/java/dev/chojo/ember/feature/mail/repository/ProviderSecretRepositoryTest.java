/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The signing secrets a provider issued to a station.
 *
 * <p>A station keeps one per provider, and clearing it is how the signature check is switched back
 * off - so an empty value has to remove the row rather than store emptiness, which would leave the
 * check on with nothing to check against.
 */
class ProviderSecretRepositoryTest extends RepositoryTestBase {

    private static final ProviderSecretRepository repository = new ProviderSecretRepository();
    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Provider Secret Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void aSecretIsStoredAndFoundAgain() {
        repository.store(station.id(), MailProviderType.SWEEGO, "the-secret");

        assertEquals(
                "the-secret",
                repository.find(station.id(), MailProviderType.SWEEGO).orElseThrow());
    }

    @Test
    void storingAgainReplacesTheOldOne() {
        repository.store(station.id(), MailProviderType.SWEEGO, "first");
        repository.store(station.id(), MailProviderType.SWEEGO, "second");

        assertEquals(
                "second", repository.find(station.id(), MailProviderType.SWEEGO).orElseThrow());
    }

    @Test
    void eachProviderKeepsItsOwn() {
        repository.store(station.id(), MailProviderType.SWEEGO, "sweego-secret");
        repository.store(station.id(), MailProviderType.BREVO, "brevo-secret");

        assertEquals(
                "sweego-secret",
                repository.find(station.id(), MailProviderType.SWEEGO).orElseThrow());
        assertEquals(
                "brevo-secret",
                repository.find(station.id(), MailProviderType.BREVO).orElseThrow());
    }

    @Test
    void anEmptyValueRemovesTheSecret() {
        repository.store(station.id(), MailProviderType.SWEEGO, "to-be-removed");
        repository.store(station.id(), MailProviderType.SWEEGO, "");

        assertTrue(repository.find(station.id(), MailProviderType.SWEEGO).isEmpty());
        repository.store(station.id(), MailProviderType.SWEEGO, null);
        assertTrue(repository.find(station.id(), MailProviderType.SWEEGO).isEmpty());
    }

    @Test
    void aStationWithoutOneAnswersEmpty() {
        assertTrue(repository.find(station.id(), MailProviderType.TWILIO).isEmpty());
    }
}
