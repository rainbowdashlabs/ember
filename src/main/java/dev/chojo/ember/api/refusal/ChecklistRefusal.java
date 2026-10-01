/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#CHECKLISTS}: checklists.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum ChecklistRefusal implements Refusal {
    /** A checklist written down without a name. */
    CHECKLIST_NEEDS_A_NAME(1, HttpStatus.BAD_REQUEST, Sentences.CHECKLIST_NEEDS_A_NAME),

    /** A checklist written down with no column to tick anything off in. */
    CHECKLIST_NEEDS_A_COLUMN(2, HttpStatus.BAD_REQUEST, "A checklist needs at least one column, so nothing was saved"),

    /** A checklist renamed to nothing. */
    CHECKLIST_RENAME_NEEDS_A_NAME(3, HttpStatus.BAD_REQUEST, Sentences.CHECKLIST_NEEDS_A_NAME),

    /** A checklist told to follow a filter and an appointment at once. */
    CHECKLIST_FOLLOWS_ONE_THING(
            4,
            HttpStatus.BAD_REQUEST,
            "A checklist follows either a filter or an appointment, never both, so nothing was changed"),

    /** Columns reordered without saying what the new order is. */
    CHECKLIST_COLUMN_ORDER_MISSING(
            5, HttpStatus.BAD_REQUEST, "Name the columns in the order they are to stand in, so nothing was changed"),

    /** A new order of columns that leaves one out, or names one twice. */
    CHECKLIST_COLUMN_ORDER_INCOMPLETE(
            6,
            HttpStatus.BAD_REQUEST,
            "A new order has to name every column of this checklist exactly once, so nothing was changed"),

    /** Members put on a checklist without naming any of them. */
    CHECKLIST_NAMES_NO_MEMBERS(
            7, HttpStatus.BAD_REQUEST, "Name the members to put on the checklist, so nothing was saved"),

    /** A whole column ticked off without naming the rows it is to be ticked off on. */
    CHECKLIST_NAMES_NO_ROWS(
            8, HttpStatus.BAD_REQUEST, "Name the rows the column is to be set on, so nothing was changed"),

    /** A checklist that could not be drawn as a PDF. */
    CHECKLIST_PDF_NOT_MADE(9, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHECKLIST_PDF_NOT_MADE),

    /** A checklist whose PDF was still being drawn when the work was cut short. */
    CHECKLIST_PDF_INTERRUPTED(10, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHECKLIST_PDF_NOT_MADE),

    /** A column of a checklist that is gone. */
    CHECKLIST_COLUMN_NOT_HERE(11, HttpStatus.NOT_FOUND, "That column is not here any more"),

    /** A column of one checklist reached through another. */
    CHECKLIST_COLUMN_ON_ANOTHER_LIST(12, HttpStatus.FORBIDDEN, "That column belongs to another checklist"),

    /** A row of a checklist that is gone. */
    CHECKLIST_ROW_NOT_HERE(13, HttpStatus.NOT_FOUND, "That row is not here any more"),

    /** A row of one checklist reached through another. */
    CHECKLIST_ROW_ON_ANOTHER_LIST(14, HttpStatus.FORBIDDEN, "That row belongs to another checklist"),

    /** A checklist told to follow an appointment without saying which day of it. */
    CHECKLIST_OCCURRENCE_DAY_MISSING(
            15,
            HttpStatus.BAD_REQUEST,
            "Name the day of the appointment the checklist is to follow, so nothing was saved"),

    /** An appointment a checklist was told to follow that is gone, or belongs to another station. */
    CHECKLIST_APPOINTMENT_NOT_HERE(16, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** An appointment the reader may not see at all, named as the one a checklist is to follow. */
    CHECKLIST_APPOINTMENT_NOT_YOURS_TO_FOLLOW(
            17, HttpStatus.FORBIDDEN, "You cannot see that appointment, so a checklist cannot follow it"),

    /** A day a checklist was told to follow that does not read as a date. */
    CHECKLIST_DAY_NOT_A_DATE(18, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A column of a checklist written down without a label. */
    CHECKLIST_COLUMN_NEEDS_A_LABEL(20, HttpStatus.BAD_REQUEST, "A column needs a label, so nothing was saved");

    private final Definition definition;

    ChecklistRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.CHECKLISTS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
