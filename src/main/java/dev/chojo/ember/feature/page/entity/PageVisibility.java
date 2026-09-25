/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import io.javalin.openapi.OpenApiName;

/**
 * Who reaches a station page.
 *
 * <p>This replaces a published flag, which could say only yes or no and so could not say the case
 * this exists for: a page meant for the people who were sent its link and for nobody else.
 *
 * <p>The two questions below are the whole of the interface on purpose. A reader asks the one it
 * means rather than comparing constants, because the two are not the same question and the places
 * that get them mixed up are exactly the places that leak a page or lose one: the menu and the
 * sitemap want {@link #listed()}, while the editor's picker and a wiki delete's warning want
 * {@link #reachable()}, since a page nobody can find is still a page somebody outside can open.
 */
@OpenApiName("PageVisibility")
public enum PageVisibility {
    /** Nobody outside the station. */
    DRAFT,
    /**
     * Anybody holding the page's link. Such a page stands outside the page tree: it has no parent,
     * it has no children, and its slug path answers nothing.
     */
    UNLISTED,
    /** Anybody, at the path the page's slugs spell. */
    PUBLIC;

    /** Whether the page belongs in the station's menu, its page list and its sitemap. */
    public boolean listed() {
        return this == PUBLIC;
    }

    /** Whether somebody outside the station can open the page at all, by whatever address. */
    public boolean reachable() {
        return this == UNLISTED || this == PUBLIC;
    }
}
