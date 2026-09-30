/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

/**
 * A picture a feed entry shows.
 *
 * @param path where the picture is served below a feed token's public routes, such as
 *             {@code lost-and-found/17/image}, so a reader fetches it without signing in
 * @param alt  what the picture shows, for readers that cannot show it
 */
public record FeedImage(String path, String alt) {}
