/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.rewrite;

import org.openrewrite.SourceFile;
import org.openrewrite.java.marker.JavaSourceSet;

/**
 * Which sources the null recipes change: the application's own, not its tests and not the recipes.
 *
 * <p>SpotBugs checks the main classes only, and the rules they are held to have no reason to reach
 * the code that tests them.
 */
final class MainSources {

    private MainSources() {}

    /**
     * Whether a source belongs to the main source set.
     *
     * @param source the parsed source
     * @return true for a main source, and for one the parser assigned to no source set
     */
    static boolean contains(SourceFile source) {
        return source.getMarkers()
                .findFirst(JavaSourceSet.class)
                .map(set -> "main".equals(set.getName()))
                .orElse(true);
    }
}
