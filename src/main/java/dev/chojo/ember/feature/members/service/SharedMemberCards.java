/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.SharedMemberCard;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.service.StationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The member cards of a person, one per station the reader and the person both belong to.
 *
 * <p>Where people meet outside a station, in messages, there is no station to read the card at. Each
 * shared station shows the card a reader there would see: private tags only where the reader may
 * view members at that station. A station only one of them belongs to shows nothing.
 */
@Singleton
public class SharedMemberCards {
    private final StationMemberService memberService;
    private final MemberPermissionResolver permissions;
    private final StationService stationService;
    private final MemberCardService cardService;

    @Inject
    public SharedMemberCards(
            StationMemberService memberService,
            MemberPermissionResolver permissions,
            StationService stationService,
            MemberCardService cardService) {
        this.memberService = memberService;
        this.permissions = permissions;
        this.stationService = stationService;
        this.cardService = cardService;
    }

    /**
     * The person's cards at the stations the reader shares with them, ordered by station name.
     *
     * @param readerAccountId the account looking
     * @param personAccountId the account looked at
     * @return one card per shared station, empty where they share none
     */
    public List<SharedMemberCard> between(int readerAccountId, int personAccountId) {
        Map<Integer, StationMember> personAt = currentMemberships(personAccountId).stream()
                .collect(Collectors.toMap(StationMember::stationId, Function.identity(), (first, second) -> first));
        return currentMemberships(readerAccountId).stream()
                .filter(reader -> personAt.containsKey(reader.stationId()))
                .flatMap(reader -> cardAt(reader, personAt.get(reader.stationId())).stream())
                .sorted(Comparator.comparing(shared -> shared.stationName().toLowerCase()))
                .toList();
    }

    private List<StationMember> currentMemberships(int accountId) {
        return memberService.findByAccount(accountId).stream()
                .filter(member -> !member.former())
                .toList();
    }

    private Optional<SharedMemberCard> cardAt(StationMember reader, StationMember person) {
        boolean privateTagsSeen = permissions.resolve(reader).contains(StationPermission.MEMBER_READ);
        return stationService.findById(reader.stationId()).flatMap(station -> cardService
                .find(station.id(), station.uid(), person.uid(), privateTagsSeen)
                .map(card -> new SharedMemberCard(station.uid(), station.name(), card)));
    }
}
