/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * A signature field somebody is asked to sign, and how they would sign it: for themselves, as guardian on
 * behalf of a member in their care, or letting that member sign through their account.
 *
 * @param pending the field and its document
 * @param signer  who would sign it, in which capacity and through whose account
 */
public record OpenSignature(PendingSignature pending, Signer signer) {}
