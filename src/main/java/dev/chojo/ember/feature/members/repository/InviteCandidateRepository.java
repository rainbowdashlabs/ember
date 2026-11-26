/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.members.entity.InviteCandidate;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * The accounts a member manager may find by name to invite into their station.
 *
 * <p>A manager finds an account that is an active member of a station the manager is an active member
 * of, an account that is an active member of a station in an association one of those stations is in,
 * and an account that chose to be findable from every station. Nobody else: the search would otherwise
 * tell a manager who has an account on this instance at all. A guardian and the person they look after
 * are members of one station, so they find each other through it; the people around them do not.
 *
 * <p>Each word of the search has to appear in the full name, without regard to case or accents: both
 * sides are folded the same way ({@link #FOLDED_FROM}), so "Muller" finds "Müller" and "Strasse" finds
 * "Straße".
 *
 * <p>A station that moved to another installation is a read-only copy here and ties nobody to anybody.
 */
@Singleton
public class InviteCandidateRepository {
    /** The letters folded away, each to the letter at its place in {@link #FOLDED_TO}. */
    static final String FOLDED_FROM = "äöüéèêëáàâãåóòôõíìîïúùûñçýÄÖÜÉÈÊËÁÀÂÃÅÓÒÔÕÍÌÎÏÚÙÛÑÇÝ";

    /** What each letter of {@link #FOLDED_FROM} is folded to. */
    static final String FOLDED_TO = "aoueeeeaaaaaooooiiiiuuuncyaoueeeeaaaaaooooiiiiuuuncy";

    private static final String FOLDED_NAME =
            "replace(lower(translate(a.full_name, '%s', '%s')), 'ß', 'ss')".formatted(FOLDED_FROM, FOLDED_TO);

    private static final String TEMPLATE = """
            WITH mine AS (SELECT sm.station_id
                          FROM station_member sm
                                   JOIN station s ON s.id = sm.station_id
                          WHERE sm.account_id = :caller
                            AND NOT sm.former
                            AND s.moved_away_at IS NULL),
                 my_associations AS (SELECT DISTINCT s.cluster_id
                                     FROM station s
                                              JOIN mine m ON m.station_id = s.id
                                     WHERE s.cluster_id IS NOT NULL),
                 reach AS (SELECT sm.account_id, 'SHARED_STATION' AS because, st.name AS through, 1 AS closeness
                           FROM station_member sm
                                    JOIN mine m ON m.station_id = sm.station_id
                                    JOIN station st ON st.id = sm.station_id
                           WHERE NOT sm.former
                             AND sm.account_id IS NOT NULL
                           UNION ALL
                           SELECT sm.account_id, 'SHARED_ASSOCIATION', c.name, 2
                           FROM station_member sm
                                    JOIN station st ON st.id = sm.station_id
                                    JOIN my_associations ma ON ma.cluster_id = st.cluster_id
                                    JOIN cluster c ON c.id = st.cluster_id
                           WHERE NOT sm.former
                             AND sm.account_id IS NOT NULL
                             AND st.moved_away_at IS NULL
                           UNION ALL
                           SELECT account_id, 'OPTED_IN', NULL, 3
                           FROM account_preference
                           WHERE findable_by_name),
                 closest AS (SELECT DISTINCT ON (r.account_id) r.account_id, r.because, r.through
                             FROM reach r
                                      JOIN account a ON a.id = r.account_id
                             WHERE a.id <> :caller
                               AND %s
                             ORDER BY r.account_id, r.closeness, r.through)
            SELECT a.id                                                             AS account_id,
                   a.uid                                                            AS account_uid,
                   a.first_name,
                   a.last_name,
                   c.because,
                   c.through,
                   EXISTS (SELECT 1
                           FROM station_member here
                           WHERE here.station_id = :station
                             AND here.account_id = a.id)                            AS already_here,
                   EXISTS (SELECT 1
                           FROM account_link_request asked
                           WHERE asked.station_id = :station
                             AND asked.account_id = a.id
                             AND asked.answered_at IS NULL
                             AND asked.expires_at > now())                          AS already_asked,
                   EXISTS (SELECT 1
                           FROM account_link_request declined
                           WHERE declined.station_id = :station
                             AND declined.account_id = a.id
                             AND declined.answer = 'DECLINED'
                             AND declined.answered_at > now() - INTERVAL '30 days') AS declined_lately
            FROM closest c
                     JOIN account a ON a.id = c.account_id
            ORDER BY lower(a.last_name), lower(a.first_name), a.id
            LIMIT :limit;""";

    private static final String BY_NAME = TEMPLATE.formatted(FOLDED_NAME + " LIKE ALL (:patterns)");
    private static final String BY_UID = TEMPLATE.formatted("a.uid = :uid::UUID");

    /**
     * The accounts the manager may find whose name holds every word of the search.
     *
     * @param callerAccountId the manager's account
     * @param stationId       the station the manager invites into
     * @param words           the words of the search, each of them non-blank
     * @param limit           the most accounts to return
     * @return the accounts, by last and first name
     */
    public List<InviteCandidate> find(int callerAccountId, int stationId, List<String> words, int limit) {
        if (words.isEmpty()) return List.of();
        var patterns = words.stream().map(InviteCandidateRepository::containing).toList();
        return query(BY_NAME)
                .single(call().bind("caller", callerAccountId)
                        .bind("station", stationId)
                        .bind("patterns", patterns, PostgreSqlTypes.VARCHAR)
                        .bind("limit", limit))
                .map(InviteCandidate.map())
                .all();
    }

    /**
     * The account behind this uid, when the manager may find it, whatever its name.
     *
     * @param callerAccountId the manager's account
     * @param stationId       the station the manager invites into
     * @param accountUid      the account
     * @return the account as a candidate, or empty where the manager may not find it
     */
    public Optional<InviteCandidate> findReachable(int callerAccountId, int stationId, UUID accountUid) {
        return query(BY_UID)
                .single(call().bind("caller", callerAccountId)
                        .bind("station", stationId)
                        .bind("uid", accountUid, UUID_STRING)
                        .bind("limit", 1))
                .map(InviteCandidate.map())
                .first();
    }

    /**
     * A word folded the way the query folds the names it is matched against.
     *
     * @param word a word of the search
     * @return the word in lower case, accents and ß folded away
     */
    static String folded(String word) {
        var folded = new StringBuilder();
        for (char letter : word.toCharArray()) {
            int at = FOLDED_FROM.indexOf(letter);
            if (letter == 'ß') folded.append("ss");
            else folded.append(at < 0 ? Character.toLowerCase(letter) : FOLDED_TO.charAt(at));
        }
        return folded.toString();
    }

    private static String containing(String word) {
        String escaped = folded(word).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
