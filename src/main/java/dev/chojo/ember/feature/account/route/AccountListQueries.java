/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.refusal.AdminRefusal;
import dev.chojo.ember.feature.account.entity.AccountListQuery;
import dev.chojo.ember.feature.account.entity.AccountPasswordState;
import dev.chojo.ember.feature.account.entity.AccountSort;
import io.javalin.http.Context;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Reads what the account list is asked for from its query parameters. A parameter that is absent
 * narrows nothing; one that names something that does not exist is refused, so a mistyped address
 * never quietly shows every account.
 */
final class AccountListQueries {
    private static final Pattern WORD_BREAK = Pattern.compile("([a-z])([A-Z])");

    private AccountListQueries() {}

    /**
     * @param ctx the request
     * @return the query it asks
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when a value cannot be read
     */
    static AccountListQuery of(Context ctx) {
        return new AccountListQuery(
                ctx.queryParam("q"),
                enums(ctx, "role", InstanceUserType.class),
                enums(ctx, "password", AccountPasswordState.class),
                flag(ctx, "twoFactor"),
                number(ctx, "passkeysMin"),
                number(ctx, "passkeysMax"),
                flag(ctx, "confirmed"),
                Boolean.TRUE.equals(flag(ctx, "withoutAddress")),
                day(ctx, "signedInFrom"),
                day(ctx, "signedInTo"),
                Boolean.TRUE.equals(flag(ctx, "neverSignedIn")),
                all(ctx, "station", UUID::fromString),
                Boolean.TRUE.equals(flag(ctx, "withoutStation")),
                all(ctx, "association", UUID::fromString),
                Boolean.TRUE.equals(flag(ctx, "withoutAssociation")),
                Objects.requireNonNullElse(named(AccountSort.class, ctx.queryParam("sort")), AccountSort.NAME),
                descending(ctx.queryParam("direction")));
    }

    private static boolean descending(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return false;
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "asc" -> false;
            case "desc" -> true;
            default -> throw AdminRefusal.ACCOUNT_LIST_UNKNOWN_CHOICE.raise(raw);
        };
    }

    private static <E extends Enum<E>> Set<E> enums(Context ctx, String name, Class<E> type) {
        var values = EnumSet.noneOf(type);
        for (String raw : ctx.queryParams(name)) {
            E value = named(type, raw);
            if (value != null) values.add(value);
        }
        return values;
    }

    private static <T> Set<T> all(Context ctx, String name, Function<String, T> read) {
        var values = new LinkedHashSet<T>();
        for (String raw : ctx.queryParams(name)) {
            if (raw.isBlank()) continue;
            try {
                values.add(read.apply(raw.strip()));
            } catch (IllegalArgumentException e) {
                throw AdminRefusal.ACCOUNT_LIST_UNKNOWN_CHOICE.raise(raw);
            }
        }
        return values;
    }

    private static @Nullable Boolean flag(Context ctx, String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw AdminRefusal.ACCOUNT_LIST_UNKNOWN_CHOICE.raise(raw);
        };
    }

    private static @Nullable Integer number(Context ctx, String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw AdminRefusal.ACCOUNT_LIST_UNKNOWN_CHOICE.raise(raw);
        }
    }

    private static @Nullable LocalDate day(Context ctx, String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.strip());
        } catch (DateTimeParseException e) {
            throw AdminRefusal.ACCOUNT_LIST_UNKNOWN_CHOICE.raise(raw);
        }
    }

    /**
     * An enum constant by its name, written as the constant ({@code LAST_SIGN_IN}) or in the camel case
     * an address reads better in ({@code lastSignIn}).
     */
    private static <E extends Enum<E>> @Nullable E named(Class<E> type, @Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        String constant = WORD_BREAK.matcher(raw.strip()).replaceAll("$1_$2").toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, constant);
        } catch (IllegalArgumentException e) {
            throw AdminRefusal.ACCOUNT_LIST_UNKNOWN_CHOICE.raise(raw);
        }
    }
}
