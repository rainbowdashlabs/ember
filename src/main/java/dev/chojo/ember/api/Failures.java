/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import tools.jackson.core.JacksonException;

import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Reads a failure nobody wrote a message for and works out what to tell the reader.
 *
 * <p>A request that breaks used to answer with a bare status and an empty body, which leaves the
 * reader unable to tell whether they sent something wrong or Ember fell over. What comes out of
 * here is a named {@link Refusal}, which carries both a sentence and the honest status: a refusal
 * from the database over what was sent is a {@code 400} or a {@code 409}, a database out of reach
 * is a {@code 503}, and only a genuine fault is a {@code 500}.
 *
 * <p>The technical half never comes out of here. A stack trace, a statement, a constraint name, a
 * table name, a file path and a class name all belong in the log, and {@link #readable(String)} is
 * the gate that keeps them out of anything written by an exception rather than by a person.
 */
public final class Failures {
    private static final int MAX_READABLE_LENGTH = 200;
    private static final int MAX_CAUSE_DEPTH = 12;

    private static final Pattern QUALIFIED_NAME = Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*){2,}");
    private static final Pattern OBJECT_HANDLE = Pattern.compile("@[0-9a-fA-F]{4,}");
    private static final Pattern INTERNAL_WORD = Pattern.compile(
            "(?i)\\b(exception|throwable|stacktrace|sqlstate|nullpointer|classcast|javalin|jackson|postgres|jdbc)\\b");
    private static final Pattern STATEMENT_WORD =
            Pattern.compile("\\b(SELECT|INSERT|UPDATE|DELETE|FROM|WHERE|JOIN|VALUES)\\b");
    private static final Pattern DATABASE_WORD =
            Pattern.compile("(?i)(\\bERROR:|\\bconstraint\\b|\\bduplicate key\\b|\\brelation\\b)");

    private static final Set<String> DUPLICATE_STATES = Set.of("23505");
    private static final Set<String> REFERENCE_STATES = Set.of("23503");
    private static final Set<String> VALUE_STATES =
            Set.of("22001", "22003", "22007", "22008", "22P02", "23502", "23514");
    private static final Set<String> COLLISION_STATES = Set.of("40001", "40P01");
    private static final Set<String> TIMEOUT_STATES = Set.of("57014");

    private Failures() {}

    /**
     * Mints the reference that pairs a fault a reader sees with the log line an operator reads.
     *
     * @return eight hex characters, short enough to be read out over a telephone
     */
    public static String reference() {
        var bytes = new byte[4];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Names a failure that no handler claimed.
     *
     * <p>The cause chain is walked for a database refusal first, because those are the failures
     * that are frequently not Ember's fault at all: a second row with details that have to be
     * unique, a row naming something already deleted, a value longer than the column holds. Each
     * of those gets the status that says so and a sentence saying that nothing was saved.
     * Everything else is a fault, named as one rather than dressed up as something specific.
     *
     * @param err what was thrown
     * @return the refusal to answer with
     */
    public static Refusal describe(Throwable err) {
        String state = sqlStateOf(err);
        if (state == null) return Refusal.UNEXPECTED_FAULT;
        if (DUPLICATE_STATES.contains(state)) return Refusal.ALREADY_EXISTS;
        if (REFERENCE_STATES.contains(state)) return Refusal.STILL_LINKED;
        if (VALUE_STATES.contains(state)) return Refusal.DOES_NOT_FIT;
        if (COLLISION_STATES.contains(state)) return Refusal.CHANGE_COLLIDED;
        if (TIMEOUT_STATES.contains(state)) return Refusal.TOOK_TOO_LONG;
        if (state.startsWith("08") || state.startsWith("53")) return Refusal.STORE_UNREACHABLE;
        return Refusal.UNEXPECTED_FAULT_FROM_UNKNOWN_STATE;
    }

    /**
     * Answers whether a message written by an exception may be shown to a reader.
     *
     * <p>The rule is deliberately suspicious: a message passes only when it holds nothing that
     * looks like machinery. A qualified name, a path, a statement fragment, an object handle, a
     * line break or simply too much text all disqualify it, because each of those is a way an
     * internal detail reaches a screen it does not belong on. A sentence somebody wrote by hand
     * survives all of them.
     *
     * @param message the message an exception carried, possibly {@code null}
     * @return the message when it reads as prose, and empty when it reads as machinery
     */
    public static Optional<String> readable(String message) {
        if (message == null) return Optional.empty();
        String trimmed = message.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_READABLE_LENGTH) return Optional.empty();
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\t') >= 0) {
            return Optional.empty();
        }
        if (trimmed.indexOf('/') >= 0 || trimmed.indexOf('\\') >= 0 || trimmed.indexOf('$') >= 0) {
            return Optional.empty();
        }
        if (QUALIFIED_NAME.matcher(trimmed).find()) return Optional.empty();
        if (OBJECT_HANDLE.matcher(trimmed).find()) return Optional.empty();
        if (INTERNAL_WORD.matcher(trimmed).find()) return Optional.empty();
        if (STATEMENT_WORD.matcher(trimmed).find()) return Optional.empty();
        if (DATABASE_WORD.matcher(trimmed).find()) return Optional.empty();
        return Optional.of(trimmed);
    }

    /**
     * Names the place in a body that could not be read, in the reader's own spelling.
     *
     * <p>Jackson knows the path it stumbled on and offers it ready-made, but its own rendering
     * names the Java type at every step. This builds the same path out of the field names and list
     * positions the caller sent, which are the caller's words and reveal nothing.
     *
     * @param path the property names and list positions Jackson recorded, outermost first
     * @return the path as the sender would write it, or empty when nothing was recorded
     */
    public static Optional<String> fieldPath(Iterable<JacksonException.Reference> path) {
        var written = new StringBuilder();
        for (var step : path) {
            if (step.getPropertyName() != null) {
                if (!written.isEmpty()) written.append('.');
                written.append(step.getPropertyName());
            } else if (step.getIndex() >= 0) {
                written.append('[').append(step.getIndex()).append(']');
            }
        }
        return written.isEmpty() ? Optional.empty() : Optional.of(written.toString());
    }

    private static String sqlStateOf(Throwable err) {
        Throwable current = err;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SQLException refused && refused.getSQLState() != null) {
                return refused.getSQLState();
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return null;
    }
}
