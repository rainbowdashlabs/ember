/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(MailTemplateRenderer.class);
    private static final String TEMPLATE_ROOT = "templates/mail";
    private static final String FALLBACK_LOCALE = "en";

    private final PebbleEngine engine;

    public MailTemplateRenderer() {
        var loader = new FileLoader();
        loader.setPrefix(TEMPLATE_ROOT);
        loader.setSuffix("");
        this.engine = new PebbleEngine.Builder()
                .loader(loader)
                .autoEscaping(true)
                .strictVariables(false)
                .cacheActive(true)
                .build();
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
