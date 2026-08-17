/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.webhook.service;

import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.File;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.webhook.repository.WebhookKeyRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The key an outside tool presents when it reports something to Ember.
 *
 * <p>Two properties carry the whole design. Nobody is ever asked to invent a key - it exists the
 * first time it is wanted. And a key says who the caller speaks for: the instance's own key answers
 * for everything, a station's only for that station.
 */
class WebhookKeyServiceTest extends RepositoryTestBase {

    private static final WebhookKeyRepository repository = new WebhookKeyRepository();
    private static WebhookKeyService service;
    private static Mailing mailing;
    private static Station station;

    @BeforeAll
    static void setup() {
        mailing = new Mailing();
        File file = mock(File.class);
        when(file.mailing()).thenReturn(mailing);
        Conf conf = mock(Conf.class);
        when(conf.main()).thenReturn(file);
        doNothing().when(conf).save();

        service = new WebhookKeyService(conf, repository);
        station = stationRepo.create("Webhook Key Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void theInstanceKeyExistsTheFirstTimeItIsWanted() {
        String first = service.instanceKey();

        assertFalse(first.isBlank(), "an operator is never asked to invent one");
        assertEquals(first, service.instanceKey(), "and it stays the same once it exists");
    }

    @Test
    void aStationGetsItsOwnKey() {
        String key = service.stationKey(station.id());

        assertFalse(key.isBlank());
        assertEquals(key, service.stationKey(station.id()));
        assertNotEquals(service.instanceKey(), key, "a station does not share the instance's key");
    }

    @Test
    void aKeySaysWhoTheCallerSpeaksFor() {
        var asInstance = service.resolve(service.instanceKey()).orElseThrow();
        var asStation = service.resolve(service.stationKey(station.id())).orElseThrow();

        assertTrue(asInstance.isInstance());
        assertEquals(station.id(), asStation.stationId());
    }

    @Test
    void anythingElseAuthorisesNothing() {
        assertTrue(service.resolve("not-a-key").isEmpty());
        assertTrue(service.resolve("").isEmpty());
        assertTrue(service.resolve(null).isEmpty());
    }

    /**
     * Replacing a key is what an operator does when the address has been seen by the wrong person,
     * so the old one has to stop working at once.
     */
    @Test
    void replacingAKeyRetiresTheOldOne() {
        String old = service.stationKey(station.id());
        String replaced = service.regenerate(station.id());

        assertNotEquals(old, replaced);
        assertTrue(service.resolve(old).isEmpty());
        assertEquals(station.id(), service.resolve(replaced).orElseThrow().stationId());
    }

    @Test
    void theAddressCarriesTheKeyAndTheReport() {
        String url = service.webhookUrl("https://ember.example/", null, "mail/brevo");

        assertEquals("https://ember.example/api/v1/public/webhooks/" + service.instanceKey() + "/mail/brevo", url);
    }
}
