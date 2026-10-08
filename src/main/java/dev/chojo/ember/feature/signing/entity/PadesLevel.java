/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * The PAdES baseline level a signature in a checked document was found to have, which may be any
 * signature, not only one Ember made.
 */
public enum PadesLevel {
    /** A signature without a timestamp. */
    BASELINE_B,
    /** A signature with a timestamp proving when it existed. */
    BASELINE_T,
    /** A timestamped signature with the material to check it offline. */
    BASELINE_LT,
    /** A long-term signature further protected by an archive timestamp. */
    BASELINE_LTA,
    /** A signature that meets none of the baseline levels, such as a plain PKCS#7 signature. */
    NOT_BASELINE
}
