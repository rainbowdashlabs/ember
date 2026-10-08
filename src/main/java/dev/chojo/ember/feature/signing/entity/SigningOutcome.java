/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.twofactor.entity.StepUpProof;

/**
 * Where a field stands after a signing act filled it.
 *
 * @param field        the field as it now stands, with the document it is on
 * @param requestState where its request now stands, complete once no field waits any more
 * @param proof        the proof the act was confirmed with
 * @param bound        whether that proof is bound to the document
 */
public record SigningOutcome(PendingSignature field, RequestState requestState, StepUpProof proof, boolean bound) {}
