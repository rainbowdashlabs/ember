/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Enriched member representation for API responses.
 * Always includes the resolved identity with display metadata.
 */
public record MemberWithName(
        int id,
        int stationId,
        int accountId,
        String name,
        String email,
        StationUserType userType,
        boolean profileComplete,
        Instant formerAt,
        LocalDate joinDate,
        MemberIdentity identity) {

    /**
     * Creates a MemberWithName from a StationMember entity, resolving name, email, and identity.
     */
    public static MemberWithName from(
            StationMember m, AccountRepository accountRepository, MemberIdentityFactory identityFactory) {
        return from(m, accountRepository, identityFactory, true);
    }

    public static MemberWithName from(
            StationMember m,
            AccountRepository accountRepository,
            MemberIdentityFactory identityFactory,
            boolean profileComplete) {
        var identity = identityFactory.local(m.stationId(), m.id());
        if (m.accountId() == null) {
            return new MemberWithName(
                    m.id(),
                    m.stationId(),
                    0,
                    m.displayName(),
                    "",
                    m.userType(),
                    profileComplete,
                    m.formerAt(),
                    m.joinDate(),
                    identity);
        }
        var account = accountRepository.findById(m.accountId()).orElse(null);
        String name = account != null ? (account.firstName() + " " + account.lastName()).trim() : "";
        String email = account != null ? account.email() : "";
        return new MemberWithName(
                m.id(),
                m.stationId(),
                m.accountId(),
                name,
                email,
                m.userType(),
                profileComplete,
                m.formerAt(),
                m.joinDate(),
                identity);
    }
}
