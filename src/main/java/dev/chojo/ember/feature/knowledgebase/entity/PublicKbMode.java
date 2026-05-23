/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

/**
 * Controls how the public knowledgebase is exposed.
 * <ul>
 *     <li>{@code OFF} — no public access (default)</li>
 *     <li>{@code ALLOW_ALL} — all folders/files are public unless explicitly opted out</li>
 *     <li>{@code DENY_ALL} — nothing is public unless explicitly opted in</li>
 * </ul>
 * Files/folders with access restrictions are never publicly visible regardless of mode.
 */
public enum PublicKbMode {
    OFF,
    ALLOW_ALL,
    DENY_ALL
}
