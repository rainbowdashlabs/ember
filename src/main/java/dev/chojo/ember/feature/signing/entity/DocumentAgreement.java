/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * An agreement signed on a member document, as one who signed it or acts for its member sees it.
 *
 * @param requestUid   the request for signatures on the document
 * @param state        where the request stands
 * @param withdrawable whether the reader may withdraw it now
 * @param withdrawnAt  when it was withdrawn, or null where nobody withdrew it
 * @param withdrawnBy  the official name of whoever withdrew it, or null
 */
public record DocumentAgreement(
        UUID requestUid,
        RequestState state,
        boolean withdrawable,
        @Nullable Instant withdrawnAt,
        @Nullable String withdrawnBy) {}
