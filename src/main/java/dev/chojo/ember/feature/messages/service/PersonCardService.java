/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.members.entity.SharedMemberCard;
import dev.chojo.ember.feature.members.service.SharedMemberCards;
import dev.chojo.ember.feature.messages.repository.MessagePeopleRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

/**
 * The cards behind the names in messages. People there meet as accounts, so a person's card is
 * their member card at every station the reader shares with them.
 */
@Singleton
public class PersonCardService {
    private final MessagePeopleRepository people;
    private final SharedMemberCards cards;

    @Inject
    public PersonCardService(MessagePeopleRepository people, SharedMemberCards cards) {
        this.people = people;
        this.cards = cards;
    }

    /**
     * The person's cards at the stations shared with the reader.
     *
     * @param readerAccountId the account looking
     * @param personUid       the account looked at
     * @return one card per shared station, never empty
     * @throws dev.chojo.ember.api.refusal.RefusalResponse where no account carries the uid or it shares
     *                                                     no station with the reader
     */
    public List<SharedMemberCard> cardsOf(int readerAccountId, UUID personUid) {
        var shared = people.findByUid(personUid)
                .map(person -> cards.between(readerAccountId, person.accountId()))
                .orElse(List.of());
        if (shared.isEmpty()) throw MessageRefusal.CARD_PERSON_NOT_SHARED.raise();
        return shared;
    }
}
