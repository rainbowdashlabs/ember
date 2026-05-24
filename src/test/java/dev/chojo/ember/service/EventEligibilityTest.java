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

    private static final int ROLE_MEMBER = 1;
    private static final int ROLE_TEAM = 2;
    private static final int GROUP_A = 10;
    private static final int TAG_X = 20;
    private static final int MEMBER_42 = 42;

    private Restriction roleRestriction(int roleId) {
        return new Restriction(0, roleId, null, null, null);
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
        assertTrue(set.matches(List.of(ROLE_MEMBER), List.of(), List.of(), 1));
    }

    // -- Role restrictions (AND mode) --

    @Test
    void roleRestrictionMatchesWhenMemberHasRole() {
        var set = new RestrictionSet(List.of(roleRestriction(ROLE_MEMBER)), RestrictionMode.AND);
        assertTrue(set.matches(List.of(ROLE_MEMBER), List.of(), List.of(), 1));
    }

    @Test
    void roleRestrictionRejectsWhenMemberLacksRole() {
        var set = new RestrictionSet(List.of(roleRestriction(ROLE_TEAM)), RestrictionMode.AND);
        assertFalse(set.matches(List.of(ROLE_MEMBER), List.of(), List.of(), 1));
    }

    // -- Group restrictions (AND mode) --

    @Test
    void groupRestrictionMatchesWhenMemberInGroup() {
        var set = new RestrictionSet(List.of(groupRestriction(GROUP_A)), RestrictionMode.AND);
        assertTrue(set.matches(List.of(), List.of(GROUP_A), List.of(), 1));
    }

    @Test
    void groupRestrictionRejectsWhenMemberNotInGroup() {
        var set = new RestrictionSet(List.of(groupRestriction(GROUP_A)), RestrictionMode.AND);
        assertFalse(set.matches(List.of(), List.of(), List.of(), 1));
    }

    // -- Tag restrictions (AND mode) --

    @Test
    void tagRestrictionMatchesWhenMemberHasTag() {
        var set = new RestrictionSet(List.of(tagRestriction(TAG_X)), RestrictionMode.AND);
        assertTrue(set.matches(List.of(), List.of(), List.of(TAG_X), 1));
    }

    @Test
    void tagRestrictionRejectsWhenMemberLacksTag() {
        var set = new RestrictionSet(List.of(tagRestriction(TAG_X)), RestrictionMode.AND);
        assertFalse(set.matches(List.of(), List.of(), List.of(), 1));
    }

    // -- Combined AND logic --

    @Test
    void combinedAndRestrictionsRequireAllToMatch() {
        var set = new RestrictionSet(
                List.of(roleRestriction(ROLE_MEMBER), groupRestriction(GROUP_A), tagRestriction(TAG_X)),
                RestrictionMode.AND);

        // All match
        assertTrue(set.matches(List.of(ROLE_MEMBER), List.of(GROUP_A), List.of(TAG_X), 1));
        // Missing tag
        assertFalse(set.matches(List.of(ROLE_MEMBER), List.of(GROUP_A), List.of(), 1));
        // Missing group
        assertFalse(set.matches(List.of(ROLE_MEMBER), List.of(), List.of(TAG_X), 1));
        // Missing role
        assertFalse(set.matches(List.of(), List.of(GROUP_A), List.of(TAG_X), 1));
    }

    @Test
    void partialAndRestrictionsIgnoreUnsetTypes() {
        // Only role + tag (no group restriction)
        var set = new RestrictionSet(List.of(roleRestriction(ROLE_MEMBER), tagRestriction(TAG_X)), RestrictionMode.AND);

        // Has role + tag => eligible (group not restricted)
        assertTrue(set.matches(List.of(ROLE_MEMBER), List.of(), List.of(TAG_X), 1));
        // Has role but NOT tag
        assertFalse(set.matches(List.of(ROLE_MEMBER), List.of(), List.of(), 1));
    }

    // -- OR mode --

    @Test
    void orModeAllowsAnyMatch() {
        var set = new RestrictionSet(
                List.of(roleRestriction(ROLE_TEAM), groupRestriction(GROUP_A), tagRestriction(TAG_X)),
                RestrictionMode.OR);

        // Only role matches
        assertTrue(set.matches(List.of(ROLE_TEAM), List.of(), List.of(), 1));
        // Only group matches
        assertTrue(set.matches(List.of(), List.of(GROUP_A), List.of(), 1));
        // Only tag matches
        assertTrue(set.matches(List.of(), List.of(), List.of(TAG_X), 1));
        // Nothing matches
        assertFalse(set.matches(List.of(ROLE_MEMBER), List.of(), List.of(), 1));
    }

    // -- Member restrictions (always OR) --

    @Test
    void memberRestrictionGrantsAccessRegardlessOfMode() {
        var set = new RestrictionSet(
                List.of(roleRestriction(ROLE_TEAM), memberRestriction(MEMBER_42)), RestrictionMode.AND);

        // Member 42 passes even without the required role
        assertTrue(set.matches(List.of(), List.of(), List.of(), MEMBER_42));
        // Other member must satisfy role restriction
        assertFalse(set.matches(List.of(), List.of(), List.of(), 99));
        assertTrue(set.matches(List.of(ROLE_TEAM), List.of(), List.of(), 99));
    }

    @Test
    void memberOnlyRestrictionDeniesOtherMembers() {
        var set = new RestrictionSet(List.of(memberRestriction(MEMBER_42)), RestrictionMode.AND);

        assertTrue(set.matches(List.of(), List.of(), List.of(), MEMBER_42));
        assertFalse(set.matches(List.of(), List.of(), List.of(), 99));
    }

    // -- hasRestrictions --

    @Test
    void hasRestrictionsReturnsFalseWhenEmpty() {
        var set = new RestrictionSet(List.of(), RestrictionMode.AND);
        assertFalse(set.hasRestrictions());
    }

    @Test
    void hasRestrictionsReturnsTrueWhenNotEmpty() {
        var set = new RestrictionSet(List.of(roleRestriction(ROLE_MEMBER)), RestrictionMode.AND);
        assertTrue(set.hasRestrictions());
    }
}
