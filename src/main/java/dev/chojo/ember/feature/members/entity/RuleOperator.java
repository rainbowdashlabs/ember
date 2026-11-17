/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * How a check of a member rule compares. Which operators a check offers depends on its subject and,
 * for a field, on the field's type; {@link RuleOperators} holds that table.
 */
public enum RuleOperator {
    /** Is in the group, or holds the tag. */
    IN,
    /** Is not in the group, or does not hold the tag. */
    NOT_IN,
    /** The user type or answer is one of the given ones. */
    ONE_OF,
    /** The user type or answer is none of the given ones; an empty answer counts as none of them. */
    NONE_OF,
    /** At least the given duration or age. */
    AT_LEAST,
    /** Less than the given duration or age. */
    LESS_THAN,
    /** A number or an age within the given range, both ends included. */
    BETWEEN,
    /** A yes-or-no field answered yes. */
    IS_YES,
    /** A yes-or-no field answered no. */
    IS_NO,
    /** A yes-or-no field answered no or not answered. */
    IS_NOT_YES,
    /** The field holds no answer. */
    EMPTY,
    /** The field holds an answer. */
    NOT_EMPTY,
    /** The text equals the given one, ignoring case and surrounding space. */
    EQUALS,
    /** The text contains the given one, ignoring case. */
    CONTAINS,
    /** The number equals the given one. */
    EQ,
    /** The number is less than the given one. */
    LT,
    /** The number is at most the given one. */
    LE,
    /** The number is more than the given one. */
    GT,
    /** The number is at least the given one. */
    GE,
    /** The date lies before the given date. */
    BEFORE,
    /** The date lies on or after the given date. */
    ON_OR_AFTER,
    /** The date lies between the reference day and the given duration after it. */
    WITHIN_NEXT,
    /** The date lies between the given duration before the reference day and the reference day. */
    WITHIN_LAST,
    /** The expiry date lies on or after the reference day and within the given duration from it. */
    EXPIRES_WITHIN,
    /** The expiry date lies before the reference day. */
    EXPIRED,
    /** The expiry state on the reference day is one of the given ones. */
    STATE_IN
}
