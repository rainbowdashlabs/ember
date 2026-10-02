/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import org.jspecify.annotations.Nullable;

/**
 * The outcome of trying a mail provider.
 *
 * @param success whether the provider took the connection or the message
 * @param error   what it said when it did not, or {@code null}
 */
public record MailTestResponse(boolean success, @Nullable String error) {}
