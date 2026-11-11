/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * The name of the key-value pair a log event carries when it says itself which problem it is.
 *
 * <p>The problem log counts warnings by logger and message, which turns one fault reported with a
 * hundred different ids into a hundred problems. A caller that knows better, such as the request
 * handling that knows the route a request matched, adds this pair with a stable value, and the problem
 * log counts by that value instead. The message still says what happened in each case.
 */
public final class ProblemKey {
    /** The key of the pair. */
    public static final String NAME = "problem";

    /**
     * Marks a warning as a refusal: something a reader asked for and was told no. The problem log keeps
     * those apart from faults, never counts them as waiting and never forwards them.
     */
    public static final Marker REFUSAL = MarkerFactory.getMarker("REFUSAL");

    private ProblemKey() {}
}
