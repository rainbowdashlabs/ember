/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

/**
 * One page of a form as the editor saves it.
 *
 * @param key         the page's key, which a stored page keeps and a new one is given by the editor
 * @param title       optional title
 * @param description optional description
 * @param after       where the reader goes once the page is done
 */
public record PageEntry(String key, String title, String description, PageTarget after) {}
