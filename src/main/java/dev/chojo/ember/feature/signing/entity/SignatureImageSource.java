/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * How a person's signature picture was made.
 */
public enum SignatureImageSource {
    /** Drawn on the screen with a finger, a pen or the mouse. */
    DRAWN,
    /** The name typed and set in a handwriting style, for whoever cannot draw on the screen. */
    TYPED,
    /** A photo or scan of a signature on paper, cleaned to a transparent picture. */
    UPLOADED
}
