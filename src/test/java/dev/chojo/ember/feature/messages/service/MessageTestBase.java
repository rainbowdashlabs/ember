/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The people message tests write between: stations, accounts and memberships on the test
 * database, each with a name of its own so classes never meet, and the message services on top.
 */
public abstract class MessageTestBase extends RepositoryTestBase {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /** A station of its own. */
    protected static Station station() {
        return stationRepo.create("Messages " + System.nanoTime() + "-" + SEQUENCE.incrementAndGet());
    }

    /** An account of its own with a real address. */
    protected static Account account(String firstName) {
        return accountRepo.create(
                "messages-%d-%d@test.com".formatted(System.nanoTime(), SEQUENCE.incrementAndGet()),
                firstName,
                "Muster");
    }

    /** An account that is a current member of the station. */
    protected static Account memberOf(Station station, String firstName) {
        Account account = account(firstName);
        join(station, account);
        return account;
    }

    /** Makes an account a current member of a station. */
    protected static StationMember join(Station station, Account account) {
        return stationMemberRepo.create(station.id(), account.id());
    }

    /** Makes one member the guardian of another. */
    protected static void guard(StationMember guardian, StationMember child) {
        stationMemberRepo.addManager(guardian.id(), child.id());
    }

    /** Asserts that the action is refused with exactly this refusal. */
    protected static void assertRefused(Refusal expected, Executable action) {
        assertEquals(expected, assertThrows(RefusalResponse.class, action).refusal());
    }

    /** A stream that keeps what it is sent. */
    public static final class RecordingStream implements MessageStream {
        /** The names of the events sent, in order. */
        public final List<String> events = new ArrayList<>();

        /** What each event carried, in order. */
        public final List<Object> data = new ArrayList<>();

        /** Whether the tab is still there; a send to a closed one fails. */
        public boolean open = true;

        @Override
        public boolean send(String event, Object payload) {
            if (!open) return false;
            events.add(event);
            data.add(payload);
            return true;
        }

        /** The signals sent, in order. */
        public List<MessageEvent> signals() {
            return data.stream()
                    .filter(MessageEvent.class::isInstance)
                    .map(MessageEvent.class::cast)
                    .toList();
        }
    }
}
