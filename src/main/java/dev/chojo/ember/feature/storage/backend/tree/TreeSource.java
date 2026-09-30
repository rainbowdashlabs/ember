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
public interface TreeSource<T extends FileTree> extends AutoCloseable {

    /**
     * Lends a tree.
     *
     * @return the lease, to be closed when the call is done
     * @throws dev.chojo.ember.feature.storage.backend.StorageUnavailableException when no tree can be
     *                                                                              had in time
     */
    Lease<T> acquire();

    /**
     * Lends a tree for the health check, which always really tries to connect.
     *
     * @return the lease
     */
    default Lease<T> acquireForProbe() {
        return acquire();
    }

    /** Releases every tree the source holds. */
    @Override
    void close();
}
