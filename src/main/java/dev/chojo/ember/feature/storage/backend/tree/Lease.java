/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

/**
 * A {@link FileTree} lent to one caller until it is handed back.
 *
 * @param <T> the kind of tree
 */
public interface Lease<T extends FileTree> extends AutoCloseable {

    /** The tree, for this caller alone until the lease is closed. */
    T tree();

    /**
     * Marks the tree as broken, so closing the lease closes the tree rather than handing it to the
     * next caller.
     */
    void discard();

    /** Hands the tree back, or closes it when it was discarded or can no longer be used. */
    @Override
    void close();
}
