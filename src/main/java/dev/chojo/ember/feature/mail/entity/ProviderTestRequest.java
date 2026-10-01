/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import org.jspecify.annotations.Nullable;

/**
 * Where a test mail should go. Empty means only the connection is tried and nothing is sent.
 *
 * @param recipient the address to send to, which need not be the one asking: whether a relay
 *                  delivers is often a question about somebody else's mailbox
 */
public record ProviderTestRequest(@Nullable String recipient) {}
