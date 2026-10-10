/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.i18n;

import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListAnswer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

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
     * The headline of a feed entry comes from {@code feedTitle}, and falls back to {@code category}
     * when no template is written. With neither, the reader is handed the raw name of the type, which
     * is how a closing registration reached an RSS reader titled "REGISTRATION_CLOSING".
     */
    @Test
    void everyNotificationTypeIsTitledAndFiledInEveryLanguage() {
        var localizer = new Localizer();
        var missing = new ArrayList<String>();
        for (String locale : LOCALES) {
            var titles = localizer.get("notifications", locale, "feedTitle");
            var categories = localizer.get("notifications", locale, "category");
            assertTrue(titles.size() > 20, "the " + locale + " feed titles were not loaded at all");
            assertTrue(categories.size() > 20, "the " + locale + " categories were not loaded at all");
            for (NotificationType type : NotificationType.values()) {
                if (!covered(titles, type.name())) {
                    missing.add(locale + ": " + type.name() + " has no feed title");
                }
                if (!categories.containsKey(type.name())) {
                    missing.add(locale + ": " + type.name() + " has no category");
                }
            }
        }

        assertEquals(List.of(), missing, "notification types a feed reader sees by their raw name");
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
        for (String section : List.of("message", "feedTitle", "category")) {
            var german = localizer.get("notifications", "de", section).keySet();
            var english = localizer.get("notifications", "en", section).keySet();

            assertEquals(
                    List.of(),
                    german.stream()
                            .filter(key -> !english.contains(key))
                            .sorted()
                            .toList(),
                    section + " written in German and not in English");
            assertEquals(
                    List.of(),
                    english.stream()
                            .filter(key -> !german.contains(key))
                            .sorted()
                            .toList(),
                    section + " written in English and not in German");
        }
    }

    /**
     * A notification whose parameters pick a wording of their own, such as an expiry reminder for a
     * date already passed, finds that wording in every language. Without it the reader gets the
     * type's plain sentence, which says the wrong thing.
     */
    @Test
    void everyVariantIsWordedInEveryLanguage() {
        var localizer = new Localizer();
        var missing = new ArrayList<String>();
        for (String locale : LOCALES) {
            var messages = localizer.get("notifications", locale, "message");
            for (NotificationType type : NotificationType.values()) {
                for (String variant : variantsOf(type.paramsType())) {
                    if (!covered(messages, type.localeKey() + "." + variant)) {
                        missing.add(locale + ": " + type.name() + " " + variant);
                    }
                }
            }
        }

        assertEquals(List.of(), missing, "variants without a wording");
    }

    /**
     * A placeholder names a parameter the notification carries. A misspelt one is printed as it
     * stands, braces and all, in the middle of the sentence.
     */
    @Test
    void everyPlaceholderNamesAParameter() {
        var localizer = new Localizer();
        var unknown = new ArrayList<String>();
        for (String locale : LOCALES) {
            for (NotificationType type : NotificationType.values()) {
                var known = new HashSet<>(componentNames(type.paramsType()));
                known.add("statusLabel");
                var messages = localizer.get("notifications", locale, "message");
                var titles = localizer.get("notifications", locale, "feedTitle");
                textsOf(messages, type.localeKey()).forEach((key, text) -> placeholders(text).stream()
                        .filter(name -> !known.contains(name))
                        .forEach(name -> unknown.add(locale + ": " + key + " {" + name + "}")));
                textsOf(titles, type.name()).forEach((key, text) -> placeholders(text).stream()
                        .filter(name -> !known.contains(name))
                        .forEach(name -> unknown.add(locale + ": feedTitle " + key + " {" + name + "}")));
            }
        }

        assertEquals(List.of(), unknown, "placeholders no parameter fills");
    }

    /** A sentence names the same things in every language, so no reader loses a detail. */
    @Test
    void bothLanguagesFillTheSamePlaceholders() {
        var localizer = new Localizer();
        var differing = new ArrayList<String>();
        for (String section : List.of("message", "feedTitle", "digest")) {
            var german = localizer.get("notifications", "de", section);
            var english = localizer.get("notifications", "en", section);
            german.forEach((key, text) -> {
                String other = english.get(key);
                if (other != null && !placeholders(text).equals(placeholders(other))) {
                    differing.add(section + "." + key);
                }
            });
        }

        assertEquals(List.of(), differing, "texts that fill different placeholders per language");
    }

    /**
     * A status or an answer is shown by its label. Without one the reader sees the raw name, such as
     * {@code NOT_INTERESTED}.
     */
    @Test
    void everyStatusAndAnswerHasALabelInEveryLanguage() {
        var localizer = new Localizer();
        var missing = new ArrayList<String>();
        for (String locale : LOCALES) {
            var statuses = localizer.get("notifications", locale, "ical");
            var answers = localizer.get("notifications", locale, "waitlistAnswer");
            Stream.concat(Arrays.stream(RegistrationStatus.values()), Arrays.stream(LendingStatus.values()))
                    .map(status -> "status." + status.name())
                    .filter(key -> !statuses.containsKey(key))
                    .forEach(key -> missing.add(locale + ": " + key));
            Arrays.stream(WaitingListAnswer.values())
                    .map(Enum::name)
                    .filter(key -> !answers.containsKey(key))
                    .forEach(key -> missing.add(locale + ": waitlistAnswer " + key));
        }

        assertEquals(List.of(), missing, "statuses and answers without a label");
    }

    private static Map<String, String> textsOf(Map<String, String> section, String base) {
        var texts = new TreeMap<String, String>();
        section.forEach((key, text) -> {
            if (key.equals(base) || key.startsWith(base + ".")) texts.put(key, text);
        });
        return texts;
    }

    private static Set<String> placeholders(String text) {
        var names = new HashSet<String>();
        var matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }

    private static List<String> componentNames(Class<? extends NotificationParams> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    /**
     * Every wording the parameters can pick, found by building them once filled in, once with each
     * field left empty in turn and once with each value of every choice they carry.
     */
    private static Set<String> variantsOf(Class<? extends NotificationParams> type) {
        var components = type.getRecordComponents();
        var variants = new TreeSet<String>();
        variantOf(type, filled(components)).ifPresent(variants::add);
        for (int i = 0; i < components.length; i++) {
            Class<?> field = components[i].getType();
            var values = new ArrayList<@Nullable Object>();
            if (!field.isPrimitive()) values.add(null);
            if (field.isEnum()) values.addAll(Arrays.asList(field.getEnumConstants()));
            for (var value : values) {
                Object[] arguments = filled(components);
                arguments[i] = value;
                variantOf(type, arguments).ifPresent(variants::add);
            }
        }
        return variants;
    }

    private static Optional<String> variantOf(Class<? extends NotificationParams> type, Object[] arguments) {
        try {
            Class<?>[] types = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getType)
                    .toArray(Class<?>[]::new);
            return Optional.ofNullable(
                    type.getDeclaredConstructor(types).newInstance(arguments).variant());
        } catch (ReflectiveOperationException refused) {
            return Optional.empty();
        }
    }

    private static Object[] filled(RecordComponent[] components) {
        return Arrays.stream(components)
                .map(component -> sample(component.getType()))
                .toArray();
    }

    private static @Nullable Object sample(Class<?> type) {
        if (type == String.class) return "Probe";
        if (type == int.class || type == Integer.class) return 2;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == LocalDate.class) return LocalDate.of(2026, 10, 17);
        if (type.isEnum()) return type.getEnumConstants()[0];
        return null;
    }
}
