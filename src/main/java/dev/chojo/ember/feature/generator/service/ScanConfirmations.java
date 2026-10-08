/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.feature.generator.entity.PaperSubmission;

/**
 * Told once the scan of a signed paper copy counts as confirmed, so the signatures still open on the
 * participant's copy can be settled as signed on paper.
 */
@FunctionalInterface
public interface ScanConfirmations {

    /** Settles nothing. */
    ScanConfirmations NONE = (session, submission) -> {};

    /**
     * @param session    the manager of the registrations who confirmed it, or handed it in confirmed
     * @param submission the confirmed scan
     */
    void confirmed(StationSession session, PaperSubmission submission);
}
