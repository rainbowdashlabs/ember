/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.entity.ReachablePerson;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blocking, finding people to write to, the guardian switch, and the status the header reads.
 */
class BlockAndSettingsTest extends MessageTestBase {
    private MessageFixtures services;
    private Station station;
    private Account anna;
    private Account ben;

    @BeforeEach
    void people() {
        services = MessageFixtures.build();
        station = station();
        anna = memberOf(station, "Anna");
        ben = memberOf(station, "Ben");
    }

    @Test
    void aBlockIsListedAndTakenBack() {
        services.blocks().block(anna.id(), ben.uid());
        services.blocks().block(anna.id(), ben.uid());

        assertEquals(
                List.of(ben.uid()),
                services.blocks().blocked(anna.id()).stream().map(Person::uid).toList());

        services.blocks().unblock(anna.id(), ben.uid());
        services.blocks().unblock(anna.id(), UUID.randomUUID());

        assertTrue(services.blocks().blocked(anna.id()).isEmpty());
    }

    @Test
    void nobodyBlocksThemselvesOrSomebodyWhoIsNotHere() {
        assertRefused(MessageRefusal.BLOCK_SELF, () -> services.blocks().block(anna.id(), anna.uid()));
        assertRefused(
                MessageRefusal.BLOCK_PERSON_NOT_HERE, () -> services.blocks().block(anna.id(), UUID.randomUUID()));
        assertRefused(
                MessageRefusal.BLOCK_PERSON_NOT_HERE, () -> services.blocks().block(anna.id(), null));
    }

    @Test
    void theMessagesOfABlockedPersonAreNotUnread() {
        var group = services.groups().create(anna.id(), List.of(ben.uid()), null);
        services.messages().send(ben.id(), group.uid(), "Hallo", null);
        assertEquals(1, services.settings().status(anna.id()).unread());

        services.blocks().block(anna.id(), ben.uid());

        assertEquals(0, services.settings().status(anna.id()).unread());
    }

    @Test
    void theSearchFindsPeopleOfEveryCommonStationWithAHint() {
        var other = station();
        join(other, anna);
        var cora = memberOf(other, "Cora");
        memberOf(station(), "Fremd");

        List<ReachablePerson> everybody = services.settings().reachable(anna.id(), "");
        assertEquals(
                List.of(ben.uid(), cora.uid()),
                everybody.stream().map(found -> found.person().uid()).toList());
        assertEquals(other.name(), everybody.get(1).stationName());

        assertEquals(
                List.of(cora.uid()),
                services.settings().reachable(anna.id(), "cora muster").stream()
                        .map(found -> found.person().uid())
                        .toList());
    }

    @Test
    void aChildWithAGuardianHasNoMessagesUntilTheGuardianSaysSo() {
        var guardian = memberOf(station, "Gerda");
        var child = account("Kind");
        var childMember = join(station, child);
        guard(stationMemberRepo.findAllByAccountId(guardian.id()).getFirst(), childMember);

        assertFalse(services.settings().status(child.id()).available());
        assertRefused(MessageRefusal.MESSAGES_NOT_TURNED_ON, () -> services.conversations()
                .list(child.id()));
        assertRefused(MessageRefusal.PERSON_NOT_REACHABLE, () -> services.conversations()
                .openDirect(anna.id(), child.uid()));
        assertTrue(services.settings().reachable(anna.id(), "Kind").isEmpty());

        services.settings().setGuardianAllowed(child.id(), true);

        assertTrue(services.settings().guardianAllowed(child.id()));
        assertTrue(services.settings().status(child.id()).available());
        services.conversations().openDirect(anna.id(), child.uid());
    }

    @Test
    void aChildWhoseLastGuardianWentIsLikeEverybodyElse() {
        var guardian = memberOf(station, "Gerda");
        var child = account("Kind");
        var childMember = join(station, child);
        var guardianMember = stationMemberRepo.findAllByAccountId(guardian.id()).getFirst();
        guard(guardianMember, childMember);
        assertFalse(services.settings().status(child.id()).available());

        stationMemberRepo.setFormer(guardianMember.id(), true);

        assertTrue(services.settings().status(child.id()).available());
    }

    @Test
    void theMailCanBeTurnedOff() {
        assertTrue(services.settings().status(anna.id()).mail());

        assertFalse(services.settings().setMail(anna.id(), false).mail());
        assertTrue(services.settings().setMail(anna.id(), true).mail());
    }

    @Test
    void anAccountThatIsNotThereHasNoMessages() {
        assertFalse(services.settings().status(-1).available());
        assertFalse(services.settings().status(-1).mail());
        assertFalse(services.settings().guardianAllowed(-1));
    }
}
