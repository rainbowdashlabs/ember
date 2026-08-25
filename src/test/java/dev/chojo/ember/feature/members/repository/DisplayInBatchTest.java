/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The colour and the tag a list of people show, fetched for the whole list at once.
 *
 * <p>Asking per member costs a round trip a row. Measured on two hundred, that was 432ms of a page load
 * against 13ms for the two queries here, and it is what the association's member list was paying to draw
 * a name. What the batch answers has to be what the one-at-a-time answer was, which is what this pins.
 */
class DisplayInBatchTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    @Test
    void theColourIsTheHighestPlacedGroupThatCarriesOne() {
        int n = NAMES.incrementAndGet();
        var station = stationRepo.create("Wache Farbe " + n);
        var account = accountRepo.create("farbe" + n + "@test.com", "Farb", "Wert" + n);
        var member = stationMemberRepo.create(station.id(), account.id());

        var low = memberGroupRepo.create(station.id(), "Unten " + n);
        var high = memberGroupRepo.create(station.id(), "Oben " + n);
        var colourless = memberGroupRepo.create(station.id(), "Ohne Farbe " + n);
        memberGroupRepo.update(low.id(), low.name(), "#111111", 1);
        memberGroupRepo.update(high.id(), high.name(), "#222222", 5);
        memberGroupRepo.update(colourless.id(), colourless.name(), null, 9);
        for (var group : List.of(low, high, colourless)) memberGroupRepo.addMember(group.id(), member.id());

        var colors = memberGroupRepo.findNameColors(List.of(member.id()));

        assertEquals("#222222", colors.get(member.id()), "the highest placed group that has a colour wins");

        memberGroupRepo.delete(low.id());
        memberGroupRepo.delete(high.id());
        memberGroupRepo.delete(colourless.id());
        stationMemberRepo.delete(member.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aMemberInNoColouredGroupIsAbsentRatherThanNull() {
        int n = NAMES.incrementAndGet();
        var station = stationRepo.create("Wache Farblos " + n);
        var account = accountRepo.create("farblos" + n + "@test.com", "Farblos", "Wert" + n);
        var member = stationMemberRepo.create(station.id(), account.id());

        assertTrue(memberGroupRepo.findNameColors(List.of(member.id())).isEmpty());
        assertTrue(memberGroupRepo.findNameColors(List.of()).isEmpty(), "and nothing asked for is nothing answered");

        stationMemberRepo.delete(member.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /**
     * A tag nobody can see is not a tag anybody wears, which is the rule the one-at-a-time path applied
     * after fetching every tag the member had.
     */
    @Test
    void theTagIsTheHighestPlacedVisibleOne() {
        int n = NAMES.incrementAndGet();
        var station = stationRepo.create("Wache Tag " + n);
        var account = accountRepo.create("tag" + n + "@test.com", "Tag", "Wert" + n);
        var member = stationMemberRepo.create(station.id(), account.id());

        var low = userTagRepo.create(station.id(), "Unten " + n);
        var high = userTagRepo.create(station.id(), "Oben " + n);
        var hidden = userTagRepo.create(station.id(), "Versteckt " + n);
        userTagRepo.update(low.id(), low.name(), "#111111", true, 1);
        userTagRepo.update(high.id(), high.name(), "#222222", true, 5);
        userTagRepo.update(hidden.id(), hidden.name(), "#333333", false, 9);
        for (var tag : List.of(low, high, hidden)) userTagRepo.addMember(tag.id(), member.id());

        var tags = userTagRepo.findDisplayTags(List.of(member.id()));

        assertEquals("#222222", tags.get(member.id()).color(), "the hidden one above it is not worn");
        assertEquals(high.name(), tags.get(member.id()).name());

        userTagRepo.update(high.id(), high.name(), "#222222", false, 5);
        assertEquals(
                "#111111",
                userTagRepo
                        .findDisplayTags(List.of(member.id()))
                        .get(member.id())
                        .color(),
                "hiding the top one falls to the next visible, not past it to the one above");

        userTagRepo.update(low.id(), low.name(), "#111111", false, 1);
        assertNull(
                userTagRepo.findDisplayTags(List.of(member.id())).get(member.id()),
                "and with none visible there is nothing to wear");

        userTagRepo.delete(low.id());
        userTagRepo.delete(high.id());
        userTagRepo.delete(hidden.id());
        stationMemberRepo.delete(member.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /** Every member asked for is answered for, which is what makes one query safe to swap in for many. */
    @Test
    void aWholeListIsAnsweredInOneGo() {
        int n = NAMES.incrementAndGet();
        var station = stationRepo.create("Wache Liste " + n);
        var group = memberGroupRepo.create(station.id(), "Bunt " + n);
        memberGroupRepo.update(group.id(), group.name(), "#abcdef", 1);

        var accounts = new java.util.ArrayList<Integer>();
        var members = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 5; i++) {
            var account = accountRepo.create("liste" + n + "-" + i + "@test.com", "Liste", "Wert" + i);
            accounts.add(account.id());
            int memberId = stationMemberRepo.create(station.id(), account.id()).id();
            members.add(memberId);
            if (i % 2 == 0) memberGroupRepo.addMember(group.id(), memberId);
        }

        var colors = memberGroupRepo.findNameColors(members);

        assertEquals(3, colors.size(), "the three in the coloured group and nobody else");
        for (int i = 0; i < members.size(); i++) {
            assertEquals(i % 2 == 0 ? "#abcdef" : null, colors.get(members.get(i)));
        }

        for (int memberId : members) stationMemberRepo.delete(memberId);
        memberGroupRepo.delete(group.id());
        stationRepo.delete(station.id());
        for (int accountId : accounts) accountRepo.delete(accountId);
    }
}
