/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.cluster.repository.ClusterProfileFieldRepository;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Whether a member has answered everything their profile asks of them, as SQL.
 *
 * <p>This is the one place the rule is written. The member's own profile, the reminder about missing
 * answers and the member list all ask it, a list once for every row in the same statement, so a judgement
 * cannot differ between the screen that marks a profile incomplete and the one that asks to complete it.
 *
 * <p>A question is put to a member by their kind of member or by a group they are in, from their station,
 * or by their kind of member from the association the station answers to. A station's question that is put
 * to both their kind and a group of theirs is judged as their kind meets it, and one put to several of their
 * groups as the first of those on their form, which is the way the profile lays them out.
 *
 * <p>Such a question counts against them when it takes an answer at all, they have to answer it, they may
 * write it, and the answer is blank. A question the station only lets them read is not theirs to fill in,
 * and neither is one the association asks and keeps to itself. Answers are documents, so a blank one is no
 * row, the document null a selection left on its blank entry writes, or the empty string.
 */
public final class ProfileCompletenessSql {

    private static final String INCOMPLETE = """
            (EXISTS (SELECT 1
                     FROM profile_field f
                              CROSS JOIN LATERAL (SELECT pfa.required_override, pfa.readonly_override
                                                  FROM profile_field_assignment pfa
                                                  WHERE pfa.field_id = f.id
                                                    AND (pfa.role = {m}.user_type
                                                         OR pfa.group_id IN (SELECT mge.group_id
                                                                             FROM member_group_entry mge
                                                                             WHERE mge.member_id = {m}.id))
                                                  ORDER BY pfa.target_kind = 'ROLE' DESC, pfa.position
                                                  LIMIT 1) a
                              LEFT JOIN profile_field_value v ON v.member_id = {m}.id AND v.field_id = f.id
                     WHERE f.station_id = {m}.station_id
                       AND f.field_type NOT IN ({answerless})
                       AND coalesce(a.required_override, f.required)
                       AND NOT coalesce(a.readonly_override, f.readonly)
                       AND {blank})
             OR EXISTS (SELECT 1
                        FROM cluster_profile_field cpf
                                 JOIN cluster_profile_field_assignment a ON a.field_id = cpf.id
                                 JOIN station s ON s.cluster_id = cpf.cluster_id
                                 LEFT JOIN cluster_profile_field_value v ON v.member_id = {m}.id AND v.field_id = cpf.id
                        WHERE s.id = {m}.station_id
                          AND a.role = {m}.user_type
                          AND {reaches}
                          AND cpf.field_type NOT IN ({answerless})
                          AND coalesce(a.required_override, cpf.required)
                          AND NOT coalesce(a.readonly_override, cpf.readonly)
                          AND NOT cpf.station_readonly
                          AND {blank}))""".replace("{answerless}", answerlessTypes())
            .replace("{reaches}", ClusterProfileFieldRepository.REACHES_STATION)
            .replace("{blank}", "(v.value IS NULL OR v.value::TEXT IN ('null', '\"\"'))");

    private ProfileCompletenessSql() {}

    /**
     * Whether the member under this alias has left a question blank they are required to answer.
     *
     * @param member the alias of the {@code station_member} row in the enclosing statement
     * @return a boolean SQL expression
     */
    public static String incomplete(String member) {
        return INCOMPLETE.replace("{m}", member);
    }

    /** The types nobody answers: headings and gaps, and an age, which counts itself from a date. */
    private static String answerlessTypes() {
        return Arrays.stream(ProfileFieldType.values())
                .filter(type -> !type.holdsValue() || type.isCalculated())
                .map(type -> "'" + type.fieldType().name() + "'")
                .collect(Collectors.joining(", "));
    }
}
