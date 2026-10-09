/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.feature.generator.entity.PaperSubmission;

/**
 * Told where the scan of a signed paper copy stands, so the signatures still open on the participant's copy
 * follow it: while the scan waits for a manager they are asked of nobody, and once it counts as confirmed
 * they are settled as signed on paper.
 */
public interface ScanConfirmations {

    /**
     * @param submission the scan that now waits for a manager
     */
    void waiting(PaperSubmission submission);

    /**
     * @param session    the manager of the registrations who confirmed it, or handed it in confirmed
     * @param submission the confirmed scan
     */
    void confirmed(StationSession session, PaperSubmission submission);
}
