/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.entity.StationRef;
import dev.chojo.ember.feature.messages.entity.StationScope;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What a conversation is called for one reader, the same in the daily mail and on a push: a station
 * chat by its name and station, a group by its name or the first names of the others, a direct
 * conversation by the other person. Where nobody else takes part any more, the people who ever did
 * name it.
 */
public final class ConversationTitles {
    private ConversationTitles() {}

    /**
     * The title of a conversation for one reader.
     *
     * @param conversation the conversation
     * @param readerId     the reader's account
     * @param stations     the stations of the station chats among the conversations being named
     * @param members      the conversation's members taking part now
     * @param everyMember  everybody who ever took part, asked only where nobody else takes part now
     * @return the title
     */
    public static String titleOf(
            Conversation conversation,
            int readerId,
            Map<Integer, StationRef> stations,
            List<Membership> members,
            Supplier<List<Membership>> everyMember) {
        StationScope scope = conversation.station();
        String name = conversation.name();
        if (scope != null) {
            StationRef station = stations.get(scope.stationId());
            String chat = Objects.requireNonNullElse(name, "");
            return station == null ? chat : chat + " (" + station.name() + ")";
        }
        if (name != null) return name;
        List<Person> others = othersIn(members, readerId);
        if (others.isEmpty()) others = othersIn(everyMember.get(), readerId);
        if (others.isEmpty()) return "?";
        if (conversation.isDirect()) return nameOf(others.getFirst());
        return others.stream().map(Person::firstName).collect(Collectors.joining(", "));
    }

    /** A person's full name, or a question mark where nobody is known. */
    public static String nameOf(@Nullable Person person) {
        if (person == null) return "?";
        return (person.firstName() + " " + person.lastName()).strip();
    }

    private static List<Person> othersIn(List<Membership> members, int readerId) {
        return members.stream()
                .filter(member -> member.accountId() != readerId)
                .map(Membership::person)
                .toList();
    }
}
