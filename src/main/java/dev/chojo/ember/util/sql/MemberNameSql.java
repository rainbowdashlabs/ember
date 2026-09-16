/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

/**
 * The one way a statement writes a member's name, so that a roster, a picker and a search all
 * spell the same person the same way.
 * <p>
 * A name is assembled in SQL wherever resolving it in Java would cost a query per row: a roster of
 * five hundred people named one at a time is five hundred round trips. That puts the rule in two
 * places at once, here and in the service that answers for a single member, which is why both are
 * written from the same parts and held to each other by test.
 * <p>
 * Every fragment expects the member table aliased {@code sm} and the account table aliased
 * {@code a}, left joined, which is how the statements that splice them are already written.
 */
public final class MemberNameSql {

    /**
     * The register name as the database holds it.
     * <p>
     * A member who has left carries their name frozen on the member row and has no account left to
     * read from, so the frozen name stands in. A member with neither is named by their number
     * rather than by nothing, which keeps a list from showing a blank row.
     */
    private static final String REGISTER = "coalesce(a.full_name, sm.display_name%s)";

    /**
     * The name a station reads on its own screens, with a member nothing is known about named by
     * their number so that a list never shows a blank row.
     */
    public static final String CALLED = REGISTER.formatted(", 'Mitglied ' || sm.id");

    /**
     * The same name where a blank reads better than a number: what a search matches against, and
     * what a payload carries when the reader can tell an empty name from a made-up one.
     */
    public static final String CALLED_OR_BLANK = REGISTER.formatted(", ''");

    /** The same name where the column is allowed to be empty, such as a history of who did what. */
    public static final String CALLED_OR_NULL = REGISTER.formatted("");

    /** The name a document carries. */
    public static final String OFFICIAL = CALLED;

    /**
     * What a list sorts by: the surname first, because that is how a roster is looked through, and
     * the first name after it, because that is the half a reader scans within one surname.
     */
    public static final String ORDER = "a.last_name, a.first_name";

    private MemberNameSql() {}
}
