/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.MailProviderEntry;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import dev.chojo.ember.feature.mail.repository.InstanceMailGrantRepository;
import dev.chojo.ember.feature.mail.repository.ProviderSecretRepository;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.mail.repository.StationMailSenderRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order a mail is tried through.
 *
 * <p>A station that has taken its outgoing mail into its own hands keeps it there: its chain is its
 * own, and it runs into the instance's only where an instance administrator granted that, and then
 * only after its own providers. That is the property worth pinning, because getting it wrong would
 * send a station's post out under somebody else's sender without anyone asking.
 */
class MailChainServiceTest extends RepositoryTestBase {

    private static final StationMailProviderRepository providers = new StationMailProviderRepository();
    private static final InstanceMailGrantRepository grants = new InstanceMailGrantRepository();
    private static final StationMailSenderRepository senders = new StationMailSenderRepository();
    private static MailChainService service;
    private static MailChainService withInstanceList;
    private static Station station;
    private static Station granted;

    @BeforeAll
    static void setup() {
        service = new MailChainService(new Mailing(), providers, new ProviderSecretRepository(), grants, senders);
        var mailing = new Mailing();
        setField(
                mailing,
                "providers",
                List.of(instanceProvider(MailProviderType.BREVO), instanceProvider(MailProviderType.SWEEGO)));
        withInstanceList = new MailChainService(mailing, providers, new ProviderSecretRepository(), grants, senders);
        station = stationRepo.create("Chain Station");
        granted = stationRepo.create("Chain Station Granted");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(granted.id());
    }

    private static MailProviderEntry instanceProvider(MailProviderType provider) {
        return new MailProviderEntry(
                provider, "", 587, SmtpEncryption.STARTTLS, "user", "secret", "key", "post@instance", "Ember", 2, 100);
    }

    /**
     * Without a grant the instance's list stays out of a station's chain, whatever the instance has.
     */
    @Test
    void aStationWithoutAGrantNeverGetsTheInstanceList() {
        assertTrue(withInstanceList.forStation(station.id()).isEmpty());
    }

    /**
     * A granted station without providers of its own sends through the instance's list alone, every
     * entry marked with its place in that list so its allowance is counted there.
     */
    @Test
    void aGrantedStationWithoutItsOwnUsesOnlyTheInstanceList() {
        grants.grant(granted.id(), null);
        providers.replace(granted.id(), List.of());

        var chain = withInstanceList.forStation(granted.id());

        assertEquals(2, chain.size());
        assertEquals(MailProviderType.BREVO, chain.get(0).provider());
        assertEquals(0, chain.get(0).instancePosition());
        assertEquals(1, chain.get(1).instancePosition());
        assertEquals("post@instance", chain.get(0).senderAddress());
    }

    /**
     * Own providers first, the instance's after them: the instance carries the post only once the
     * station's own providers have nothing left to give.
     */
    @Test
    void aGrantedStationTriesItsOwnProvidersBeforeTheInstances() {
        grants.grant(granted.id(), 20);
        providers.replace(granted.id(), List.of(fallback(0, MailProviderType.SMTP, 2)));

        var chain = withInstanceList.forStation(granted.id());

        assertEquals(3, chain.size());
        assertEquals(MailProviderType.SMTP, chain.get(0).provider());
        assertFalse(chain.get(0).isInstanceProvider(), "the station's own comes first");
        assertEquals(MailProviderType.BREVO, chain.get(1).provider());
        assertEquals(1, chain.get(1).position());
        assertEquals(0, chain.get(1).instancePosition());
        assertEquals(MailProviderType.SWEEGO, chain.get(2).provider());
        assertEquals(2, chain.get(2).position());
        assertEquals(1, chain.get(2).instancePosition());
        assertEquals(1, withInstanceList.ownForStation(granted.id()).size(), "its own list is just its own");
        assertEquals(
                MailProviderType.SMTP,
                withInstanceList.firstForStation(granted.id()).orElseThrow().provider());
    }

    /**
     * The instance's providers send from the instance's address, the one they have authorised, but
     * under the station's name, so the reader sees who wrote. A reply goes to the station.
     */
    @Test
    void theInstanceSendsUnderTheStationsNameWithRepliesToTheStation() {
        grants.grant(granted.id(), null);
        providers.replace(granted.id(), List.of(fallback(0, MailProviderType.SMTP, 2)));
        senders.updateReplyTo(granted.id(), "kontakt@wache.test");

        var chain = withInstanceList.forStation(granted.id());

        assertEquals("post@instance", chain.get(1).senderAddress(), "the instance's own address");
        assertEquals("Chain Station Granted", chain.get(1).senderName(), "the station's name");
        assertEquals("kontakt@wache.test", chain.get(1).replyTo());
        assertEquals("Wache", chain.getFirst().senderName(), "its own provider keeps its own name");
        assertEquals("kontakt@wache.test", chain.getFirst().replyTo(), "replies go to the station through any");

        senders.updateReplyTo(granted.id(), null);
        var withoutReplyAddress = withInstanceList.forStation(granted.id());
        assertEquals("", withoutReplyAddress.get(1).replyTo(), "no reply address, replies go to the sender");
        assertEquals("", withoutReplyAddress.getFirst().replyTo());
    }

    @Test
    void aWithdrawnGrantTakesTheInstanceListOutAgain() {
        grants.grant(granted.id(), null);
        grants.withdraw(granted.id());

        assertTrue(withInstanceList.forStation(granted.id()).stream().noneMatch(MailChainEntry::isInstanceProvider));
    }

    private static MailChainEntry fallback(int position, MailProviderType provider, int attempts) {
        return new MailChainEntry(
                position,
                provider,
                "smtp.example",
                587,
                SmtpEncryption.STARTTLS,
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
        var withList = new MailChainService(mailing, providers, new ProviderSecretRepository(), grants, senders);

        assertTrue(withList.forInstance().isEmpty(), "a bare configuration lists nothing");

        setField(
                mailing,
                "providers",
                List.of(new MailProviderEntry(
                        MailProviderType.BREVO,
                        "",
                        587,
                        SmtpEncryption.STARTTLS,
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
        assertEquals(0, chain.getFirst().instancePosition(), "the instance's own entries carry their place");
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
