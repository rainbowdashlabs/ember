/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.List;
import java.util.UUID;

/**
 * That a signing act was one of several confirmed by one proof, and what is needed to recompute the
 * challenge that proof covered.
 *
 * <p>Each act of a batch keeps the whole list: its own item and every other one, each by its request, its
 * field and its digest. The other items are named by their digest only, never by what they bound, so the
 * evidence of one document says nothing about the content, the statements or the values of another; its
 * own item is recomputed from the act itself, which is what ties the act into the challenge.
 *
 * @param uid      the batch
 * @param position where this act stands in the batch, counted from 0
 * @param items    every item of the batch in the order it was signed, this act's included
 */
public record BatchMembership(UUID uid, int position, List<Item> items) {
    /** The length of an item digest, in bytes. */
    public static final int DIGEST_BYTES = 32;

    /** Copies the items, and refuses a batch of fewer than two or a position outside it. */
    public BatchMembership {
        items = List.copyOf(items);
        if (items.size() < 2) throw new IllegalArgumentException("A batch of one is a single act");
        if (position < 0 || position >= items.size()) {
            throw new IllegalArgumentException("Position " + position + " is outside a batch of " + items.size());
        }
    }

    /**
     * One field of a batch.
     *
     * <p>The array is handed over as it is, without a copy.
     *
     * @param requestUid the request the field belongs to
     * @param fieldName  the field's name in its document
     * @param digest     the item's digest as the batch challenge took it, thirty-two bytes
     */
    public record Item(UUID requestUid, String fieldName, byte[] digest) {}
}
