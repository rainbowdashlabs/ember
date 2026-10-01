/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#INSIGHTS}: insights.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum InsightsRefusal implements Refusal {
    /** Figures about a page addressed by something that is not a number. */
    INSIGHTS_PAGE_NOT_A_NUMBER(2, HttpStatus.BAD_REQUEST, "That does not name a page"),

    /** Figures asked for without the span of time they are to cover. */
    INSIGHTS_WINDOW_MISSING(3, HttpStatus.BAD_REQUEST, "Give the first and the last moment the figures are to cover"),

    /** A span the figures were asked for over whose ends do not read as moments. */
    INSIGHTS_WINDOW_NOT_A_MOMENT(
            4, HttpStatus.BAD_REQUEST, "The span the figures were asked for is not made of moments"),

    /** More pages asked for at once than the list hands out. */
    INSIGHTS_LIMIT_OUT_OF_RANGE(5, HttpStatus.BAD_REQUEST, "Ask for between 1 and 500 pages"),

    /** How many pages to show, given as something that is not a number. */
    INSIGHTS_LIMIT_NOT_A_NUMBER(6, HttpStatus.BAD_REQUEST, "How many pages to show has to be a number"),

    /** A span for the list of pages whose last moment falls before its first. */
    INSIGHTS_WINDOW_ENDS_BEFORE_IT_STARTS(7, HttpStatus.BAD_REQUEST, Sentences.INSIGHTS_WINDOW_BACKWARDS),

    /**
     * A page that is not here, or one of another station.
     *
     * <p>One code for both on purpose. These figures are a station's own, and telling the two apart
     * would say that the page exists at a station the reader may not see.
     */
    INSIGHTS_PAGE_NOT_HERE(8, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A span for one page's figures whose last moment falls before its first. */
    INSIGHTS_PAGE_WINDOW_ENDS_BEFORE_IT_STARTS(9, HttpStatus.BAD_REQUEST, Sentences.INSIGHTS_WINDOW_BACKWARDS);

    private final Definition definition;

    InsightsRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.INSIGHTS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
