/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

import de.chojo.sadu.queries.api.call.Call;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The optional part of a {@code WHERE} clause, each predicate registered with its binds so a fragment can never
 * reach the statement without them. Fragments are trusted constants for a {@code %s} slot; values only travel as
 * binds. A predicate whose value is {@code null} is dropped, so an absent filter widens the result.
 *
 * <pre>{@code
 * var where = WhereBuilder.create()
 *         .add("AND e.category_id = :category_id", "category_id", categoryId)
 *         .addIf(!includeDeleted, "AND e.deleted_at IS NULL");
 * return query("SELECT %s FROM station_event WHERE station_id = :station_id %s;", EVENT_COLUMNS, where.fragment())
 *         .single(where.apply(call().bind("station_id", stationId)))
 *         .map(StationEvent.map())
 *         .all();
 * }</pre>
 */
public final class WhereBuilder {
    private final List<Predicate> predicates = new ArrayList<>();

    private WhereBuilder() {}

    /** Starts an empty clause. */
    public static WhereBuilder create() {
        return new WhereBuilder();
    }

    /** Appends a predicate without binds; a blank fragment is ignored. */
    public WhereBuilder add(String fragment) {
        if (fragment.isBlank()) return this;
        return append(fragment, UnaryOperator.identity());
    }

    /** Appends a predicate without binds when the condition holds. */
    public WhereBuilder addIf(boolean condition, String fragment) {
        return condition ? add(fragment) : this;
    }

    /** Appends a predicate with one bind, unless the value is {@code null}. */
    public WhereBuilder add(String fragment, String parameter, @Nullable Integer value) {
        return value == null ? this : append(fragment, call -> call.bind(parameter, value));
    }

    /** Appends a predicate with one bind, unless the value is {@code null}. */
    public WhereBuilder add(String fragment, String parameter, @Nullable Boolean value) {
        return value == null ? this : append(fragment, call -> call.bind(parameter, value));
    }

    /** Appends a predicate with one bind, unless the value is {@code null}. */
    public WhereBuilder add(String fragment, String parameter, @Nullable String value) {
        return value == null ? this : append(fragment, call -> call.bind(parameter, value));
    }

    /** Appends a predicate with one bind, unless the value is {@code null}. */
    public WhereBuilder add(String fragment, String parameter, @Nullable Enum<?> value) {
        return value == null ? this : append(fragment, call -> call.bind(parameter, value));
    }

    /**
     * Appends a case-insensitive substring search, binding the trimmed lower-cased term inside {@code %}, so the
     * fragment reads {@code LOWER(column) LIKE :parameter}. A blank search is dropped.
     */
    public WhereBuilder like(String fragment, String parameter, @Nullable String search) {
        if (search == null || search.isBlank()) return this;
        return add(fragment, parameter, "%" + search.trim().toLowerCase() + "%");
    }

    /** The clause for the statement's {@code %s} slot, one predicate per line, empty without predicates. */
    public String fragment() {
        return predicates.stream().map(Predicate::fragment).collect(Collectors.joining("\n"));
    }

    /** Applies every retained predicate's binds to the call and returns it. */
    public Call apply(Call call) {
        Call bound = call;
        for (Predicate predicate : predicates) {
            bound = predicate.bind().apply(bound);
        }
        return bound;
    }

    private WhereBuilder append(String fragment, UnaryOperator<Call> bind) {
        predicates.add(new Predicate(fragment.strip(), bind));
        return this;
    }

    private record Predicate(String fragment, UnaryOperator<Call> bind) {}
}
