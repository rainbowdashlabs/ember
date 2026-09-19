/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.util.List;
import java.util.Map;

/**
 * The people a table is about, and whatever the screen that named them knows beyond their identity.
 *
 * <p>This is what keeps the table from having to know which screen it serves. The register names
 * people and nothing else; an appointment names them and brings what they answered when they signed
 * up and where their registration stands. A table drawn for the register simply finds those two
 * empty, and a column asking for an answer nobody gave prints nothing, which is the same thing it
 * does for a member who left the question blank.
 *
 * @param memberIds           who the table is about, in the order they are to be printed
 * @param registrationAnswers what each of them answered, by member and then by question
 * @param registrationStatus  where each of their registrations stands, by member
 */
public record MemberTablePeople(
        List<Integer> memberIds,
        Map<Integer, Map<Integer, String>> registrationAnswers,
        Map<Integer, String> registrationStatus) {

    /** People and nothing more, which is what the register knows about them. */
    public static MemberTablePeople of(List<Integer> memberIds) {
        return new MemberTablePeople(memberIds, Map.of(), Map.of());
    }

    /** What this member answered, or nothing where they answered nothing. */
    public Map<Integer, String> answersOf(int memberId) {
        return registrationAnswers.getOrDefault(memberId, Map.of());
    }
}
