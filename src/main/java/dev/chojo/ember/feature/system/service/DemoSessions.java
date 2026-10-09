/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberPermissionResolver;
import dev.chojo.ember.feature.station.entity.Station;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.EnumSet;
import java.util.Set;

/**
 * The sessions the demo acts in when it seeds through the services a person's request reaches, so what it
 * seeds passes the same checks with the rights that person holds at the station.
 *
 * <p>Nobody signed in for it: the session stands for no stored login and carries no second factor, so
 * whatever asks for a fresh step-up refuses it, as it refuses a person who has given none.
 */
@Singleton
public class DemoSessions {
    /** The session number of a session nobody signed in for. */
    private static final int NO_LOGIN = 0;

    private final AccountRepository accounts;
    private final StationMemberRepository members;
    private final MemberPermissionResolver permissions;

    @Inject
    public DemoSessions(
            AccountRepository accounts, StationMemberRepository members, MemberPermissionResolver permissions) {
        this.accounts = accounts;
        this.members = members;
        this.permissions = permissions;
    }

    /**
     * A session of a seeded member at their station, with everything they may do there as it stands now.
     *
     * @param station  the station
     * @param memberId the member, who holds an account
     * @return their session
     * @throws IllegalStateException when the member or their account is not there
     */
    public StationSession of(Station station, int memberId) {
        var member = members.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("The demo seeded no member " + memberId));
        Integer accountId = member.accountId();
        if (accountId == null) throw new IllegalStateException("Demo member " + memberId + " has no account");
        var account = accounts.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("The demo seeded no account " + accountId));
        Set<StationPermission> held = EnumSet.of(StationPermission.LOGIN);
        held.addAll(permissions.resolve(member));
        return StationSession.of(
                new UserSession(account, NO_LOGIN, station.id(), station.uid(), member, held, Set.of(), null));
    }
}
