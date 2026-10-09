/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

/**
 * How the signature picture a signing act left in its field came to the act: made for it on the spot, or
 * the one the signer's account keeps.
 */
public enum ActPictureSource {
    /** Drawn for the act with a finger, a pen or the mouse. */
    DRAWN,
    /** The name typed for the act and set in a handwriting style. */
    TYPED,
    /** A photo or scan of a signature on paper, uploaded for the act and cleaned to a transparent picture. */
    UPLOADED,
    /** The picture the signer's account keeps, saved before the act. */
    SAVED;

    /**
     * @param source how a picture made for the act was made, or null where the signer did not say
     * @return the source of a picture made for the act, drawn where the signer did not say
     */
    public static ActPictureSource madeAs(@Nullable SignatureImageSource source) {
        if (source == null) return DRAWN;
        return switch (source) {
            case DRAWN -> DRAWN;
            case TYPED -> TYPED;
            case UPLOADED -> UPLOADED;
        };
    }
}
