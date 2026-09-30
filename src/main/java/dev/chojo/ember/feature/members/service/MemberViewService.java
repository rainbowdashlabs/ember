/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.MemberWithName;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.RichMember;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Members as the administration screens show them: named, with the account behind them, and with
 * the answers the reader may read.
 */
@Singleton
public class MemberViewService {
    private final AccountRepository accountRepository;
    private final StationMemberRepository memberRepository;
    private final MemberIdentityFactory identityFactory;
    private final MemberNameResolver nameResolver;
    private final ProfileFieldService profileFieldService;

    @Inject
    public MemberViewService(
            AccountRepository accountRepository,
            StationMemberRepository memberRepository,
            MemberIdentityFactory identityFactory,
            MemberNameResolver nameResolver,
            ProfileFieldService profileFieldService) {
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.identityFactory = identityFactory;
        this.nameResolver = nameResolver;
        this.profileFieldService = profileFieldService;
    }

    /**
     * A member with name and account, counted as having a complete profile.
     *
     * @param member the member
     * @return the member as the lists show it
     */
    public MemberWithName named(StationMember member) {
        return MemberWithName.from(member, accountRepository, identityFactory, nameResolver);
    }

    /**
     * The same, saying whether the member still owes answers to the profile questions.
     *
     * @param member the member
     * @return the member as the register shows it
     */
    public MemberWithName withCompleteness(StationMember member) {
        boolean complete = profileFieldService.isProfileComplete(member.id());
        return MemberWithName.from(member, accountRepository, identityFactory, nameResolver, complete);
    }

    /**
     * Each member named, with completeness.
     *
     * @param members the members
     * @return the members as the register shows them
     */
    public List<MemberWithName> withCompleteness(List<StationMember> members) {
        return members.stream().map(this::withCompleteness).toList();
    }

    /**
     * Everybody on the register, with the answers this reader may read and no others.
     *
     * <p>The row carries every answer a member has given, because one query is what makes the
     * screen quick. Which of them may leave the server is decided by the same field scopes every
     * other screen passes through: a reader holding nothing but the right to read the register
     * used to be sent the lot, and drawing only some of it on screen is not the same as not sending
     * it.
     *
     * @param stationId     the station
     * @param includeFormer whether former members are listed too
     * @param readerRights  what the reader may do at the station
     * @return the rows
     */
    public List<RichMember> richMembers(int stationId, boolean includeFormer, Set<StationPermission> readerRights) {
        Set<Integer> readable =
                profileFieldService.findReadableBy(stationId, ProfileFieldScopes.readableBy(readerRights)).stream()
                        .map(ProfileField::id)
                        .collect(Collectors.toSet());
        return memberRepository.findRichMembers(stationId, includeFormer).stream()
                .map(m -> m.withReadableValues(readable))
                .map(m -> m.withIdentity(identityFactory.local(m.stationId(), m.id())))
                .toList();
    }
}
