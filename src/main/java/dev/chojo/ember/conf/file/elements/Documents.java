/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

/**
 * Configuration for the documents Ember generates from templates.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("DOCUMENTS")
public class Documents {

    /**
     * The directory the default font of generated documents is read from, once, when the application
     * starts.
     *
     * <p>The container images fill it with Berlin Type on their first start and keep it in the data
     * directory, so later starts find it there. Ember takes the family whose regular style it finds there
     * and prints every text that names no font in it. An empty or missing directory leaves Liberation Sans
     * as the default, which is also the fallback for every character and style the default font lacks.
     * The WOFF2 web files beside the font files are what the template editor shows the default font in.
     */
    @Overwrite(env = @Env)
    private String defaultFontDir = "data/default-font";

    public String defaultFontDir() {
        return defaultFontDir;
    }

    @Override
    public String toString() {
        return "Documents{defaultFontDir=" + defaultFontDir + '}';
    }
}
