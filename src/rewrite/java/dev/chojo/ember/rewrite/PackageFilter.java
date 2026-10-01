/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.rewrite;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * The packages a recipe run reaches, so the same recipe can be run one feature at a time.
 *
 * <p>The filter is a comma separated list of package names. A package is reached when it is one of
 * them or lies below one of them, so {@code dev.chojo.ember.feature.inventory} reaches
 * {@code dev.chojo.ember.feature.inventory.service} but not {@code dev.chojo.ember.feature.inventorycheck}.
 * No filter reaches every package.
 */
final class PackageFilter {

    private final List<String> prefixes;

    private PackageFilter(List<String> prefixes) {
        this.prefixes = prefixes;
    }

    /**
     * Reads a filter as a recipe option gives it.
     *
     * @param packages comma separated package names, or {@code null} or blank for every package
     * @return the filter
     */
    static PackageFilter of(@Nullable String packages) {
        if (packages == null || packages.isBlank()) {
            return new PackageFilter(List.of());
        }
        return new PackageFilter(Arrays.stream(packages.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList());
    }

    /**
     * Whether a package is reached.
     *
     * @param packageName the dotted package name
     * @return true when no filter is set or the package is one of the named ones or below one
     */
    boolean includesPackage(String packageName) {
        if (prefixes.isEmpty()) {
            return true;
        }
        return prefixes.stream().anyMatch(prefix -> packageName.equals(prefix) || packageName.startsWith(prefix + "."));
    }

    /**
     * Whether the package a type is declared in is reached.
     *
     * @param typeName the fully qualified type name, with {@code $} or {@code .} before a nested type
     * @return true when the type's package is reached
     */
    boolean includesType(String typeName) {
        String topLevel = typeName.contains("$") ? typeName.substring(0, typeName.indexOf('$')) : typeName;
        int lastDot = topLevel.lastIndexOf('.');
        return includesPackage(lastDot < 0 ? "" : topLevel.substring(0, lastDot));
    }
}
