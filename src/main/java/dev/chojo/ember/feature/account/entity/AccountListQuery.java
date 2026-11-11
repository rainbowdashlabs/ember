/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import dev.chojo.ember.api.auth.InstanceUserType;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * What the instance's account list is narrowed to and how it is ordered. Every part left empty or
 * {@code null} narrows nothing. Within one part the values are alternatives; the parts all have to
 * hold together.
 *
 * @param search             a part of the name, the address or the sign-in name
 * @param roles              the roles on the instance an account may hold
 * @param passwords          the password states an account may be in
 * @param twoFactor          whether a second factor is asked after its password
 * @param passkeysMin        the fewest passkeys it may sign in with, inclusive
 * @param passkeysMax        the most passkeys it may sign in with, inclusive
 * @param confirmed          whether its real address was confirmed; a stand-in address matches neither
 * @param withoutAddress     whether an account with only a stand-in address matches too, or with no
 *                           answer on the confirmation, only it does
 * @param signedInFrom       the first day its last sign-in may fall on
 * @param signedInTo         the last day its last sign-in may fall on
 * @param neverSignedIn      whether an account that never signed in matches too, or with no days
 *                           given, only it does
 * @param stations           the stations, by uid, of which it is a member now in at least one
 * @param withoutStation     whether an account in no station matches too, or alone only it does
 * @param associations       the associations, by uid, in which it holds a role in at least one
 * @param withoutAssociation whether an account in no association matches too, or alone only it does
 * @param sort               what the list is ordered by
 * @param descending         whether that order runs the other way round
 */
public record AccountListQuery(
        @Nullable String search,
        Set<InstanceUserType> roles,
        Set<AccountPasswordState> passwords,
        @Nullable Boolean twoFactor,
        @Nullable Integer passkeysMin,
        @Nullable Integer passkeysMax,
        @Nullable Boolean confirmed,
        boolean withoutAddress,
        @Nullable LocalDate signedInFrom,
        @Nullable LocalDate signedInTo,
        boolean neverSignedIn,
        Set<UUID> stations,
        boolean withoutStation,
        Set<UUID> associations,
        boolean withoutAssociation,
        AccountSort sort,
        boolean descending) {

    /**
     * Every account a search finds, by name, with nothing else narrowed.
     *
     * @param search a part of the name, the address or the sign-in name, or {@code null} for every account
     * @return the query
     */
    public static AccountListQuery search(@Nullable String search) {
        return new AccountListQuery(
                search,
                Set.of(),
                Set.of(),
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                false,
                Set.of(),
                false,
                Set.of(),
                false,
                AccountSort.NAME,
                false);
    }
}
