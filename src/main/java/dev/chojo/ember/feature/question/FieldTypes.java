/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which field types each feature offers.
 *
 * <p>Every set is what the feature accepts today, under the shared names. A type outside a feature's
 * set is refused where a field of that feature is written. A feature's type picker offers part of
 * its set at most: where a screen offers less, a field already saved under another type keeps it.
 */
public final class FieldTypes {

    /** A station's member profile. */
    public static final Set<FieldType> PROFILE = of(
            FieldType.TEXT,
            FieldType.NUMBER,
            FieldType.DATE,
            FieldType.BOOLEAN,
            FieldType.CHOICE,
            FieldType.GENDER,
            FieldType.AGE,
            FieldType.BIRTH_DATE,
            FieldType.EXPIRY_DATE,
            FieldType.SECTION,
            FieldType.SPACER);

    /**
     * An association's questions to the members at its stations. The date of birth is left out: each
     * station declares its own, and a second one would collide with it. A gender is offered: it is one
     * per station, the association's or the station's own, whichever comes first.
     */
    public static final Set<FieldType> ASSOCIATION = of(
            FieldType.TEXT,
            FieldType.NUMBER,
            FieldType.DATE,
            FieldType.BOOLEAN,
            FieldType.CHOICE,
            FieldType.GENDER,
            FieldType.AGE,
            FieldType.EXPIRY_DATE,
            FieldType.SECTION,
            FieldType.SPACER);

    /** The fields of an inventory, a kind of gear or a single piece. */
    public static final Set<FieldType> INVENTORY =
            of(FieldType.TEXT, FieldType.NUMBER, FieldType.DATE, FieldType.BOOLEAN, FieldType.CHOICE);

    /** The fields of a board's tickets. */
    public static final Set<FieldType> BOARD = of(
            FieldType.TEXT,
            FieldType.NUMBER,
            FieldType.DATE,
            FieldType.BOOLEAN,
            FieldType.CHOICE,
            FieldType.LANE_ASSIGNEE);

    /** The fields an organiser fills in on an appointment. */
    public static final Set<FieldType> APPOINTMENT = of(
            FieldType.TEXT,
            FieldType.LONG_TEXT,
            FieldType.NUMBER,
            FieldType.DATE,
            FieldType.TIME,
            FieldType.BOOLEAN,
            FieldType.CHOICE,
            FieldType.URL,
            FieldType.LOCATION,
            FieldType.MEMBER,
            FieldType.MEMBER_LIST,
            FieldType.MEMBER_OF_GROUP,
            FieldType.MEMBER_LIST_OF_GROUP,
            FieldType.MEMBER_OF_TYPE,
            FieldType.MEMBER_LIST_OF_TYPE,
            FieldType.MEMBER_OF_TAG,
            FieldType.MEMBER_LIST_OF_TAG);

    /** The questions each registrant of an appointment answers, as the editor offers them. */
    public static final Set<FieldType> REGISTRATION = of(
            FieldType.TEXT,
            FieldType.LONG_TEXT,
            FieldType.NUMBER,
            FieldType.DATE,
            FieldType.TIME,
            FieldType.BOOLEAN,
            FieldType.CHOICE,
            FieldType.MEMBER);

    /** The fields of an attendance sheet. */
    public static final Set<FieldType> ATTENDANCE = of(
            FieldType.TEXT,
            FieldType.LONG_TEXT,
            FieldType.NUMBER,
            FieldType.DATE,
            FieldType.TIME,
            FieldType.BOOLEAN,
            FieldType.CHOICE,
            FieldType.URL,
            FieldType.MEMBER,
            FieldType.MEMBER_LIST,
            FieldType.MEMBER_OF_GROUP,
            FieldType.MEMBER_LIST_OF_GROUP);

    /** The questions a waiting list asks of whoever joins it. */
    public static final Set<FieldType> WAITING_LIST = of(
            FieldType.TEXT,
            FieldType.NUMBER,
            FieldType.DATE,
            FieldType.BOOLEAN,
            FieldType.CHOICE,
            FieldType.BIRTH_DATE);

    private FieldTypes() {}

    private static Set<FieldType> of(FieldType first, FieldType... rest) {
        return Collections.unmodifiableSet(EnumSet.of(first, rest));
    }
}
