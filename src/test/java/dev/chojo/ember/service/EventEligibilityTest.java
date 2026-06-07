/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the {@link RestrictionSet#matches} logic directly.
 * No database needed — pure unit tests for AND/OR restriction evaluation.
 */
class EventEligibilityTest {

    private static final String USER_TYPE_MEMBER = "MEMBER";
    private static final String USER_TYPE_TEAM = "TEAM";
    private static final int GROUP_A = 10;
    private static final int TAG_X = 20;
    private static final int MEMBER_42 = 42;

    private Restriction userTypeRestriction(String userType) {
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

    // -- No restrictions --

    @Test
    void noRestrictionsAllowsAll() {
        var set = new RestrictionSet(List.of(), RestrictionMode.AND);
        assertTrue(set.matches(USER_TYPE_MEMBER, List.of(), List.of(), 1));
    }

    // -- User type restrictions (AND mode) --

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

    // -- Group restrictions (AND mode) --

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

    // -- Tag restrictions (AND mode) --

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

    // -- Combined AND logic --

    @Test
    void combinedAndRestrictionsRequireAllToMatch() {
        var set = new RestrictionSet(
                List.of(userTypeRestriction(USER_TYPE_MEMBER), groupRestriction(GROUP_A), tagRestriction(TAG_X)),
                RestrictionMode.AND);

        // All match
        assertTrue(set.matches(USER_TYPE_MEMBER, List.of(GROUP_A), List.of(TAG_X), 1));
        // Missing tag
        assertFalse(set.matches(USER_TYPE_MEMBER, List.of(GROUP_A), List.of(), 1));
        // Missing group
        assertFalse(set.matches(USER_TYPE_MEMBER, List.of(), List.of(TAG_X), 1));
        // Missing user type
        assertFalse(set.matches(null, List.of(GROUP_A), List.of(TAG_X), 1));
    }

    @Test
    void partialAndRestrictionsIgnoreUnsetTypes() {
        // Only user type + tag (no group restriction)
        var set = new RestrictionSet(
                List.of(userTypeRestriction(USER_TYPE_MEMBER), tagRestriction(TAG_X)), RestrictionMode.AND);

        // Has user type + tag => eligible (group not restricted)
        assertTrue(set.matches(USER_TYPE_MEMBER, List.of(), List.of(TAG_X), 1));
        // Has user type but NOT tag
        assertFalse(set.matches(USER_TYPE_MEMBER, List.of(), List.of(), 1));
    }

    // -- OR mode --

    @Test
    void orModeAllowsAnyMatch() {
        var set = new RestrictionSet(
                List.of(userTypeRestriction(USER_TYPE_TEAM), groupRestriction(GROUP_A), tagRestriction(TAG_X)),
                RestrictionMode.OR);

        // Only user type matches
        assertTrue(set.matches(USER_TYPE_TEAM, List.of(), List.of(), 1));
        // Only group matches
        assertTrue(set.matches(null, List.of(GROUP_A), List.of(), 1));
        // Only tag matches
        assertTrue(set.matches(null, List.of(), List.of(TAG_X), 1));
        // Nothing matches
        assertFalse(set.matches(USER_TYPE_MEMBER, List.of(), List.of(), 1));
    }

    // -- Member restrictions (always OR) --

    @Test
    void memberRestrictionGrantsAccessRegardlessOfMode() {
        var set = new RestrictionSet(
                List.of(userTypeRestriction(USER_TYPE_TEAM), memberRestriction(MEMBER_42)), RestrictionMode.AND);

        // Member 42 passes even without the required user type
        assertTrue(set.matches(null, List.of(), List.of(), MEMBER_42));
        // Other member must satisfy user type restriction
        assertFalse(set.matches(null, List.of(), List.of(), 99));
        assertTrue(set.matches(USER_TYPE_TEAM, List.of(), List.of(), 99));
    }

    @Test
    void memberOnlyRestrictionDeniesOtherMembers() {
        var set = new RestrictionSet(List.of(memberRestriction(MEMBER_42)), RestrictionMode.AND);

        assertTrue(set.matches(null, List.of(), List.of(), MEMBER_42));
        assertFalse(set.matches(null, List.of(), List.of(), 99));
    }

    // -- hasRestrictions --

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
