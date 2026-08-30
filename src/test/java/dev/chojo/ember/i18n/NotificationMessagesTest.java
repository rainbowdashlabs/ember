/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.i18n;

import dev.chojo.ember.feature.notifications.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every kind of notification has something to say, in every language.
 *
 * <p>A type without a message is not silent: the reader is handed its parameters joined with
 * dashes, which is how a reminder about a closing registration reached a feed reading
 * "Berufsfeuerwehrtag - 3 - Millie Jo Harnack". Two types had slipped through that way, so the
 * check is written down rather than left to whoever adds the next one.
 */
class NotificationMessagesTest {

    private static final List<String> LOCALES = List.of("de", "en");

    /** A count in the parameters routes to a plural variant, so either spelling counts as present. */
    private static boolean covered(Map<String, String> messages, String key) {
        return messages.containsKey(key)
                || (messages.containsKey(key + ".one") && messages.containsKey(key + ".other"));
    }

    @Test
    void everyNotificationTypeHasAMessageInEveryLanguage() {
        var localizer = new Localizer();
        var missing = new ArrayList<String>();
        for (String locale : LOCALES) {
            var messages = localizer.get("notifications", locale, "message");
            assertTrue(messages.size() > 20, "the " + locale + " messages were not loaded at all");
            for (NotificationType type : NotificationType.values()) {
                if (!covered(messages, type.localeKey())) {
                    missing.add(locale + ": " + type.name() + " (" + type.localeKey() + ")");
                }
            }
        }

        assertEquals(List.of(), missing, "notification types with nothing to say");
    }

    /**
     * A plural type needs both halves. One of them alone reads correctly until the day the count is
     * the other number.
     */
    @Test
    void aPluralMessageIsWrittenForBothCounts() {
        var localizer = new Localizer();
        var halves = new ArrayList<String>();
        for (String locale : LOCALES) {
            var messages = localizer.get("notifications", locale, "message");
            for (String key : messages.keySet()) {
                if (key.endsWith(".one") && !messages.containsKey(key.replace(".one", ".other"))) {
                    halves.add(locale + ": " + key + " without its other");
                }
                if (key.endsWith(".other") && !messages.containsKey(key.replace(".other", ".one"))) {
                    halves.add(locale + ": " + key + " without its one");
                }
            }
        }

        assertEquals(List.of(), halves, "plural messages missing a half");
    }

    /** What one language says, the other says too, or a reader in it falls back to raw parameters. */
    @Test
    void bothLanguagesCarryTheSameMessages() {
        var localizer = new Localizer();
        var german = localizer.get("notifications", "de", "message").keySet();
        var english = localizer.get("notifications", "en", "message").keySet();

        assertEquals(
                List.of(),
                german.stream().filter(key -> !english.contains(key)).sorted().toList(),
                "written in German and not in English");
        assertEquals(
                List.of(),
                english.stream().filter(key -> !german.contains(key)).sorted().toList(),
                "written in English and not in German");
    }
}
