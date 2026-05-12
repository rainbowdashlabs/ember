/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

public record ErrorResponseWrapper(String error, String message) {
    public ErrorResponseWrapper(String error) {
        this(error, null);
    }
}
