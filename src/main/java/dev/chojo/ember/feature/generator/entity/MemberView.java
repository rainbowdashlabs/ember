/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.restriction.RestrictionAudience;

import java.util.function.Predicate;

/**
 * What decides which parts of a template one member's document holds: the audiences of the letter's
 * blocks the member belongs to, and how many guardians the member has, which decides a block's guardian
 * condition and how many people sign for a guardian.
 *
 * @param audience   whether the member belongs to the audience of a block's restriction
 * @param guardians  how many guardians the member has
 * @param everyBlock whether every block is seen whatever its restriction and condition, as by nobody in
 *                   particular
 */
public record MemberView(Predicate<RestrictionAudience> audience, int guardians, boolean everyBlock) {

    /**
     * Who sees everything: a preview without a member, and the conversion of a letter's texts. Two
     * guardians are as many as any signer asks about.
     */
    public static final MemberView EVERYBODY = new MemberView(audience -> true, SignatureRole.GUARDIANS_CHECKED, true);

    /**
     * @param audience  whether the member belongs to the audience of a block's restriction
     * @param guardians how many guardians the member has
     * @return what that member sees
     */
    public static MemberView of(Predicate<RestrictionAudience> audience, int guardians) {
        return new MemberView(audience, guardians, false);
    }

    /**
     * Any member the template could be for who belongs to no audience a block names, with the given
     * number of guardians. What such a member sees is printed for every member with that many.
     *
     * @param guardians how many guardians the member has
     * @return the view
     */
    public static MemberView anyMember(int guardians) {
        return of(MemberView::namesNobody, guardians);
    }

    /**
     * @param cell a block
     * @return whether the block is printed for the member, leaving aside the blocks it is stacked in
     */
    public boolean sees(ContentCell cell) {
        if (everyBlock) return true;
        var restriction = cell.restriction();
        var condition = cell.guardianCondition();
        return (restriction == null || audience.test(restriction)) && (condition == null || condition.holds(guardians));
    }

    private static boolean namesNobody(RestrictionAudience audience) {
        var selection = audience.toSelection();
        return selection.userTypes().isEmpty()
                && selection.groupIds().isEmpty()
                && selection.tagIds().isEmpty()
                && selection.memberIds().isEmpty();
    }
}
