/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import io.javalin.openapi.OpenApiName;

/**
 * How far a form meant for people outside the station reaches.
 *
 * <p>This says nothing about whether the form is taking answers, which its status and its dates
 * already decide. It says who can get to it at all.
 *
 * <p>An internal form has neither reach: it is answered from inside the station and carries this
 * only because every form has a column.
 */
@OpenApiName("FormVisibility")
public enum FormVisibility {
    /**
     * Anybody, at the form's own stable address. This is the form put on a public page: it is
     * published by being there, and its address is as public as the page around it.
     */
    PUBLIC,
    /**
     * Only whoever was sent the link. The stable address answers nothing, so replacing the link
     * really does end every way in that was given out, which is the whole reason for having one.
     */
    UNLISTED;

    /** Whether the form answers at its own address, rather than at its link alone. */
    public boolean openlyAddressed() {
        return this == PUBLIC;
    }
}
