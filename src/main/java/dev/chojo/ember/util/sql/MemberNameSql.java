/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

/**
 * A member's name as SQL, for statements that would otherwise resolve names one query per row. The Java
 * resolver for a single member follows the same rule and a test holds the two together. Every fragment takes
 * the table aliases its statement uses; an empty alias means none.
 */
public final class MemberNameSql {

    /** The register name on an account: the generated {@code full_name}, else the two halves. */
    public static String ofAccount(String account) {
        String a = prefix(account);
        return "coalesce(nullif(%sfull_name, ''), nullif(trim(BOTH ' ' FROM %sfirst_name || ' ' || %slast_name), ''))"
                .formatted(a, a, a);
    }

    /**
     * The name a station reads for a member: the called name, else the name frozen when they left, else
     * {@code Mitglied <id>} so a list never shows a blank row.
     */
    public static String ofMember(String member, String account) {
        return "coalesce(%s, %s, 'Mitglied ' || %sid)"
                .formatted(calledName(member, account), frozen(member), prefix(member));
    }

    /**
     * The register name with the nickname in place of the first half. The station's setting is not read here,
     * which would cost a join per row; a caller with it off asks for {@link #ofAccount(String)} instead.
     */
    private static String calledName(String member, String account) {
        String m = prefix(member);
        String a = prefix(account);
        return """
                coalesce(
                    nullif(trim(BOTH ' ' FROM coalesce(nullif(%snickname, ''), %sfirst_name) || ' ' || %slast_name), ''),
                    %s)""".formatted(m, a, a, ofAccount(account));
    }

    /** {@link #ofMember} with a blank instead of the number, for search and payloads. */
    public static String ofMemberOrBlank(String member, String account) {
        return "coalesce(%s, %s, '')".formatted(calledName(member, account), frozen(member));
    }

    /** {@link #ofMember} with {@code NULL} instead of the number. */
    public static String ofMemberOrNull(String member, String account) {
        return "coalesce(%s, %s)".formatted(calledName(member, account), frozen(member));
    }

    /**
     * Register name and nickname at once, {@code Max "Maxe" Mustermann}, or the register name alone when the
     * nickname is empty or the first name; a member who has left keeps the frozen name.
     */
    public static String identifiedOfMember(String member, String account) {
        String m = prefix(member);
        String a = prefix(account);
        return """
                coalesce(
                    CASE
                        WHEN nullif(%snickname, '') IS NULL
                            OR lower(%snickname) = lower(coalesce(%sfirst_name, ''))
                        THEN %s
                        ELSE nullif(trim(BOTH ' ' FROM
                            coalesce(%sfirst_name || ' ', '') || '"' || %snickname || '"'
                            || coalesce(' ' || %slast_name, '')), '')
                    END,
                    %s,
                    'Mitglied ' || %sid)""".formatted(m, m, a, ofAccount(account), a, m, a, frozen(member), m);
    }

    /** The frozen name, through {@code nullif} because the column holds an empty string, not NULL, until then. */
    private static String frozen(String member) {
        return "nullif(%sdisplay_name, '')".formatted(prefix(member));
    }

    /** The roster order: surname, then called first name; members who have left sort last by frozen name. */
    public static String order(String member, String account) {
        return "%slast_name, coalesce(nullif(%snickname, ''), %sfirst_name), %sdisplay_name"
                .formatted(prefix(account), prefix(member), prefix(account), prefix(member));
    }

    private static String prefix(String alias) {
        return alias == null || alias.isBlank() ? "" : alias + ".";
    }

    private MemberNameSql() {}
}
