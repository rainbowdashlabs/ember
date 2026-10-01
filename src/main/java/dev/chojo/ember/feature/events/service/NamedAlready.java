/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.question.MemberEligibility;
import dev.chojo.ember.feature.question.QuestionValues;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who passes a member question's narrowing on a write that carries earlier answers back with it.
 *
 * <p>Editors write every question of an appointment back on each save. A member named before stays
 * named although they have since left the group, the user type or the tag the question is narrowed
 * to: the narrowing is measured where somebody is named, not again on every save after. Everybody
 * newly named is asked as usual.
 *
 * @param named the members the stored answers already name
 * @param rest  who passes for everybody else
 */
record NamedAlready(Set<Integer> named, MemberEligibility rest) implements MemberEligibility {

    /**
     * The eligibility of a write over these stored answers.
     *
     * @param stored      the answers as they stand, in any shape they were stored in
     * @param eligibility who passes for everybody they do not name
     */
    static NamedAlready in(Collection<@Nullable String> stored, MemberEligibility eligibility) {
        var named = stored.stream()
                .flatMap(value -> QuestionValues.memberIds(value).stream())
                .collect(Collectors.toUnmodifiableSet());
        return new NamedAlready(named, eligibility);
    }

    @Override
    public boolean inGroup(int memberId, int groupId) {
        return named.contains(memberId) || rest.inGroup(memberId, groupId);
    }

    @Override
    public boolean ofType(int memberId, StationUserType userType) {
        return named.contains(memberId) || rest.ofType(memberId, userType);
    }

    @Override
    public boolean hasTag(int memberId, int tagId) {
        return named.contains(memberId) || rest.hasTag(memberId, tagId);
    }
}
