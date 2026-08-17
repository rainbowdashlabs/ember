/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

/**
 * Sub-file selector under a producer-chosen storage key. For image categories
 * the variant carries widths ({@code original}, {@code 1024}, …); for KB files the gzip
 * decision ({@code content} vs {@code content.gz}); for everything else the implicit
 * {@link #ORIGINAL}.
 *
 * <p>A {@code null} or {@link #ORIGINAL} variant collapses to the bare key on the backend
 * (no extra path segment), so a flat producer never pays for the abstraction.
 */
public record Variant(String name) {

    /**
     * Sentinel returned by the absence of a variant - store/read without a trailing segment.
     */
    public static final Variant ORIGINAL = new Variant("original");

    /**
     * Convenience factory that returns {@link #ORIGINAL} for null / "original" / empty inputs.
     */
    public static Variant of(String name) {
        if (name == null || name.isEmpty() || name.equals(ORIGINAL.name)) return ORIGINAL;
        return new Variant(name);
    }

    /**
     * Whether this variant is the implicit original (no path segment appended).
     */
    public boolean isOriginal() {
        return ORIGINAL.name.equals(name);
    }
}
