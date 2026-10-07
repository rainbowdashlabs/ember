/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/** What the web version of an uploaded font style is, which decides how the template editor is sent it. */
public enum WebFontFormat {
    /** A compressed web font, as most font publishers offer for web pages. */
    WOFF2("font/woff2"),
    /** The older web font format. */
    WOFF("font/woff"),
    /** A TrueType font marked as the web file. */
    TRUETYPE("font/ttf"),
    /** An OpenType font with PostScript outlines marked as the web file. */
    CFF("font/otf");

    private final String mediaType;

    WebFontFormat(String mediaType) {
        this.mediaType = mediaType;
    }

    /** @return the media type a file of this format is stored and served as */
    public String mediaType() {
        return mediaType;
    }

    /**
     * @param outline the outlines of a TrueType or OpenType file
     * @return the format such a file is as a web version
     */
    public static WebFontFormat of(FontOutline outline) {
        return outline == FontOutline.CFF ? CFF : TRUETYPE;
    }
}
