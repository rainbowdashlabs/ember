/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

/**
 * Who reads the content a block sits in, which decides what a block naming a news entry or an
 * appointment may name and show.
 *
 * <p>A block shows the same thing to every reader of its content, so it may only name what every one
 * of them may see. That is the whole rule: a block naming something part of its readers may not see
 * would show it to them, or show them a gap they cannot explain.
 */
public enum BlockAudience {
    /**
     * A public page, read by anybody without signing in. Only what is public to everyone: a news
     * entry on the station's public blog, an appointment on its public calendar.
     */
    PUBLIC,
    /**
     * A news entry or a wiki article, read by the station's signed-in members. Everything every one of
     * them may see: published and kept to nobody in particular, internal ones included.
     */
    MEMBERS;

    /**
     * The audience a request names, the public where it names none or something else: the narrower
     * reading is the one that cannot show anybody too much.
     *
     * @param raw the name as the request gives it, or null
     * @return the audience
     */
    public static BlockAudience named(String raw) {
        return MEMBERS.name().equalsIgnoreCase(raw) ? MEMBERS : PUBLIC;
    }
}
