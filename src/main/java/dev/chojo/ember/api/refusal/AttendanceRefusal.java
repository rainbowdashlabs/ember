/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#ATTENDANCE}: attendance.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum AttendanceRefusal implements Refusal {
    /**
     * An attendance sheet that is gone, whose template is gone, or that sits at another station.
     * One code for all three deliberately: the lookup asks only about this station's own sheets, and
     * telling them apart would say that the sheet exists at a station the reader may not see.
     */
    ATTENDANCE_SHEET_NOT_HERE(1, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE),

    /**
     * A template that is gone, or belongs to another station. One code deliberately: the lookup and
     * the station check right behind it answer alike, because telling them apart would say that the
     * template exists at a station the reader may not see.
     */
    ATTENDANCE_TEMPLATE_NOT_HERE_OR_NOT_YOURS(2, HttpStatus.NOT_FOUND, Sentences.EVENT_TEMPLATE_NOT_HERE),

    /** An entry asked for by a route that then checks which station its sheet is at. */
    ATTENDANCE_ENTRY_NOT_HERE(3, HttpStatus.NOT_FOUND, "That entry on the attendance sheet is not here any more"),

    /**
     * A member that is gone, or belongs to another station. One code deliberately: the lookup and
     * the station check right behind it answer alike, because telling them apart would say that the
     * member is at a station the reader may not see.
     */
    ATTENDANCE_MEMBER_NOT_HERE(4, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A template written down without a name. */
    ATTENDANCE_TEMPLATE_NEEDS_A_NAME(5, HttpStatus.BAD_REQUEST, Sentences.ATTENDANCE_TEMPLATE_NEEDS_A_NAME),

    /** A template that went between the station check and the read of its fields and groups. */
    ATTENDANCE_TEMPLATE_GONE_WHILE_READ(6, HttpStatus.NOT_FOUND, Sentences.EVENT_TEMPLATE_NOT_HERE),

    /** A template renamed to nothing. */
    ATTENDANCE_TEMPLATE_RENAME_NEEDS_A_NAME(7, HttpStatus.BAD_REQUEST, Sentences.ATTENDANCE_TEMPLATE_NEEDS_A_NAME),

    /** A template that went before the rename reached it. */
    ATTENDANCE_TEMPLATE_NOT_HERE_TO_CHANGE(8, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_TEMPLATE_NOT_HERE_ON_WRITE),

    /** A template that went before the deletion reached it. */
    ATTENDANCE_TEMPLATE_NOT_HERE_TO_DELETE(9, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_TEMPLATE_NOT_HERE_ON_WRITE),

    /** A field added to a template without a name, without a kind, or without either. */
    ATTENDANCE_FIELD_DETAILS_MISSING(10, HttpStatus.BAD_REQUEST, Sentences.ATTENDANCE_FIELD_DETAILS_MISSING),

    /** A field on a template left without a name or without a kind while it was being changed. */
    ATTENDANCE_FIELD_CHANGE_DETAILS_MISSING(11, HttpStatus.BAD_REQUEST, Sentences.ATTENDANCE_FIELD_DETAILS_MISSING),

    /** A field that went before the change reached it. */
    ATTENDANCE_FIELD_NOT_HERE_TO_CHANGE(12, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_FIELD_NOT_HERE),

    /** A field that went before the deletion reached it. */
    ATTENDANCE_FIELD_NOT_HERE_TO_DELETE(13, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_FIELD_NOT_HERE),

    /** A day the sheet was looked for on that does not read as a date. */
    ATTENDANCE_DAY_NOT_A_DATE(14, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A sheet that went between the station check and the read of its fields and entries. */
    ATTENDANCE_SHEET_GONE_WHILE_READ(15, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE),

    /** A sheet that went before it could be reopened. */
    ATTENDANCE_SHEET_NOT_HERE_TO_REOPEN(16, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE_ON_WRITE),

    /** A sheet that went before it could be closed. */
    ATTENDANCE_SHEET_NOT_HERE_TO_CLOSE(17, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE_ON_WRITE),

    /** A sheet that went before the change reached it. */
    ATTENDANCE_SHEET_NOT_HERE_TO_CHANGE(18, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE_ON_WRITE),

    /** A sheet that went before the deletion reached it. */
    ATTENDANCE_SHEET_NOT_HERE_TO_DELETE(19, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE_ON_WRITE),

    /** An entry put on a sheet without saying which member it is for. */
    ATTENDANCE_ENTRY_NAMES_NO_MEMBER(
            20, HttpStatus.BAD_REQUEST, "Name the member this entry is for, so nothing was saved"),

    /** An arrival written against an entry that went first. */
    ATTENDANCE_CHECK_IN_ENTRY_NOT_HERE(21, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE),

    /** A departure written against an entry that went first. */
    ATTENDANCE_CHECK_OUT_ENTRY_NOT_HERE(22, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE),

    /** An entry that went before the deletion reached it. */
    ATTENDANCE_ENTRY_NOT_HERE_TO_DELETE(23, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE),

    /** An entry changed without saying what the member's attendance now is. */
    ATTENDANCE_STATUS_NOT_GIVEN(
            24,
            HttpStatus.BAD_REQUEST,
            "Say whether the member was present, was absent, declined or is still unconfirmed, "
                    + "so nothing was saved"),

    /** An attendance written against an entry that went first. */
    ATTENDANCE_STATUS_ENTRY_NOT_HERE(25, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE),

    /** Times cleared on an entry that went first. */
    ATTENDANCE_RESET_TIMES_ENTRY_NOT_HERE(26, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE),

    /** A sheet asked for as a PDF that could not be made into one. */
    ATTENDANCE_SHEET_PDF_NOT_MADE(27, HttpStatus.NOT_FOUND, "That attendance sheet could not be handed out as a PDF"),

    /** A report asked for without the span of days it is to cover. */
    ATTENDANCE_REPORT_SPAN_MISSING(
            28, HttpStatus.BAD_REQUEST, "Give the first and the last day the report is to cover"),

    /** A report asked for without naming whom it is about. */
    ATTENDANCE_REPORT_AUDIENCE_MISSING(
            29, HttpStatus.BAD_REQUEST, "Name at least one kind of member or one group for the report"),

    /** A report asked for as a table file, with nothing to put in it. */
    ATTENDANCE_REPORT_TABLE_EMPTY(30, HttpStatus.NOT_FOUND, Sentences.NOTHING_TO_EXPORT),

    /** A report asked for as a PDF, with nothing to put in it. */
    ATTENDANCE_REPORT_PDF_EMPTY(31, HttpStatus.NOT_FOUND, Sentences.NOTHING_TO_EXPORT),

    /** A set of report settings kept for later without a name. */
    ATTENDANCE_REPORT_PRESET_NEEDS_A_NAME(
            32, HttpStatus.BAD_REQUEST, "A saved report needs a name, so nothing was saved"),

    /** A saved report of another station, asked to be thrown away. */
    ATTENDANCE_REPORT_PRESET_NOT_YOURS(33, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_REPORT_PRESET_NOT_HERE),

    /** A saved report that went before the deletion reached it. */
    ATTENDANCE_REPORT_PRESET_NOT_HERE_TO_DELETE(34, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_REPORT_PRESET_NOT_HERE),

    /** An absence written down without saying whom it is for. */
    ABSENCE_MEMBER_NOT_NAMED(35, HttpStatus.BAD_REQUEST, "Name the member this absence is for, so nothing was saved"),

    /** An absence written down without its first or its last day. */
    ABSENCE_SPAN_MISSING(36, HttpStatus.BAD_REQUEST, Sentences.ABSENCE_SPAN_MISSING),

    /** An absence whose last day falls before its first. */
    ABSENCE_ENDS_BEFORE_IT_STARTS(37, HttpStatus.BAD_REQUEST, Sentences.ABSENCE_ENDS_BEFORE_IT_STARTS),

    /** An absence asked to be thrown away that is not here. */
    ABSENCE_NOT_HERE(38, HttpStatus.NOT_FOUND, Sentences.ABSENCE_NOT_HERE),

    /** An absence that went before the deletion reached it. */
    ABSENCE_NOT_HERE_TO_DELETE(39, HttpStatus.NOT_FOUND, Sentences.ABSENCE_NOT_HERE_ON_WRITE),

    /** An absence written down for oneself without its first or its last day. */
    MY_ABSENCE_SPAN_MISSING(41, HttpStatus.BAD_REQUEST, Sentences.ABSENCE_SPAN_MISSING),

    /** An absence written down for oneself whose last day falls before its first. */
    MY_ABSENCE_ENDS_BEFORE_IT_STARTS(42, HttpStatus.BAD_REQUEST, Sentences.ABSENCE_ENDS_BEFORE_IT_STARTS),

    /** An absence written down for a member the reader does not look after. */
    ABSENCE_MEMBER_NOT_YOURS(43, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** An absence of one's own asked to be taken back that is not here. */
    MY_ABSENCE_NOT_HERE(44, HttpStatus.NOT_FOUND, Sentences.ABSENCE_NOT_HERE),

    /** An absence of somebody the reader neither is nor looks after, asked to be taken back. */
    MY_ABSENCE_NOT_YOURS_TO_DELETE(45, HttpStatus.FORBIDDEN, "That absence is not yours to take back"),

    /** An absence of one's own that went before the deletion reached it. */
    MY_ABSENCE_NOT_HERE_TO_DELETE(46, HttpStatus.NOT_FOUND, Sentences.ABSENCE_NOT_HERE_ON_WRITE),

    /** A field of an attendance sheet given a type the sheet does not offer. */
    ATTENDANCE_FIELD_TYPE_NOT_OFFERED(
            47, HttpStatus.BAD_REQUEST, "Attendance sheets do not offer that type, so nothing was saved"),

    /** A field of an attendance sheet set up to start from a value it would not take as an answer. */
    ATTENDANCE_FIELD_DEFAULT_NOT_ACCEPTED(48, HttpStatus.BAD_REQUEST, Sentences.QUESTION_DEFAULT_NOT_ACCEPTED),

    /** An attendance sheet that ends before, or at the moment, it starts. */
    ATTENDANCE_SHEET_ENDS_BEFORE_IT_STARTS(
            49, HttpStatus.BAD_REQUEST, "The sheet has to end after it starts, so nothing was saved"),

    /** An attendance sheet running longer than a sheet may, raised with the longest it may run. */
    ATTENDANCE_SHEET_TOO_LONG(
            50,
            HttpStatus.BAD_REQUEST,
            "The sheet runs longer than a sheet may, so nothing was saved. The longest it may run is"),

    /** The hours a sheet counts a presence as, given as a negative number. */
    ATTENDANCE_COUNTED_HOURS_NEGATIVE(
            51, HttpStatus.BAD_REQUEST, "The hours a sheet counts as cannot be negative, so nothing was saved"),

    /** The hours a sheet counts a presence as, given as more than a sheet may count, raised with the most. */
    ATTENDANCE_COUNTED_HOURS_TOO_MANY(
            52,
            HttpStatus.BAD_REQUEST,
            "The hours a sheet counts as are more than it may count, so nothing was saved. The most it may count is"),

    /** A field of a sheet filled in with something it does not take. */
    ATTENDANCE_SHEET_ANSWER_NOT_ACCEPTED(
            53, HttpStatus.BAD_REQUEST, "That answer does not suit this field of the sheet, so nothing was saved"),

    /**
     * Anything written to an attendance sheet that is closed. One code for every way of writing to
     * one, because a closed sheet refuses them all for the same reason.
     */
    ATTENDANCE_SHEET_CLOSED(
            54,
            HttpStatus.BAD_REQUEST,
            "This attendance sheet is closed and has to be reopened first, so nothing was changed"),

    /** A member put on a sheet for a day before they joined the station. */
    ATTENDANCE_MEMBER_NOT_YET_JOINED(
            55, HttpStatus.BAD_REQUEST, "The member had not joined the station on this date, so nothing was saved"),

    /** A saved report that names neither a user type nor a group. */
    ATTENDANCE_REPORT_PRESET_SELECTS_NOBODY(
            56,
            HttpStatus.BAD_REQUEST,
            "A saved report has to name at least one user type or group, so nothing was saved"),

    /** A saved report whose user types or groups include an empty entry. */
    ATTENDANCE_REPORT_PRESET_EMPTY_ENTRY(
            57, HttpStatus.BAD_REQUEST, "A saved report cannot name an empty entry, so nothing was saved");

    private final Definition definition;

    AttendanceRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.ATTENDANCE, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
