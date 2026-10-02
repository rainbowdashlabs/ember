/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#GENERAL}: failures that belong to no one feature.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum GeneralRefusal implements Refusal {
    /** Nobody expected this one, and the log is where it is explained. */
    UNEXPECTED_FAULT(1, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UNEXPECTED_FAULT),

    /** The store refused in a way nothing here has a name for, which is ours to look into. */
    UNEXPECTED_FAULT_FROM_UNKNOWN_STATE(2, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UNEXPECTED_FAULT),

    /** A second row with details that may only exist once. */
    ALREADY_EXISTS(3, HttpStatus.CONFLICT, "Something with the same details is already there, so nothing was saved"),

    /** A row naming something gone, or something still depended on. */
    STILL_LINKED(
            4,
            HttpStatus.CONFLICT,
            "This names something that is no longer there, or something else still depends on it, "
                    + "so nothing was saved"),

    /** A value larger, longer or otherwise different from what can be kept. */
    DOES_NOT_FIT(
            5,
            HttpStatus.BAD_REQUEST,
            "Something in what was sent does not fit what can be stored here, so nothing was saved"),

    /** Two changes to the same thing at the same moment, one of which had to be undone. */
    CHANGE_COLLIDED(
            6,
            HttpStatus.CONFLICT,
            "Somebody changed the same thing at the same moment, so this change was undone. "
                    + "Trying again usually works"),

    /** Work that ran long enough to be stopped before it finished. */
    TOOK_TOO_LONG(
            7,
            HttpStatus.SERVICE_UNAVAILABLE,
            "This took too long and was stopped before anything was saved. Trying again may work"),

    /** The store Ember keeps its data in could not be reached. */
    STORE_UNREACHABLE(
            8,
            HttpStatus.SERVICE_UNAVAILABLE,
            "Ember could not reach the place it keeps its data, so nothing was saved. "
                    + "Trying again in a moment usually works"),

    /** An address that cannot name anything, because the identifier in it is not one. */
    ADDRESS_NOT_AN_IDENTIFIER(9, HttpStatus.NOT_FOUND, "That address does not name anything"),

    /** Something already loaded that turns out to belong to another station. */
    NOT_YOURS_TO_OPEN(10, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /**
     * Gone, or another station's, from the loader that does both at once. The two are one code
     * deliberately: telling them apart would say that the thing exists at another station, which
     * is what the shared answer is there to withhold.
     */
    NOT_HERE_OR_NOT_YOURS(11, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /**
     * A picture whose header declares more pixels than may be unpacked, wherever it was uploaded.
     * One code for every upload, because the one place that measures pictures is the one that
     * refuses them.
     */
    PICTURE_TOO_MANY_PIXELS(
            12,
            HttpStatus.CONTENT_TOO_LARGE,
            "That picture has too many pixels to be worked with, so nothing was saved. "
                    + "A smaller version of it will work"),

    /**
     * An upload to a picture that is none of the formats the picture takes, read from its bytes
     * rather than from what the browser declared. One code for every sized picture, because the one
     * pipeline that stores them is the one that refuses them.
     */
    PICTURE_KIND_NOT_TAKEN(
            13,
            HttpStatus.BAD_REQUEST,
            "That file is not a picture that can be kept here, so nothing was saved. "
                    + "A PNG, JPEG, GIF or WebP file will work"),

    /** An upload to a picture that weighs more than the place it is for takes. */
    PICTURE_TOO_LARGE(
            14,
            HttpStatus.CONTENT_TOO_LARGE,
            "That picture is larger than this place takes, so nothing was saved. A smaller file will work"),

    /** A request to something that lives at a station, sent without naming one. */
    NO_STATION_CHOSEN(15, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** A request to something that lives at a station, from somebody who is no member of it. */
    NOT_A_MEMBER_OF_THIS_STATION(16, HttpStatus.FORBIDDEN, "You are not a member of this station"),

    /** A request to something only somebody signed in may do, with no session behind it. */
    NOT_SIGNED_IN(17, HttpStatus.UNAUTHORIZED, Sentences.SIGN_IN_FIRST),

    /** Any request from one address far more often than the instance answers for anybody. */
    REQUESTS_TOO_OFTEN(18, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A request to a route that needs a signed-in caller, sent without any session. */
    ROUTE_NEEDS_SIGN_IN(19, HttpStatus.UNAUTHORIZED, Sentences.SIGN_IN_FIRST),

    /** A session that has ended, been signed out or never existed. */
    SIGN_IN_SESSION_NOT_VALID(20, HttpStatus.UNAUTHORIZED, "Your sign-in has ended. Sign in again"),

    /** A request naming a station in its header that is not on this instance. */
    REQUESTED_STATION_NOT_HERE(21, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_HERE),

    /** A request whose station header does not read as a station's identity. */
    REQUESTED_STATION_NOT_AN_IDENTITY(22, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_AN_IDENTITY),

    /** A request naming a cluster in its header that is not on this instance. */
    REQUESTED_CLUSTER_NOT_HERE(23, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NOT_HERE),

    /** A request whose cluster header does not read as a cluster's identity. */
    REQUESTED_CLUSTER_NOT_AN_IDENTITY(24, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NOT_AN_IDENTITY),

    /** A route asked for by somebody holding none of the permissions it needs. */
    ROUTE_PERMISSION_MISSING(25, HttpStatus.FORBIDDEN, "You do not have the permission this needs");

    private final Definition definition;

    GeneralRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.GENERAL, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
