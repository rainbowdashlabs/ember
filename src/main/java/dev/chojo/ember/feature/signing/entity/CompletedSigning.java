/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * What a completed signing act gives the rest of Ember: the legal level the provider reached and the
 * evidence of the act. The document is sealed by the station once the act is recorded, as one state of
 * its request that carries every act so far.
 *
 * @param level    the legal level of the signature
 * @param evidence the record of the act and its proof
 */
public record CompletedSigning(SignatureLevel level, SigningEvidence evidence) {}
