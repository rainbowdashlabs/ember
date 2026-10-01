/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chojo.ember.conf.file.File;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every setting of the configuration file, read from the configuration classes themselves: the key it has in
 * {@code config.yaml}, the value a fresh file starts with, and the environment variable that overrides it.
 *
 * <p>The classes under {@code conf.file} are the configuration's structure, so a field whose type lives there is a
 * section and is walked into; every other field is a setting. A deprecated field or section is left out, since it
 * is only still read so an old file keeps working. The variable name follows the rule Ocular's override
 * processor applies: the class's {@link OverwritePrefix} or its simple name, upper-cased, then the field name
 * upper-cased, unless an {@link Env} names the variable itself. A variable is only listed when Ocular can convert
 * text to the field's type; for an enum or a nested object it reads the variable and drops it.
 */
public final class SettingsCatalog {

    private static final String SECTION_PACKAGE = "dev.chojo.ember.conf.file";
    private static final Set<Class<?>> CONVERTIBLE = Set.of(
            String.class,
            int.class,
            Integer.class,
            long.class,
            Long.class,
            boolean.class,
            Boolean.class,
            double.class,
            Double.class,
            float.class,
            Float.class,
            short.class,
            Short.class,
            byte.class,
            Byte.class,
            List.class,
            ArrayList.class,
            Set.class,
            HashSet.class);
    private static final String UNSET = "-";

    private SettingsCatalog() {}

    /**
     * One setting of the configuration file.
     *
     * @param configKey the dotted key in {@code config.yaml}
     * @param env the environment variable that overrides it, or null when only the file sets it
     * @param defaultValue the value a fresh configuration file carries, {@code -} when it is empty
     */
    public record Setting(
            String configKey,
            @Nullable String env,
            @JsonProperty("default") String defaultValue) {}

    /**
     * Every setting of a fresh configuration file, in the order the classes declare them.
     *
     * @return the settings
     */
    public static List<Setting> settings() {
        return settingsOf(new File());
    }

    /**
     * Every setting below a configuration object, keys starting at its root.
     *
     * @param root the object to walk
     * @return the settings
     */
    static List<Setting> settingsOf(Object root) {
        List<Setting> settings = new ArrayList<>();
        collect(root, "", settings);
        return settings;
    }

    /**
     * The settings as the committed JSON file carries them.
     *
     * @return the JSON text, ending in a newline
     */
    public static String render() {
        var mapper =
                JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        return mapper.writeValueAsString(settings()) + "\n";
    }

    private static void collect(Object section, String path, List<Setting> settings) {
        for (Field field : section.getClass().getDeclaredFields()) {
            if (isSkipped(field)) continue;
            Object value = read(field, section);
            String key = path + field.getName();
            if (value != null && isSection(field.getType())) {
                collect(value, key + ".", settings);
            } else {
                settings.add(new Setting(key, envName(field), render(value)));
            }
        }
    }

    private static boolean isSkipped(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers)
                || Modifier.isTransient(modifiers)
                || field.isSynthetic()
                || field.isAnnotationPresent(Deprecated.class)
                || field.getType().isAnnotationPresent(Deprecated.class);
    }

    private static boolean isSection(Class<?> type) {
        return !type.isEnum() && !type.isRecord() && type.getName().startsWith(SECTION_PACKAGE);
    }

    private static @Nullable Object read(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read " + field, e);
        }
    }

    /**
     * The variable Ocular reads for a field, by the rule its override processor follows.
     *
     * @param field the field
     * @return the variable name, or null when the field has none or Ocular cannot convert text to its type
     */
    static @Nullable String envName(Field field) {
        Overwrite overwrite = field.getAnnotation(Overwrite.class);
        if (overwrite == null || overwrite.env().length == 0 || !CONVERTIBLE.contains(field.getType())) return null;
        Class<?> owner = field.getDeclaringClass();
        OverwritePrefix prefixAnnotation = owner.getAnnotation(OverwritePrefix.class);
        String prefix = prefixAnnotation == null ? owner.getSimpleName() : prefixAnnotation.value();
        String envPrefix = prefix.replace(".", "_").toUpperCase(Locale.ROOT);
        String named = overwrite.env()[0].value();
        if (named.isEmpty()) return envPrefix + "_" + field.getName().toUpperCase(Locale.ROOT);
        if (prefixAnnotation != null && prefixAnnotation.force()) return envPrefix + "_" + named;
        return named;
    }

    private static String render(@Nullable Object value) {
        return switch (value) {
            case null -> UNSET;
            case String text when text.isEmpty() -> UNSET;
            case Collection<?> items when items.isEmpty() -> "[]";
            case Collection<?> items -> items.stream().map(String::valueOf).collect(Collectors.joining(","));
            case Map<?, ?> entries when entries.isEmpty() -> "{}";
            default -> String.valueOf(value);
        };
    }
}
