/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

/**
 * One tree lent to every caller at once, for a tree that holds no connection and is safe to share:
 * the local disk. Nothing is ever closed.
 *
 * @param <T> the kind of tree
 */
public final class SharedTree<T extends FileTree> implements TreeSource<T> {
    private final Lease<T> lease;

    /**
     * Lends one tree to every caller.
     *
     * @param tree the tree every caller gets
     */
    public SharedTree(T tree) {
        this.lease = new Lease<>() {
            @Override
            public T tree() {
                return tree;
            }

            @Override
            public void discard() {}

            @Override
            public void close() {}
        };
    }

    @Override
    public Lease<T> acquire() {
        return lease;
    }

    @Override
    public void close() {}
}
