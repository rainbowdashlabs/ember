/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.repository.ProviderSecretRepository;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationMailConfig;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order a mail is tried through.
 *
 * <p>A station that has taken its outgoing mail into its own hands keeps it there: its chain is its
 * own, and it never runs into the instance's. That is the property worth pinning, because getting it
 * wrong would send a station's post out under somebody else's sender without anyone asking.
 */
class MailChainServiceTest extends RepositoryTestBase {

    private static final StationMailProviderRepository providers = new StationMailProviderRepository();
    private static MailChainService service;
    private static Station station;

    @BeforeAll
    static void setup() {
        service = new MailChainService(new Mailing(), stationMailConfigRepo, providers, new ProviderSecretRepository());
        station = stationRepo.create("Chain Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static MailChainEntry fallback(int position, MailProviderType provider, int attempts) {
        return new MailChainEntry(
                position,
                provider,
                "smtp.example",
                587,
                false,
                "user",
                "secret",
                "key",
                "post@example",
                "Wache",
                attempts);
    }

    /**
     * A station that sends through the instance has no chain of its own - and must not silently
     * inherit one.
     */
    @Test
    void aStationWithoutItsOwnProviderHasNoChain() {
        assertTrue(service.forStation(station.id()).isEmpty());
    }

    @Test
    void aStationsOwnProviderComesFirstAndItsFallbacksFollow() {
        stationMailConfigRepo.upsert(new StationMailConfig(
                station.id(),
                MailProviderType.SMTP,
                "own.example",
                587,
                false,
                "own",
                "own-secret",
                "post@wache",
                "Wache",
                "",
                "",
                "",
                100,
                2000));
        providers.replace(
                station.id(), List.of(fallback(1, MailProviderType.BREVO, 3), fallback(2, MailProviderType.SWEEGO, 1)));

        var chain = service.forStation(station.id());

        assertEquals(3, chain.size());
        assertEquals(MailProviderType.SMTP, chain.get(0).provider());
        assertEquals("own.example", chain.get(0).smtpHost());
        assertEquals(MailProviderType.BREVO, chain.get(1).provider());
        assertEquals(3, chain.get(1).attempts());
        assertEquals(MailProviderType.SWEEGO, chain.get(2).provider());
    }

    /**
     * Positions are renumbered onto what is actually usable, so a chain with an unconfigured entry
     * in it still reads as an order rather than stopping at the gap.
     */
    @Test
    void anEntryWithoutAProviderIsLeftOut() {
        providers.replace(
                station.id(), List.of(fallback(1, MailProviderType.NONE, 2), fallback(2, MailProviderType.BREVO, 2)));

        var chain = service.forStation(station.id());

        assertEquals(2, chain.size());
        assertEquals(MailProviderType.BREVO, chain.get(1).provider());
        assertEquals(1, chain.get(1).position());
    }

    @Test
    void theEntryInTurnIsTheOneAtThatPosition() {
        var chain = service.forStation(station.id());

        assertEquals(0, service.at(chain, 0).orElseThrow().position());
        assertTrue(service.at(chain, chain.size()).isEmpty(), "past the end there is nothing left to try");
        assertTrue(service.at(chain, -1).isEmpty());
    }
}
