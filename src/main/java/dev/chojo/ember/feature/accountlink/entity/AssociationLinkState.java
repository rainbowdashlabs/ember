/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.entity;

import dev.chojo.ember.api.auth.ClusterUserType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Where an association's request to an account stands, as the association sees it. It learns nothing
 * about the account beyond the address it typed itself.
 *
 * @param uid           the request, which sending again names
 * @param address       the address the association typed
 * @param role          the role it offered
 * @param status        how the request stands
 * @param sentAt        when it was last sent
 * @param expiresAt     until when the person may answer
 * @param answeredAt    when it was answered or ran out, or null while it waits
 * @param sendAgainFrom from when it may be sent again, or null where it may not be sent again at all
 */
public record AssociationLinkState(
        UUID uid,
        String address,
        ClusterUserType role,
        LinkStatus status,
        Instant sentAt,
        Instant expiresAt,
        @Nullable Instant answeredAt,
        @Nullable Instant sendAgainFrom) {}
