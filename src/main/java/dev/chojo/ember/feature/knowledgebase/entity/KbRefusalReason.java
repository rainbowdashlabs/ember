/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

/**
 * Why one entry of a knowledge-base request was left where it was.
 *
 * <p>A closed set rather than a sentence: there is one language file, and a reason that travels as
 * free text cannot be written in it. Each value is a whole reason on its own, so a reader is told
 * what to do about it rather than that something went wrong.
 */
public enum KbRefusalReason {
    /** The reader may not do this to the entry, or may not write in the folder it would go to. */
    NO_PERMISSION,
    /** A folder of that name already sits in the target folder. */
    NAME_TAKEN,
    /** The target folder is the moved folder itself, or lies inside it. */
    TARGET_INSIDE,
    /** The entry is shared further than the target folder lets anything below it reach. */
    SHARE_TOO_WIDE,
    /** The entry is gone, or belongs to another station. */
    NOT_FOUND
}
