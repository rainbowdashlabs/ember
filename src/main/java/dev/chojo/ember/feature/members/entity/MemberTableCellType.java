/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.question.FieldType;

/**
 * What a column of a drawn table holds, so a screen can sort and filter it by what it is.
 *
 * <p>The cells themselves are words, already cut and formatted for the reader, and a screen guessing
 * their kind back from the words got it wrong as soon as a question happened to look like a date. The
 * table says it instead, once, where the column is known.
 *
 * <p>A date and a birth date are written as days of the form {@code dd.MM.yyyy}; a boolean as the
 * word the station reads it by; a number as digits. Everything else is text as it was answered.
 */
public enum MemberTableCellType {
    TEXT,
    NUMBER,
    DATE,
    /** A date of birth, which a screen may also filter by the age it gives. */
    BIRTH_DATE,
    BOOLEAN,
    /** One of a fixed set of tokens the screen has its own words for, such as a kind of member. */
    ENUM;

    /**
     * The kind of cell a question of this type draws, whichever feature asks it.
     *
     * <p>An age is a number, however it is worked out. A choice is text, because the table writes it
     * as it was answered and has no labels for it. An expiry date is a date: a drawn table is a
     * printout, and how close the day is changes after it has been printed.
     */
    public static MemberTableCellType of(FieldType type) {
        return switch (type) {
            case NUMBER, AGE -> NUMBER;
            case DATE, EXPIRY_DATE -> DATE;
            case BIRTH_DATE -> BIRTH_DATE;
            case BOOLEAN -> BOOLEAN;
            default -> TEXT;
        };
    }
}
