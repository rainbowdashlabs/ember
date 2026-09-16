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
import dev.chojo.ember.feature.members.service.MemberNameResolver;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Enriched member representation for API responses.
 * Always includes the resolved identity with display metadata.
 *
 * <p>The name travels whole and in halves. A screen that lets somebody correct a name needs the
 * halves as they are stored: guessing them back out of the whole splits at the first space, so
 * "Millie Jo Harnack" reads as a surname of "Jo Harnack", and correcting that saved the right thing
 * and then showed the wrong thing again on the next load.
 */
public record MemberWithName(
        int id,
        int stationId,
        int accountId,
        String name,
        String firstName,
        String lastName,
        String email,
        String username,
        StationUserType userType,
        boolean profileComplete,
        Instant formerAt,
        LocalDate joinDate,
        MemberIdentity identity) {

    /**
     * Creates a MemberWithName from a StationMember entity, resolving name, email, the name it signs
     * in with, and identity.
     *
     * <p>The name is asked for rather than built here. These rows feed the screens a station is
     * administered from, which say who somebody is and what they are called at once, and only the
     * resolver knows the second half: it lives on the membership and the station may have turned it
     * off.
     */
    public static MemberWithName from(
            StationMember m,
            AccountRepository accountRepository,
            MemberIdentityFactory identityFactory,
            MemberNameResolver nameResolver) {
        return from(m, accountRepository, identityFactory, nameResolver, true);
    }

    public static MemberWithName from(
            StationMember m,
            AccountRepository accountRepository,
            MemberIdentityFactory identityFactory,
            MemberNameResolver nameResolver,
            boolean profileComplete) {
        var identity = identityFactory.local(m.stationId(), m.id());
        if (m.accountId() == null) {
            return new MemberWithName(
                    m.id(),
                    m.stationId(),
                    0,
                    m.displayName(),
                    m.displayName(),
                    "",
                    "",
                    null,
                    m.userType(),
                    profileComplete,
                    m.formerAt(),
                    m.joinDate(),
                    identity);
        }
        var account = accountRepository.findById(m.accountId()).orElse(null);
        String resolved = nameResolver.identified(m.id());
        String name = resolved != null ? resolved : "";
        String email = account != null ? account.email() : "";
        String username = account != null ? account.username() : null;
        return new MemberWithName(
                m.id(),
                m.stationId(),
                m.accountId(),
                name,
                account != null ? account.firstName() : "",
                account != null ? account.lastName() : "",
                email,
                username,
                m.userType(),
                profileComplete,
                m.formerAt(),
                m.joinDate(),
                identity);
    }
}
