/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.MailProviderEntry;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.repository.ProviderSecretRepository;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.Station;
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
        service = new MailChainService(new Mailing(), providers, new ProviderSecretRepository());
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
                attempts,
                0,
                "",
                "");
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
        providers.replace(
                station.id(),
                List.of(
                        fallback(0, MailProviderType.SMTP, 2),
                        fallback(1, MailProviderType.BREVO, 3),
                        fallback(2, MailProviderType.SWEEGO, 1)));

        var chain = service.forStation(station.id());

        assertEquals(3, chain.size());
        assertEquals(MailProviderType.SMTP, chain.get(0).provider());
        assertEquals("smtp.example", chain.get(0).smtpHost());
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
                station.id(),
                List.of(
                        fallback(0, MailProviderType.SMTP, 2),
                        fallback(1, MailProviderType.NONE, 2),
                        fallback(2, MailProviderType.BREVO, 2)));

        var chain = service.forStation(station.id());

        assertEquals(2, chain.size());
        assertEquals(MailProviderType.BREVO, chain.get(1).provider());
        assertEquals(1, chain.get(1).position());
    }

    /**
     * An instance that has saved its list has nothing left in the fields a single provider used to
     * live in. Anything asking those fields whether mail is configured therefore has to be asking
     * the list instead, or it answers no while three providers are listed, and the queue stops
     * fetching instance mail altogether.
     */
    @Test
    void theInstanceListIsReadFromTheListRatherThanTheOldFields() {
        var mailing = new Mailing();
        var withList = new MailChainService(mailing, providers, new ProviderSecretRepository());

        assertTrue(withList.forInstance().isEmpty(), "a bare configuration lists nothing");

        setField(
                mailing,
                "providers",
                List.of(new MailProviderEntry(
                        MailProviderType.BREVO,
                        "",
                        587,
                        false,
                        "user",
                        "secret",
                        "key",
                        "post@example",
                        "Ember",
                        2,
                        0)));

        var chain = withList.forInstance();

        assertEquals(1, chain.size(), "the list is what counts");
        assertEquals(MailProviderType.BREVO, chain.getFirst().provider());
        assertEquals("post@example", chain.getFirst().senderAddress());
    }

    private static void setField(Object target, String field, Object value) {
        try {
            var declared = target.getClass().getDeclaredField(field);
            declared.setAccessible(true);
            declared.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theEntryInTurnIsTheOneAtThatPosition() {
        var chain = service.forStation(station.id());

        assertEquals(0, service.at(chain, 0).orElseThrow().position());
        assertTrue(service.at(chain, chain.size()).isEmpty(), "past the end there is nothing left to try");
        assertTrue(service.at(chain, -1).isEmpty());
    }
}
