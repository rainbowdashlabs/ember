/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Where a scan of a signed paper copy stands that was handed in for a document an appointment asks for.
 */
public enum PaperState {
    /** Handed in, waiting for somebody who manages the registrations. */
    SUBMITTED,
    /** Confirmed as the signed paper copy, which settles the document for the participant. */
    CONFIRMED,
    /** Turned down with a reason; the participant may hand in another one. */
    REJECTED
}
