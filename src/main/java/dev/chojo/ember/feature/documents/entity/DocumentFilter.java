/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What a reader narrowed the station's store to.
 *
 * @param memberIds     only documents bound to one of these members, or empty for all of them
 * @param search        words to look for in the title and in what the documents say, or null
 * @param includeHidden whether the ones kept from their own members are listed too
 * @param unboundOnly   only the documents that name nobody, which are the station's own paperwork
 * @param departedOnly  only the documents about people who have all left: archived members, or members
 *                      deleted while the document was kept for the record
 */
public record DocumentFilter(
        List<Integer> memberIds,
        @Nullable String search,
        boolean includeHidden,
        boolean unboundOnly,
        boolean departedOnly) {}
