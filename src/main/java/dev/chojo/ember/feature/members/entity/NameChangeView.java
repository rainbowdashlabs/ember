/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.api.MemberIdentity;

import java.time.Instant;

/**
 * An open name request as a member manager sees it, with the member it is about.
 *
 * @param id            the request
 * @param member        the member at the reader's station whose account asked
 * @param currentName   the register name the account carries now
 * @param requestedName the register name asked for
 * @param requestedAt   when it was asked for
 */
public record NameChangeView(
        int id, MemberIdentity member, String currentName, String requestedName, Instant requestedAt) {}
