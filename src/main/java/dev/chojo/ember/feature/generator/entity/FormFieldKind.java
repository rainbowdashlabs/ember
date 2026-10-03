/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * What a form field an uploaded PDF brings is.
 */
public enum FormFieldKind {
    /** A text field, which takes a text. */
    TEXT,
    /** A check box, which is ticked or not. */
    CHECK,
    /** A signature field, which is flattened like every other form field. */
    SIGNATURE,
    /** Anything else (radio buttons, lists, push buttons), which keeps what it shows and cannot be filled. */
    OTHER;

    /** @return whether a template can fill a field of this kind */
    public boolean fillable() {
        return this == TEXT || this == CHECK;
    }
}
