/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler that takes an id from the address and deliberately belongs to no station.
 *
 * <p>Most of them do belong to one: a row named by its id is a row of some station, and answering
 * for it without asking whose it is hands one station another station's data. The architecture test
 * requires every such handler to say which station it means, and this is how the exceptions say
 * they are exceptions: an account of the instance, an address the instance administrator owns, a
 * partner resolved from a signature rather than a session, or a row that belongs to a member
 * wherever they are.
 *
 * <p>The reason is not documentation for its own sake. It is what a reviewer reads to decide
 * whether the exception still holds, so write what makes the endpoint station-free rather than that
 * it is.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface StationFree {

    /** Why this endpoint belongs to no station. */
    String value();
}
