/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#BEACON}: the beacon.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum BeaconRefusal implements Refusal {
    /** What a beacon has gathered, asked for on an instance that is not a beacon. */
    BEACON_NOT_RECEIVING(1, HttpStatus.NOT_FOUND, Sentences.BEACON_NOT_RECEIVING),

    /** A fault marked as seen after it had gone. */
    BEACON_FAULT_NOT_HERE(2, HttpStatus.NOT_FOUND, "That fault is not here any more, so nothing was changed"),

    /** A report marked as seen after it had gone. */
    BEACON_REPORT_NOT_ACKNOWLEDGED(3, HttpStatus.NOT_FOUND, "That report is not here any more, so nothing was changed"),

    /** The report a picture was asked for, which is not here. */
    BEACON_REPORT_NOT_HERE_FOR_PICTURE(4, HttpStatus.NOT_FOUND, Sentences.BEACON_REPORT_NOT_HERE),

    /** A picture asked for from a report that came without one. */
    BEACON_REPORT_HAS_NO_PICTURE(5, HttpStatus.NOT_FOUND, "That report came without a picture"),

    /** A picture a report names whose stored file is gone. */
    BEACON_REPORT_PICTURE_NOT_HERE(6, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** Something a beacon holds, addressed by something that is not a number. */
    BEACON_ID_NOT_A_NUMBER(7, HttpStatus.BAD_REQUEST, Sentences.BEACON_ID_NOT_A_NUMBER),

    /** A problem addressed by something that is not a number. */
    BEACON_PROBLEM_ID_NOT_A_NUMBER(8, HttpStatus.BAD_REQUEST, Sentences.BEACON_ID_NOT_A_NUMBER),

    /** A problem asked for on an instance that writes nothing down about problems. */
    PROBLEM_LOG_NOT_RUNNING(9, HttpStatus.NOT_FOUND, Sentences.PROBLEM_LOG_NOT_RUNNING),

    /** A problem addressed by a number nothing written down here uses. */
    BEACON_PROBLEM_NOT_HERE(10, HttpStatus.NOT_FOUND, "That problem is not here any more"),

    /** A send of several problems that ticked none of them. */
    BEACON_NOTHING_CHOSEN_TO_SEND(11, HttpStatus.BAD_REQUEST, "Tick at least one problem to send, so nothing was sent"),

    /** Several problems sent on an instance that writes nothing down about problems. */
    PROBLEM_LOG_NOT_RUNNING_ON_SEND(12, HttpStatus.NOT_FOUND, Sentences.PROBLEM_LOG_NOT_RUNNING),

    /** A report addressed by a number nothing here uses. */
    BEACON_REPORT_NOT_HERE(13, HttpStatus.NOT_FOUND, Sentences.BEACON_REPORT_NOT_HERE),

    /** Something sent on from an instance that reports to no beacon. */
    BEACON_NOT_SET_UP(14, HttpStatus.BAD_REQUEST, "This instance reports to no beacon, so nothing was sent"),

    /** A delivery to an instance that is not a beacon. */
    BEACON_INTAKE_NOT_RECEIVING(15, HttpStatus.NOT_FOUND, Sentences.BEACON_NOT_RECEIVING),

    /** More deliveries from one address than a beacon takes. */
    BEACON_INTAKE_TOO_MANY(16, HttpStatus.FORBIDDEN, "Too many deliveries from this address"),

    /** A delivery larger than anything honest a beacon reads. */
    BEACON_INTAKE_TOO_LARGE(17, HttpStatus.BAD_REQUEST, "That delivery is larger than a beacon reads"),

    /** A delivery that carries neither a key nor a signature. */
    BEACON_INTAKE_NOT_SIGNED(18, HttpStatus.FORBIDDEN, "A delivery to a beacon has to be signed"),

    /** A delivery whose signature does not match what was sent. */
    BEACON_INTAKE_SIGNATURE_NOT_GOOD(19, HttpStatus.FORBIDDEN, "That signature does not match the delivery"),

    /** A delivery whose key is not a key at all. */
    BEACON_INTAKE_KEY_NOT_READ(20, HttpStatus.FORBIDDEN, "That is not a key"),

    /** A picture delivery whose picture could not be kept. */
    BEACON_INTAKE_PICTURE_MISSING(21, HttpStatus.BAD_REQUEST, "A picture delivery arrived without a picture"),

    /** A delivery of figures written to a protocol version newer than this beacon reads. */
    BEACON_INTAKE_PROTOCOL_TOO_NEW(22, HttpStatus.BAD_REQUEST, Sentences.BEACON_PROTOCOL_TOO_NEW),

    /** A delivery to the beacon that does not say when it was sent or carries no one-time number. */
    BEACON_DELIVERY_ENVELOPE_INCOMPLETE(
            23, HttpStatus.BAD_REQUEST, "A delivery has to say when it was sent and carry a one-time number"),

    /** A signed delivery written to a protocol version newer than this beacon reads. */
    BEACON_DELIVERY_PROTOCOL_TOO_NEW(24, HttpStatus.BAD_REQUEST, Sentences.BEACON_PROTOCOL_TOO_NEW),

    /** A signed delivery sent too long before or after now to be trusted. */
    BEACON_DELIVERY_OUT_OF_TIME(25, HttpStatus.FORBIDDEN, "That delivery was sent too long before or after now"),

    /** A signed delivery addressed to another beacon, or to nothing that reads as an address. */
    BEACON_DELIVERY_FOR_ANOTHER_BEACON(26, HttpStatus.FORBIDDEN, "That delivery was addressed to another beacon"),

    /** A signed delivery that has already arrived once. */
    BEACON_DELIVERY_ALREADY_TAKEN(27, HttpStatus.CONFLICT, "That delivery has already arrived once"),

    /** A fault delivered without the fingerprint it is filed under. */
    BEACON_FAULT_FINGERPRINT_MISSING(28, HttpStatus.BAD_REQUEST, "A fault has to carry its fingerprint"),

    /** A report delivered with nothing written in it. */
    BEACON_REPORT_MESSAGE_MISSING(29, HttpStatus.BAD_REQUEST, "A report needs something written in it"),

    /** A delivery of figures with no entries in it. */
    BEACON_FIGURES_WITHOUT_SUBJECTS(30, HttpStatus.BAD_REQUEST, "A delivery of figures needs at least one entry"),

    /** A delivery of figures with more entries than a beacon takes. */
    BEACON_FIGURES_TOO_MANY_SUBJECTS(
            31, HttpStatus.BAD_REQUEST, "That delivery of figures carries more entries than a beacon takes"),

    /** A delivery of figures that does not name a day it is for. */
    BEACON_FIGURES_DAY_MISSING(32, HttpStatus.BAD_REQUEST, "A delivery of figures has to name the day it is for"),

    /** A delivery of figures for a day that has not happened yet. */
    BEACON_FIGURES_DAY_IN_THE_FUTURE(
            33, HttpStatus.BAD_REQUEST, "A delivery of figures cannot be for a day that has not happened yet");

    private final Definition definition;

    BeaconRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.BEACON, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
