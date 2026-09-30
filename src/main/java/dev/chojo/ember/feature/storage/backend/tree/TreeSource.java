/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

/**
 * Where a {@link FileTreeBackend} gets the tree for one call from.
 *
 * @param <T> the kind of tree
 */
@FunctionalInterface
public interface TreeSource<T extends FileTree> {

    /**
     * One tree lent to every caller at once and never closed, for a tree that holds no connection.
     *
     * @param tree the tree every caller gets
     * @return the source
     */
    static <T extends FileTree> TreeSource<T> shared(T tree) {
        return () -> () -> tree;
    }

    /**
     * Lends a tree.
     *
     * @return the lease, to be closed when the call is done
     * @throws dev.chojo.ember.feature.storage.backend.StorageUnavailableException when no tree can be had in time
     */
    Lease<T> acquire();

    /** Lends a tree for the health check, which always really tries to connect. */
    default Lease<T> acquireForProbe() {
        return acquire();
    }

    /**
     * Releases every tree and runs {@code afterwards} once no lent one is out any more, so whatever
     * the trees share is released after them and not underneath them.
     *
     * @param afterwards what to release last
     */
    default void close(Runnable afterwards) {
        afterwards.run();
    }
}
