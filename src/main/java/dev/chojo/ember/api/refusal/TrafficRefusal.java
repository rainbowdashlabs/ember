/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#TRAFFIC}: traffic.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum TrafficRefusal implements Refusal {
    /** Traffic figures for the whole instance asked for without one end of the stretch of time. */
    TRAFFIC_SPAN_MISSING(1, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_MISSING),

    /** One end of the stretch of time that cannot be read as a date and a time. */
    TRAFFIC_SPAN_NOT_A_TIME(2, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_NOT_A_TIME),

    /** A station narrowed down to by something that is not a whole number. */
    TRAFFIC_NUMBER_NOT_A_NUMBER(3, HttpStatus.BAD_REQUEST, "That has to be a whole number"),

    /** A kind of request to count that is not one of the kinds counted. */
    TRAFFIC_KIND_UNKNOWN(4, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_KIND_UNKNOWN),

    /** A stretch of time for the instance figures that ends before it starts. */
    TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS(5, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS),

    /** A station's own traffic figures asked for without one end of the stretch of time. */
    STATION_TRAFFIC_SPAN_MISSING(6, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_MISSING),

    /** One end of a station's stretch of time that cannot be read as a date and a time. */
    STATION_TRAFFIC_SPAN_NOT_A_TIME(7, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_NOT_A_TIME),

    /** A kind of request to count at a station that is not one of the kinds counted. */
    STATION_TRAFFIC_KIND_UNKNOWN(8, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_KIND_UNKNOWN),

    /** A stretch of time for a station's figures that ends before it starts. */
    STATION_TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS(
            10, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS);

    private final Definition definition;

    TrafficRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.TRAFFIC, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
