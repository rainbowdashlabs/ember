/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

/**
 * Simple JSON response wrapper containing a single message string.
 *
 * @param message the response message
 */
public record MessageResponse(String message) {}
