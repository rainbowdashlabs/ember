/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.question.FieldType;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The one table of which operators a check of a member rule offers, and what each one needs as its
 * value. A check on a field takes its operators from the field's type; the builder and the server read
 * the same table, so an operator the builder never offers is also one the server refuses.
 */
public final class RuleOperators {
    private static final Set<FieldType> AGE_SOURCES = EnumSet.of(FieldType.DATE, FieldType.BIRTH_DATE);
    private static final Set<RuleOperator> COUNTS_FROM_REFERENCE = EnumSet.of(
            RuleOperator.WITHIN_NEXT,
            RuleOperator.WITHIN_LAST,
            RuleOperator.EXPIRES_WITHIN,
            RuleOperator.EXPIRED,
            RuleOperator.STATE_IN);

    private RuleOperators() {}

    /**
     * The operators a check offers, in the order the builder lists them, each with what its value
     * needs.
     *
     * @param subject   what the check looks at
     * @param fieldType the type of the field it reads; ignored for subjects that read no field
     * @return the operators, empty when the subject cannot read a field of that type
     */
    public static Map<RuleOperator, RuleNeed> offered(RuleSubject subject, @Nullable FieldType fieldType) {
        var offered = new LinkedHashMap<RuleOperator, RuleNeed>();
        switch (subject) {
            case GROUP, TAG -> {
                offered.put(RuleOperator.IN, RuleNeed.NOTHING);
                offered.put(RuleOperator.NOT_IN, RuleNeed.NOTHING);
            }
            case USER_TYPE -> {
                offered.put(RuleOperator.ONE_OF, RuleNeed.USER_TYPES);
                offered.put(RuleOperator.NONE_OF, RuleNeed.USER_TYPES);
            }
            case MEMBER_SINCE -> {
                offered.put(RuleOperator.AT_LEAST, RuleNeed.DURATION);
                offered.put(RuleOperator.LESS_THAN, RuleNeed.DURATION);
            }
            case AGE -> {
                if (fieldType != null && AGE_SOURCES.contains(fieldType)) {
                    offered.put(RuleOperator.AT_LEAST, RuleNeed.YEARS);
                    offered.put(RuleOperator.LESS_THAN, RuleNeed.YEARS);
                    offered.put(RuleOperator.BETWEEN, RuleNeed.YEARS_RANGE);
                }
            }
            case FIELD, CLUSTER_FIELD -> {
                if (fieldType != null) fieldOperators(fieldType, offered);
            }
        }
        return Collections.unmodifiableMap(offered);
    }

    /**
     * What an operator needs on a check, or empty when the check does not offer it.
     *
     * @param subject   what the check looks at
     * @param fieldType the type of the field it reads, if any
     * @param operator  the operator asked about
     */
    public static Optional<RuleNeed> need(RuleSubject subject, @Nullable FieldType fieldType, RuleOperator operator) {
        return Optional.ofNullable(offered(subject, fieldType).get(operator));
    }

    /**
     * Whether a check counts from a reference day, which is the current day unless the check names a
     * fixed one.
     */
    public static boolean countsFromReference(RuleSubject subject, RuleOperator operator) {
        return subject == RuleSubject.MEMBER_SINCE
                || subject == RuleSubject.AGE
                || COUNTS_FROM_REFERENCE.contains(operator);
    }

    private static void fieldOperators(FieldType fieldType, Map<RuleOperator, RuleNeed> offered) {
        var kind = fieldType.kind();
        if (kind.isEmpty() || fieldType.isCalculated()) return;
        switch (kind.get()) {
            case BOOLEAN -> {
                offered.put(RuleOperator.IS_YES, RuleNeed.NOTHING);
                offered.put(RuleOperator.IS_NO, RuleNeed.NOTHING);
                offered.put(RuleOperator.IS_NOT_YES, RuleNeed.NOTHING);
            }
            case CHOICE -> {
                offered.put(RuleOperator.ONE_OF, RuleNeed.ANSWERS);
                offered.put(RuleOperator.NONE_OF, RuleNeed.ANSWERS);
            }
            case TEXT, LONG_TEXT, URL -> {
                offered.put(RuleOperator.EQUALS, RuleNeed.TEXT);
                offered.put(RuleOperator.CONTAINS, RuleNeed.TEXT);
            }
            case NUMBER, DECIMAL -> {
                offered.put(RuleOperator.EQ, RuleNeed.NUMBER);
                offered.put(RuleOperator.LT, RuleNeed.NUMBER);
                offered.put(RuleOperator.LE, RuleNeed.NUMBER);
                offered.put(RuleOperator.GT, RuleNeed.NUMBER);
                offered.put(RuleOperator.GE, RuleNeed.NUMBER);
                offered.put(RuleOperator.BETWEEN, RuleNeed.NUMBER_RANGE);
            }
            case DATE -> {
                offered.put(RuleOperator.BEFORE, RuleNeed.DATE);
                offered.put(RuleOperator.ON_OR_AFTER, RuleNeed.DATE);
                offered.put(RuleOperator.WITHIN_NEXT, RuleNeed.DURATION);
                offered.put(RuleOperator.WITHIN_LAST, RuleNeed.DURATION);
                if (fieldType == FieldType.EXPIRY_DATE) {
                    offered.put(RuleOperator.EXPIRES_WITHIN, RuleNeed.DURATION);
                    offered.put(RuleOperator.EXPIRED, RuleNeed.NOTHING);
                    offered.put(RuleOperator.STATE_IN, RuleNeed.STATES);
                }
            }
            default -> {}
        }
        offered.put(RuleOperator.EMPTY, RuleNeed.NOTHING);
        offered.put(RuleOperator.NOT_EMPTY, RuleNeed.NOTHING);
    }

    /** Whether a field of this type can be read by a field check at all. */
    public static boolean readable(FieldType fieldType) {
        return !offered(RuleSubject.FIELD, fieldType).isEmpty();
    }

    /** Whether an age check can count from a field of this type. */
    public static boolean countsAgeFrom(FieldType fieldType) {
        return AGE_SOURCES.contains(fieldType);
    }
}
