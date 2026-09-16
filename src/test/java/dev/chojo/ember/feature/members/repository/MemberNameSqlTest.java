/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.sql.MemberNameSql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule lives in two places, and this is what holds them to each other.
 *
 * <p>A name is written in Java for one member and in SQL for a roster of five hundred, because
 * resolving five hundred one at a time is five hundred round trips. Two implementations of one rule
 * is how a picker and a list end up spelling the same person differently, so every form is asked
 * both ways here and the two answers have to agree.
 */
class MemberNameSqlTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;
    private static int memberId;
    private static int formerId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Name Spelling Station");
        account = accountRepo.create("spelling@test.com", "Maximilian", "Hoffmann");
        memberId = stationMemberRepo.create(station.id(), account.id()).id();

        var leaver = accountRepo.create("leaver@test.com", "Frieda", "Wagner");
        formerId = stationMemberRepo.create(station.id(), leaver.id()).id();
        stationMemberRepo.setDisplayNameAndClearAccount(formerId, "Frieda Wagner");
        accountRepo.delete(leaver.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /** What the database answers for one member, using the fragment a roster splices. */
    private static String spelledInSql(String expression, int id) {
        return query("""
                        SELECT %s AS name
                        FROM station_member sm
                        LEFT JOIN account a ON a.id = sm.account_id
                        WHERE sm.id = :id;""".formatted(expression))
                .single(call().bind("id", id))
                .map(row -> row.getString("name"))
                .first()
                .orElse(null);
    }

    /** A member the station can still read an account for is spelled the same way by both. */
    @Test
    void theDatabaseAndTheServiceAgreeOnAMember() {
        var parts = NameParts.of(account.firstName(), account.lastName());

        assertEquals(parts.called(), spelledInSql(MemberNameSql.ofMember("sm", "a"), memberId));
        assertEquals(parts.called(), spelledInSql(MemberNameSql.ofMemberOrBlank("sm", "a"), memberId));
        assertEquals(parts.called(), spelledInSql(MemberNameSql.ofMemberOrNull("sm", "a"), memberId));
        assertEquals(parts.official(), spelledInSql(MemberNameSql.ofAccount("a"), memberId));
    }

    /**
     * Somebody who has left is spelled from the name frozen onto their row.
     *
     * <p>Their account is gone, so the fragment that reads one has nothing to read, and both sides
     * have to fall through to the same place.
     */
    @Test
    void theDatabaseAndTheServiceAgreeOnSomebodyWhoHasLeft() {
        var parts = NameParts.frozen("Frieda Wagner");

        assertEquals(parts.called(), spelledInSql(MemberNameSql.ofMember("sm", "a"), formerId));
        assertEquals(parts.identified(), spelledInSql(MemberNameSql.ofMemberOrBlank("sm", "a"), formerId));
        assertEquals(parts.official(), spelledInSql(MemberNameSql.ofMemberOrNull("sm", "a"), formerId));
    }

    /**
     * The three fallbacks differ only in what they say about nobody at all, which is the whole
     * reason there are three of them.
     */
    @Test
    void theThreeFallbacksDifferOnlyWhereNothingIsKnown() {
        var forgotten = accountRepo.create("forgotten@test.com", "Wird", "Vergessen");
        int nameless = stationMemberRepo.create(station.id(), forgotten.id()).id();
        stationMemberRepo.setDisplayNameAndClearAccount(nameless, "");
        accountRepo.delete(forgotten.id());

        assertEquals(
                "Mitglied " + nameless,
                spelledInSql(MemberNameSql.ofMember("sm", "a"), nameless),
                "a list names them by their number rather than showing a blank row");
        assertEquals(
                "",
                spelledInSql(MemberNameSql.ofMemberOrBlank("sm", "a"), nameless),
                "a search matches them against nothing");
        assertNull(
                spelledInSql(MemberNameSql.ofMemberOrNull("sm", "a"), nameless), "a history leaves the column empty");
    }

    /**
     * A list sorts by surname first.
     *
     * <p>Asserted against the database rather than against the string, because an order that names
     * a column the statement does not have fails only when it runs.
     */
    @Test
    void aListSortsBySurnameThenFirstName() {
        List<String> names = query(
                        """
                        SELECT %s AS name
                        FROM station_member sm
                        LEFT JOIN account a ON a.id = sm.account_id
                        WHERE sm.station_id = :station_id
                        ORDER BY %s;""".formatted(MemberNameSql.ofMemberOrBlank("sm", "a"), MemberNameSql.order("sm", "a")))
                .single(call().bind("station_id", station.id()))
                .map(row -> row.getString("name"))
                .all();

        assertTrue(names.contains("Maximilian Hoffmann"), "the station's members are listed: " + names);
    }
}
