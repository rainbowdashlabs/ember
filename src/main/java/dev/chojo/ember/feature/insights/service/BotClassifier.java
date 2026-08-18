/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.service;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Classifies a request's User-Agent string as a bot or a human visitor. Pure denylist
 * matching: a curated list of well-known crawlers plus a generic regex for the obvious
 * keywords. Bots are still counted but recorded under {@code is_bot = TRUE} so the
 * dashboard's headline number can exclude them.
 */
@Singleton
public class BotClassifier {

    private static final Pattern GENERIC = Pattern.compile(
            "(?i)(bot|crawler|spider|preview|fetch|http-client|curl|wget|libwww|python-requests|java/|okhttp|go-http-client|headless)");

    private static final List<String> KNOWN = List.of(
            "Googlebot",
            "Bingbot",
            "DuckDuckBot",
            "AhrefsBot",
            "SemrushBot",
            "LinkedInBot",
            "WhatsApp",
            "Twitterbot",
            "Slackbot",
            "Discordbot",
            "Applebot",
            "YandexBot",
            "Baiduspider",
            "facebookexternalhit");

    /**
     * Returns {@code true} when the User-Agent matches any known crawler or the generic
     * keyword denylist. A {@code null} or blank User-Agent counts as a bot - real browsers
     * always send one, scripts often don't.
     */
    public boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return true;
        for (String name : KNOWN) {
            if (userAgent.contains(name)) return true;
        }
        return GENERIC.matcher(userAgent).find();
    }
}
