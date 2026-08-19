/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailProviderBlockRepositoryTest extends RepositoryTestBase {

    private final MailProviderBlockRepository repository = new MailProviderBlockRepository();

    @Test
    void aBlockedProviderIsRememberedForTheWholeDomain() {
        repository.block(null, MailProviderType.BREVO, "somebody@example.org", "on a block list");

        assertTrue(repository.blockedFor(null, "somebody@example.org").contains(MailProviderType.BREVO));
        assertTrue(
                repository.blockedFor(null, "anybody.else@example.org").contains(MailProviderType.BREVO),
                "what is refused is the relay at that domain, not the one address");
        assertFalse(
                repository.blockedFor(null, "somebody@other.example").contains(MailProviderType.BREVO),
                "and nothing is said about another domain");
    }

    @Test
    void onlyTheProviderThatWasRefusedIsBlocked() {
        repository.block(null, MailProviderType.BREVO, "post@example.net", null);

        var blocked = repository.blockedFor(null, "post@example.net");

        assertEquals(1, blocked.size());
        assertFalse(blocked.contains(MailProviderType.SWEEGO), "the next provider is still worth trying");
    }

    /**
     * A block belongs to the list it was learned on. A station finding its own relay refused says
     * nothing about the instance's, which is a different account at a different service.
     */
    @Test
    void aStationsBlockDoesNotReachTheInstance() {
        var station = stationRepo.create("Blocked Station");
        try {
            repository.block(station.id(), MailProviderType.BREVO, "post@example.com", null);

            assertTrue(repository.blockedFor(station.id(), "post@example.com").contains(MailProviderType.BREVO));
            assertTrue(repository.blockedFor(null, "post@example.com").isEmpty());
        } finally {
            stationRepo.delete(station.id());
        }
    }

    @Test
    void blockingTwiceKeepsOneEntryAndPushesTheExpiryOut() {
        repository.block(null, MailProviderType.SWEEGO, "post@twice.example", "first");
        var first = repository.list(null).stream()
                .filter(entry -> entry.recipientDomain().equals("twice.example"))
                .findFirst()
                .orElseThrow();

        repository.block(null, MailProviderType.SWEEGO, "someone@twice.example", "second");
        var after = repository.list(null).stream()
                .filter(entry -> entry.recipientDomain().equals("twice.example"))
                .toList();

        assertEquals(1, after.size(), "one pairing, one row");
        assertEquals("second", after.getFirst().reason(), "the newer reason wins");
        assertTrue(
                !after.getFirst().lastBlockedAt().isBefore(first.lastBlockedAt()), "and the refusal is dated forward");
    }

    @Test
    void aLiftedBlockIsGone() {
        repository.block(null, MailProviderType.TWILIO, "post@lift.example", null);
        assertFalse(repository.blockedFor(null, "post@lift.example").isEmpty());

        repository.lift(null, MailProviderType.TWILIO, "lift.example");

        assertTrue(repository.blockedFor(null, "post@lift.example").isEmpty());
    }

    @Test
    void anAddressWithoutADomainIsIgnoredRatherThanStored() {
        repository.block(null, MailProviderType.BREVO, "   ", "nonsense");
        repository.block(null, MailProviderType.BREVO, null, "nonsense");

        assertTrue(repository.blockedFor(null, "   ").isEmpty());
        assertTrue(repository.blockedFor(null, null).isEmpty());
    }

    /**
     * A block that has not lapsed is not swept away by the tidying, or a provider would be tried
     * again the moment the housekeeping ran.
     */
    @Test
    void pruningLeavesBlocksThatStillStand() {
        repository.block(null, MailProviderType.BREVO, "post@prune.example", null);

        repository.prune();

        assertFalse(repository.blockedFor(null, "post@prune.example").isEmpty());
    }

    @Test
    void theDomainIsTakenCaseInsensitively() {
        repository.block(null, MailProviderType.BREVO, "Post@Mixed.Example", null);

        assertTrue(repository.blockedFor(null, "other@MIXED.example").contains(MailProviderType.BREVO));
    }
}
