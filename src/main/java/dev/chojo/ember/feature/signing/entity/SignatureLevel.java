/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * The legal level of an electronic signature in the sense of the eIDAS regulation, which is what a
 * signature provider can promise and what a kind of document can ask for.
 *
 * <p>Not to be confused with the PAdES baseline of a seal ({@link SealLevel}): that says how much of
 * the material to check a seal later is embedded, this says what the law makes of the person's act.
 */
public enum SignatureLevel {
    /**
     * A simple electronic signature: the act is recorded with its evidence and weighed freely by a
     * court. Ember's own provider never promises more than this.
     */
    SIMPLE,
    /**
     * An advanced electronic signature: uniquely linked to and capable of identifying the signer, which
     * needs identity proofing Ember does not do. Reserved for a provider outside Ember.
     */
    ADVANCED,
    /**
     * A qualified electronic signature: an advanced one made with a qualified device and certificate,
     * equal to a handwritten signature. Only a qualified trust service provider gives it.
     */
    QUALIFIED
}
