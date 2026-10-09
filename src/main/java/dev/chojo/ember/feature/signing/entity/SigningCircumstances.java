/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

/**
 * Where a confirmation came from, as the request that carried it arrived. The evidence keeps the address
 * only truncated.
 *
 * @param clientIp  the client's address in its textual form, or null when it is not known
 * @param userAgent the browser's user agent, or null when it sent none
 */
public record SigningCircumstances(
        @Nullable String clientIp, @Nullable String userAgent) {}
