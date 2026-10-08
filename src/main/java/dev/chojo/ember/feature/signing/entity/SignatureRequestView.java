/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.List;

/**
 * A request for signatures with its fields and the evidence of every act on it.
 *
 * @param request  the request
 * @param fields   its fields, in the order they were asked for
 * @param evidence the evidence of every signing act on its fields
 */
public record SignatureRequestView(
        SignatureRequest request, List<RequestedSignature> fields, List<StoredEvidence> evidence) {}
