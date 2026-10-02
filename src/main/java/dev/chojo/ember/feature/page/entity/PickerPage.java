/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight picker result row for the page picker. Exposes only the public UUID - never the
 * internal integer id.
 *
 * @param pageUid   the public id a page link names the page by
 * @param title     what the page is called
 * @param slug      the last part of its address
 * @param updatedAt when it was last changed
 */
public record PickerPage(UUID pageUid, String title, String slug, Instant updatedAt) {}
