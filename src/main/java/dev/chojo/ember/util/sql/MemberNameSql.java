/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

/**
 * The one way a statement writes a member's name, so that a roster, a picker and a search all spell
 * the same person the same way.
 * <p>
 * A name is assembled in SQL wherever resolving it in Java would cost a query per row: a roster of
 * five hundred people named one at a time is five hundred round trips. That puts the rule in two
 * places at once, here and in the service that answers for a single member, which is why both are
 * written from the same parts and held to each other by test.
 * <p>
 * Each fragment takes the aliases its statement uses, because the queries that need a name do not
 * agree on what to call the tables: a member list joins {@code sm} to {@code a}, a history of who
 * changed a profile field joins the same account twice under two names, and a statistic reads the
 * account table with no alias at all.
 */
public final class MemberNameSql {

    /**
     * The register name held on an account.
     * <p>
     * {@code full_name} is generated from the two halves, so it is what to read where it is
     * filled, and the halves are the fallback for a row where it is not.
     *
     * @param account the account table's alias, or an empty string where it has none
     */
    public static String ofAccount(String account) {
        String a = prefix(account);
        return "coalesce(nullif(%sfull_name, ''), nullif(trim(BOTH ' ' FROM %sfirst_name || ' ' || %slast_name), ''))"
                .formatted(a, a, a);
    }

    /**
     * The name a station reads for one of its members.
     * <p>
     * A member who has left carries their name frozen on the member row and has no account left to
     * read from, so the frozen name stands in. A member with neither is named by their number
     * rather than by nothing, which keeps a list from showing a blank row.
     *
     * <p>The frozen name is read through {@code nullif} because the column is {@code NOT NULL
     * DEFAULT ''}: every member who has not left carries an empty string there, and a plain
     * {@code coalesce} would take that empty string for an answer and never reach the fallback.
     *
     * @param member the member table's alias
     * @param account the account table's alias
     */
    public static String ofMember(String member, String account) {
        return "coalesce(%s, %s, 'Mitglied ' || %sid)"
                .formatted(calledName(member, account), frozen(member), prefix(member));
    }

    /**
     * The register name with the name the station calls them by in place of the first half.
     *
     * <p>The station's switch is not consulted here. A statement that reads a roster has one station
     * in hand already and would pay for a join to ask again per row, so the caller that turns the
     * setting off asks for {@link #ofAccount(String)} instead. Every such caller goes through
     * {@code MemberNameResolver}, which does consult it.
     */
    private static String calledName(String member, String account) {
        String m = prefix(member);
        String a = prefix(account);
        return """
                coalesce(
                    nullif(trim(BOTH ' ' FROM coalesce(nullif(%snickname, ''), %sfirst_name) || ' ' || %slast_name), ''),
                    %s)""".formatted(m, a, a, ofAccount(account));
    }

    /**
     * The same name where a blank reads better than a number: what a search matches against, and
     * what a payload carries when the reader can tell an empty name from a made-up one.
     */
    public static String ofMemberOrBlank(String member, String account) {
        return "coalesce(%s, %s, '')".formatted(calledName(member, account), frozen(member));
    }

    /** The same name where the column is allowed to be empty, such as a history of who did what. */
    public static String ofMemberOrNull(String member, String account) {
        return "coalesce(%s, %s)".formatted(calledName(member, account), frozen(member));
    }

    private static String frozen(String member) {
        return "nullif(%sdisplay_name, '')".formatted(prefix(member));
    }

    /**
     * What a list of members sorts by.
     * <p>
     * The surname first, because that is how a roster is looked through, and the first name after
     * it, because that is the half a reader scans within one surname. The frozen name of somebody
     * who has left has no halves and sorts last, under whatever it holds.
     */
    public static String order(String member, String account) {
        return "%slast_name, coalesce(nullif(%snickname, ''), %sfirst_name), %sdisplay_name"
                .formatted(prefix(account), prefix(member), prefix(account), prefix(member));
    }

    private static String prefix(String alias) {
        return alias == null || alias.isBlank() ? "" : alias + ".";
    }

    private MemberNameSql() {}
}
