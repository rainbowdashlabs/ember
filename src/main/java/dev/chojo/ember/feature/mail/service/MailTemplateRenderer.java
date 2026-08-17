/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.i18n.Localizer;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders mail templates with Pebble. Templates extend {@code _layout.html} (shared chrome
 * + CSS); per-mail files only carry the body content. HTML-bearing variables that must not
 * be escaped (pre-rendered fragments like {@code logoHtml}/{@code items}) are passed to the
 * template via the {@code raw} filter, e.g. {@code {{ items | raw }}}.
 *
 * <p>Locale resolution falls back to {@code en} when a localized template is missing.
 */
@Singleton
public class MailTemplateRenderer {
    public static final String TEMPLATE_ROOT = "templates/mail";
    private static final String FALLBACK_LOCALE = "en";
    private static final Localizer LOCALIZER = new Localizer();

    private final PebbleEngine engine;

    public MailTemplateRenderer() {
        var loader = new FileLoader(Path.of(TEMPLATE_ROOT).toAbsolutePath().toString());
        loader.setSuffix("");
        this.engine = new PebbleEngine.Builder()
                .loader(loader)
                .autoEscaping(true)
                .strictVariables(false)
                .cacheActive(true)
                .build();
    }

    /**
     * Resolves the localized subject line for {@code key} from {@code i18n/mail_<locale>.json}'s
     * {@code subject} section, substituting {@code placeholders}. Falls back to the {@code en}
     * bundle when the requested locale lacks the key.
     */
    public String subject(String key, String locale, Map<String, String> placeholders) {
        String template = lookupSubject(locale, key);
        if (template == null) template = lookupSubject(FALLBACK_LOCALE, key);
        if (template == null) {
            throw new IllegalStateException("Mail subject not found: " + key);
        }
        String result = template;
        if (placeholders != null) {
            for (var entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return result.replaceAll("\\{[^}]+}", "");
    }

    /**
     * Resolves a body string from {@code i18n/mail_<locale>.json}'s {@code body} section,
     * falling back to {@code en}. Returns {@code null} when neither locale carries the key.
     */
    public String body(String key, String locale) {
        String value = LOCALIZER.get("mail", locale, "body").get(key);
        if (value == null)
            value = LOCALIZER.get("mail", FALLBACK_LOCALE, "body").get(key);
        return value;
    }

    private String lookupSubject(String locale, String key) {
        if (locale == null || locale.isBlank()) return null;
        return LOCALIZER.get("mail", locale, "subject").get(key);
    }

    /**
     * Renders {@code name} for {@code locale}, substituting {@code variables}. Adds a
     * {@code lang} variable derived from the resolved locale.
     *
     * @throws IllegalStateException if neither the requested locale nor the {@code en} fallback exists.
     */
    public String render(String name, String locale, Map<String, String> variables) {
        String effectiveLocale = resolveLocale(name, locale);
        PebbleTemplate template = engine.getTemplate(effectiveLocale + "/" + name);
        var context = new HashMap<String, Object>(variables);
        context.putIfAbsent("lang", effectiveLocale);

        try (var writer = new StringWriter()) {
            template.evaluate(writer, context);
            return writer.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render template " + name, e);
        }
    }

    private String resolveLocale(String name, String locale) {
        if (locale != null && !locale.isBlank() && templateExists(locale, name)) {
            return locale;
        }
        if (!templateExists(FALLBACK_LOCALE, name)) {
            throw new IllegalStateException(
                    "Template not found: " + TEMPLATE_ROOT + "/" + FALLBACK_LOCALE + "/" + name);
        }
        return FALLBACK_LOCALE;
    }

    private boolean templateExists(String locale, String name) {
        return Path.of(TEMPLATE_ROOT, locale, name).toFile().isFile();
    }
}
