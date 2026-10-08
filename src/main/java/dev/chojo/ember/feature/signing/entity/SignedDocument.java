/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * What a completed signing act gives the rest of Ember: the sealed document, the legal level the provider
 * reached, and the evidence of the act.
 *
 * @param sealed   the sealed document and how far its seal got
 * @param level    the legal level of the signature
 * @param evidence the record of the act and its proof
 */
public record SignedDocument(SealedDocument sealed, SignatureLevel level, SigningEvidence evidence) {}
