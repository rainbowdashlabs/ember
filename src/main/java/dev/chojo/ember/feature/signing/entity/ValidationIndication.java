/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * The main result of validating a seal or a timestamp, with the names ETSI EN 319 102-1 gives them,
 * which is what the EU DSS validator and every report built on it print.
 */
public enum ValidationIndication {
    /** A signature passed every check, its chain included. */
    TOTAL_PASSED,
    /** A signature failed for a reason no later data can change, such as altered content. */
    TOTAL_FAILED,
    /** The checks could not decide; the sub-indication says what was missing. */
    INDETERMINATE,
    /** A timestamp or a building block passed its checks. */
    PASSED,
    /** A timestamp or a building block failed its checks. */
    FAILED,
    /** Nothing that could be checked was found. */
    NO_SIGNATURE_FOUND
}
