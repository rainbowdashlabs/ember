/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import dev.chojo.ember.api.auth.StationUserType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the {@link RestrictionSet#matches} logic directly.
 * No database needed - pure unit tests for AND/OR restriction evaluation.
 */
class EventEligibilityTest {

    private static final StationUserType USER_TYPE_MEMBER = StationUserType.MEMBER;
    private static final StationUserType USER_TYPE_TEAM = StationUserType.TEAM;
    private static final int GROUP_A = 10;
    private static final int TAG_X = 20;
    private static final int MEMBER_42 = 42;

    private Restriction userTypeRestriction(StationUserType userType) {
        return new Restriction(0, userType, null, null, null);
    }

    private Restriction groupRestriction(int groupId) {
        return new Restriction(0, null, groupId, null, null);
    }

    private Restriction tagRestriction(int tagId) {
        return new Restriction(0, null, null, tagId, null);
    }

    private Restriction memberRestriction(int memberId) {
        return new Restriction(0, null, null, null, memberId);
    }

    @Test
    void noRestrictionsAllowsAll() {
        var set = new RestrictionSet(List.of(), RestrictionMode.AND);
        assertTrue(set.matches(USER_TYPE_MEMBER, List.of(), List.of(), 1));
    }

    @Test
    void userTypeRestrictionMatchesWhenMemberHasUserType() {
        var set = new RestrictionSet(List.of(userTypeRestriction(USER_TYPE_MEMBER)), RestrictionMode.AND);
        assertTrue(set.matches(USER_TYPE_MEMBER, List.of(), List.of(), 1));
    }

    @Test
    void userTypeRestrictionRejectsWhenMemberLacksUserType() {
        var set = new RestrictionSet(List.of(userTypeRestriction(USER_TYPE_TEAM)), RestrictionMode.AND);
        assertFalse(set.matches(USER_TYPE_MEMBER, List.of(), List.of(), 1));
    }

    @Test
    void groupRestrictionMatchesWhenMemberInGroup() {
        var set = new RestrictionSet(List.of(groupRestriction(GROUP_A)), RestrictionMode.AND);
        assertTrue(set.matches(null, List.of(GROUP_A), List.of(), 1));
    }

    @Test
    void groupRestrictionRejectsWhenMemberNotInGroup() {
        var set = new RestrictionSet(List.of(groupRestriction(GROUP_A)), RestrictionMode.AND);
        assertFalse(set.matches(null, List.of(), List.of(), 1));
    }

    @Test
    void tagRestrictionMatchesWhenMemberHasTag() {
        var set = new RestrictionSet(List.of(tagRestriction(TAG_X)), RestrictionMode.AND);
        assertTrue(set.matches(null, List.of(), List.of(TAG_X), 1));
    }

    @Test
    void tagRestrictionRejectsWhenMemberLacksTag() {
        var set = new RestrictionSet(List.of(tagRestriction(TAG_X)), RestrictionMode.AND);
        assertFalse(set.matches(null, List.of(), List.of(), 1));
    }

    @Test
    void combinedAndRestrictionsRequireAllToMatch() {
        var set = new RestrictionSet(
                List.of(userTypeRestriction(USER_TYPE_MEMBER), groupRestriction(GROUP_A), tagRestriction(TAG_X)),
                RestrictionMode.AND);

        assertTrue(set.matches(USER_TYPE_MEMBER, List.of(GROUP_A), List.of(TAG_X), 1), "all match");
        assertFalse(set.matches(USER_TYPE_MEMBER, List.of(GROUP_A), List.of(), 1), "missing tag");
        assertFalse(set.matches(USER_TYPE_MEMBER, List.of(), List.of(TAG_X), 1), "missing group");
        assertFalse(set.matches(null, List.of(GROUP_A), List.of(TAG_X), 1), "missing user type");
    }

    @Test
    void partialAndRestrictionsIgnoreUnsetTypes() {
        var set = new RestrictionSet(
                List.of(userTypeRestriction(USER_TYPE_MEMBER), tagRestriction(TAG_X)), RestrictionMode.AND);

        assertTrue(
                set.matches(USER_TYPE_MEMBER, List.of(), List.of(TAG_X), 1),
                "user type and tag suffice when no group is restricted");
        assertFalse(set.matches(USER_TYPE_MEMBER, List.of(), List.of(), 1), "missing tag");
    }

    @Test
    void orModeAllowsAnyMatch() {
        var set = new RestrictionSet(
                List.of(userTypeRestriction(USER_TYPE_TEAM), groupRestriction(GROUP_A), tagRestriction(TAG_X)),
                RestrictionMode.OR);

        assertTrue(set.matches(USER_TYPE_TEAM, List.of(), List.of(), 1), "only user type matches");
        assertTrue(set.matches(null, List.of(GROUP_A), List.of(), 1), "only group matches");
        assertTrue(set.matches(null, List.of(), List.of(TAG_X), 1), "only tag matches");
        assertFalse(set.matches(USER_TYPE_MEMBER, List.of(), List.of(), 1), "nothing matches");
    }

    @Test
    void memberRestrictionGrantsAccessRegardlessOfMode() {
        var set = new RestrictionSet(
                List.of(userTypeRestriction(USER_TYPE_TEAM), memberRestriction(MEMBER_42)), RestrictionMode.AND);

        assertTrue(
                set.matches(null, List.of(), List.of(), MEMBER_42),
                "the named member passes even without the required user type");
        assertFalse(
                set.matches(null, List.of(), List.of(), 99), "another member must satisfy the user type restriction");
        assertTrue(set.matches(USER_TYPE_TEAM, List.of(), List.of(), 99));
    }

    @Test
    void memberOnlyRestrictionDeniesOtherMembers() {
        var set = new RestrictionSet(List.of(memberRestriction(MEMBER_42)), RestrictionMode.AND);

        assertTrue(set.matches(null, List.of(), List.of(), MEMBER_42));
        assertFalse(set.matches(null, List.of(), List.of(), 99));
    }

    @Test
    void hasRestrictionsReturnsFalseWhenEmpty() {
        var set = new RestrictionSet(List.of(), RestrictionMode.AND);
        assertFalse(set.hasRestrictions());
    }

    @Test
    void hasRestrictionsReturnsTrueWhenNotEmpty() {
        var set = new RestrictionSet(List.of(userTypeRestriction(USER_TYPE_MEMBER)), RestrictionMode.AND);
        assertTrue(set.hasRestrictions());
    }
}
