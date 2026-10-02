/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#EQUIPMENT}: equipment.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum EquipmentRefusal implements Refusal {
    /** The appointment a line of equipment hangs on, which is not here. */
    EQUIPMENT_APPOINTMENT_NOT_HERE(1, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /**
     * A line of what an appointment needs that is not here, or one hanging on another appointment.
     *
     * <p>One code for both on purpose. The appointment in the path is what scopes the line to the
     * caller's station, so a line of another appointment has to look absent.
     */
    EQUIPMENT_NEED_NOT_HERE(2, HttpStatus.NOT_FOUND, "That line of what the appointment needs is not here any more"),

    /** What an appointment needs, counted against what is there, and refused by the count. */
    EQUIPMENT_COVERAGE_NOT_WORKED_OUT(
            3, HttpStatus.BAD_REQUEST, "What the appointment needs could not be counted against what is there"),

    /** A line written onto an appointment that the station refused to take. */
    EQUIPMENT_NEED_NOT_SAVED(
            4, HttpStatus.BAD_REQUEST, "That line could not be written onto the appointment, so nothing was saved"),

    /** A line of an appointment changed to something the station refused to take. */
    EQUIPMENT_NEED_NOT_CHANGED(5, HttpStatus.BAD_REQUEST, "That line could not be changed, so nothing was saved"),

    /** A handover written down that names no piece at all. */
    EQUIPMENT_HANDOVER_NAMES_NOTHING(
            6, HttpStatus.BAD_REQUEST, "Name at least one piece that went out, so nothing was saved"),

    /** A piece written down as gone out that the station refused to take. */
    EQUIPMENT_HANDOVER_NOT_SAVED(
            7, HttpStatus.BAD_REQUEST, "That piece could not be written down as gone out, so nothing was saved"),

    /** A handover taken back after it had gone. */
    EQUIPMENT_HANDOVER_NOT_HERE_TO_UNDO(
            8, HttpStatus.NOT_FOUND, "That handover is not here any more, so nothing was changed"),

    /** Equipment asked about without naming the day it is about. */
    EQUIPMENT_DATE_MISSING(9, HttpStatus.BAD_REQUEST, Sentences.EQUIPMENT_DATE_MISSING),

    /** A day equipment was asked about on that does not read as a date. */
    EQUIPMENT_DATE_NOT_A_DATE(10, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** What goes with a piece, asked for without naming the piece. */
    RECOMMENDATION_PIECE_MISSING(11, HttpStatus.BAD_REQUEST, Sentences.EQUIPMENT_PIECE_MISSING),

    /** What goes with a piece, asked for with something that is not a number. */
    RECOMMENDATION_PIECE_NOT_A_NUMBER(12, HttpStatus.BAD_REQUEST, Sentences.EQUIPMENT_PIECE_MISSING),

    /** A collected list counted again without the span it is wanted for. */
    COLLECTED_LIST_WINDOW_MISSING(13, HttpStatus.BAD_REQUEST, "Give the first day the collected list is wanted for");

    private final Definition definition;

    EquipmentRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.EQUIPMENT, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
