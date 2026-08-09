/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

import de.chojo.sadu.queries.api.call.Call;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Assembles the optional part of a {@code WHERE} clause. Every predicate is registered
 * together with the parameters it references, so a fragment can never be added without its
 * binds — the failure mode of the hand-rolled variant, where a ternary-produced fragment and
 * a separate {@code if (value != null) call = call.bind(...)} drift apart and the statement
 * fails at runtime with a missing parameter.
 *
 * <p>Fragments must be trusted compile-time constants; they are placed into a {@code %s} slot
 * of the statement. Values never reach the statement text — they always travel as named binds
 * applied through {@link #apply(Call)}. Predicates whose value is {@code null} are dropped
 * entirely, so an absent filter widens the result instead of matching {@code IS NULL}.
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

    /**
     * Starts an empty clause.
     */
    public static WhereBuilder create() {
        return new WhereBuilder();
    }

    /**
     * Appends a predicate that carries no parameters, for example a fragment picked by a
     * {@code switch} over a filter mode. Blank fragments are ignored, so the "no filter"
     * branch needs no special casing.
     */
    public WhereBuilder add(String fragment) {
        if (fragment.isBlank()) return this;
        return append(fragment, UnaryOperator.identity());
    }

    /**
     * Appends a parameterless predicate only when {@code condition} holds.
     */
    public WhereBuilder addIf(boolean condition, String fragment) {
        return condition ? add(fragment) : this;
    }

    /**
     * Appends a predicate bound to a single integer parameter. A {@code null} value drops the
     * predicate and its bind.
     */
    public WhereBuilder add(String fragment, String parameter, Integer value) {
        return value == null ? this : append(fragment, call -> call.bind(parameter, value));
    }

    /**
     * Appends a predicate bound to a single boolean parameter. A {@code null} value drops the
     * predicate and its bind.
     */
    public WhereBuilder add(String fragment, String parameter, Boolean value) {
        return value == null ? this : append(fragment, call -> call.bind(parameter, value));
    }

    /**
     * Appends a predicate bound to a single string parameter. A {@code null} value drops the
     * predicate and its bind.
     */
    public WhereBuilder add(String fragment, String parameter, String value) {
        return value == null ? this : append(fragment, call -> call.bind(parameter, value));
    }

    /**
     * Appends a predicate bound to a single enum parameter. A {@code null} value drops the
     * predicate and its bind.
     */
    public WhereBuilder add(String fragment, String parameter, Enum<?> value) {
        return value == null ? this : append(fragment, call -> call.bind(parameter, value));
    }

    /**
     * Appends a case-insensitive substring search. The parameter is bound to the trimmed,
     * lower-cased term wrapped in {@code %} wildcards, so the fragment only has to spell out
     * the {@code LOWER(column) LIKE :parameter} comparison. A {@code null} or blank term drops
     * the predicate, which is what makes an empty search box return the unfiltered list.
     */
    public WhereBuilder like(String fragment, String parameter, String search) {
        if (search == null || search.isBlank()) return this;
        return add(fragment, parameter, "%" + search.trim().toLowerCase() + "%");
    }

    /**
     * The assembled fragment for the statement's {@code %s} slot, one predicate per line and
     * empty when no predicate applies.
     */
    public String fragment() {
        return predicates.stream().map(Predicate::fragment).collect(Collectors.joining("\n"));
    }

    /**
     * Applies the binds of every retained predicate to the call and returns it, so the call
     * can be handed straight to {@code single(...)}.
     */
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
