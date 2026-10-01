/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import java.util.Optional;
import java.util.function.Function;

/**
 * The one list of field types every feature with fields of its own picks from.
 *
 * <p>Six features each kept their own list and spelled the same thing three ways: a line of text was
 * {@code STRING} in some and {@code TEXT} in others, a choice was {@code ENUM} or {@code CHOICE}.
 * Here each name has one meaning. A feature declares which of them it offers in {@link FieldTypes}.
 *
 * <p>Most types are one of the {@link QuestionKind}s under the same name. The rest carry something a
 * kind alone does not: a date of birth or an expiry is a date that more than one thing reads, a place
 * is a line of text an appointment hands to calendars, and a member field may be narrowed to a group,
 * a user type or a tag. An age, a heading and a gap hold no value at all.
 *
 * <p>A number is whole unless its settings carry a step below one, which is how a length in metres
 * is told from a count of people without a second type.
 */
public enum FieldType {
    /** A line of text. */
    TEXT,
    /** Several lines of text. */
    LONG_TEXT,
    /** A number, whole unless its step says otherwise. */
    NUMBER,
    /** A calendar date. */
    DATE,
    /** A time of day. */
    TIME,
    /** Yes or no. */
    BOOLEAN,
    /** One of a written-down set of answers. */
    CHOICE,
    /** A web address. */
    URL,
    /** One member. */
    MEMBER,
    /** Any number of members. */
    MEMBER_LIST,
    /** The date of birth, one per station or list, which ages are counted from. */
    BIRTH_DATE,
    /** The last day something is valid, which reminders count towards. */
    EXPIRY_DATE,
    /** An age, counted from a date field and never entered. */
    AGE,
    /** A heading between fields, holding nothing. */
    SECTION,
    /** A gap on a row, holding nothing. */
    SPACER,
    /** One line of text naming a place, which calendars and invitations read. */
    LOCATION,
    /** One member of one group. */
    MEMBER_OF_GROUP,
    /** Any number of members of one group. */
    MEMBER_LIST_OF_GROUP,
    /** One member of one user type. */
    MEMBER_OF_TYPE,
    /** Any number of members of one user type. */
    MEMBER_LIST_OF_TYPE,
    /** One member carrying one tag. */
    MEMBER_OF_TAG,
    /** Any number of members carrying one tag. */
    MEMBER_LIST_OF_TAG,
    /** One member, who becomes the ticket's assignee when it moves into the configured lane. */
    LANE_ASSIGNEE;

    /**
     * The kind of answer this type takes, which is what the one check measures an answer against.
     *
     * <p>A number answers as {@link QuestionKind#NUMBER} here; its settings turn it into a decimal
     * where they carry a step below one.
     *
     * @return the kind, or nothing for a type that holds no value
     */
    public Optional<QuestionKind> kind() {
        return switch (this) {
            case TEXT, LOCATION -> Optional.of(QuestionKind.TEXT);
            case LONG_TEXT -> Optional.of(QuestionKind.LONG_TEXT);
            case NUMBER -> Optional.of(QuestionKind.NUMBER);
            case DATE, BIRTH_DATE, EXPIRY_DATE -> Optional.of(QuestionKind.DATE);
            case TIME -> Optional.of(QuestionKind.TIME);
            case BOOLEAN -> Optional.of(QuestionKind.BOOLEAN);
            case CHOICE -> Optional.of(QuestionKind.CHOICE);
            case URL -> Optional.of(QuestionKind.URL);
            case MEMBER, MEMBER_OF_GROUP, MEMBER_OF_TYPE, MEMBER_OF_TAG, LANE_ASSIGNEE ->
                Optional.of(QuestionKind.MEMBER);
            case MEMBER_LIST, MEMBER_LIST_OF_GROUP, MEMBER_LIST_OF_TYPE, MEMBER_LIST_OF_TAG ->
                Optional.of(QuestionKind.MEMBER_LIST);
            case AGE, SECTION, SPACER -> Optional.empty();
        };
    }

    /** Whether a field of this type holds a value of its own. */
    public boolean holdsValue() {
        return kind().isPresent();
    }

    /**
     * Whether the value is worked out from another field rather than given.
     *
     * <p>An age counts itself from a date, so nobody writes one and nobody has changed one: a change
     * recorded against it is a change nobody made.
     */
    public boolean isCalculated() {
        return this == AGE;
    }

    /** Whether an answer to this names members. */
    public boolean namesMembers() {
        return kind().map(QuestionKind::namesMembers).orElse(false);
    }

    /**
     * The name a wire enum sends this type under.
     *
     * <p>Shared appointments and shared boards still spell their field types the way partner
     * stations read them, so each of those two enums says which shared type each of its names
     * stands for, and this finds the name again.
     *
     * @param wire    the wire enum
     * @param meaning the shared type each wire name stands for
     * @return the wire name for this type
     * @throws IllegalArgumentException where the wire enum has no name for this type
     */
    public <E extends Enum<E>> E spelledAs(Class<E> wire, Function<E, FieldType> meaning) {
        for (E constant : wire.getEnumConstants()) {
            if (meaning.apply(constant) == this) return constant;
        }
        throw new IllegalArgumentException(wire.getSimpleName() + " has no name for " + this);
    }

    /** Which members a field of this type may name. */
    public MemberConstraint constraint() {
        return switch (this) {
            case MEMBER_OF_GROUP, MEMBER_LIST_OF_GROUP -> MemberConstraint.GROUP;
            case MEMBER_OF_TYPE, MEMBER_LIST_OF_TYPE -> MemberConstraint.USER_TYPE;
            case MEMBER_OF_TAG, MEMBER_LIST_OF_TAG -> MemberConstraint.TAG;
            default -> MemberConstraint.NONE;
        };
    }
}
