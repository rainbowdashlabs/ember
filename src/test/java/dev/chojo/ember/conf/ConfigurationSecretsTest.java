/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No configuration element writes a secret into its own text.
 *
 * <p>The whole configuration is logged once at start, and that log is kept in the database and read
 * back from the administration pages. A credential printed there is readable by everybody who can
 * reach either of those, so it has to be reported as present rather than written out.
 *
 * <p>Written against the fields rather than against a list of known offenders: a settings class gains
 * a secret every few releases, and a test naming today's would pass the day after the next one lands.
 */
class ConfigurationSecretsTest {
    private static final String ELEMENTS_PACKAGE = "dev.chojo.ember.conf.file.elements";

    /** The canary planted in every secret-looking field, distinctive enough to find in any text. */
    private static final String CANARY = "zzcanary-must-not-be-printed-zz";

    private static final List<String> SECRET_NAME_PARTS =
            List.of("password", "secret", "apikey", "token", "credential", "passphrase", "privatekey");

    @Test
    @DisplayName("no configuration element prints a secret in its text")
    void noConfigurationElementPrintsASecret() {
        var leaking = new ArrayList<String>();
        for (Class<?> element : configurationElements()) {
            plantAndRead(element).ifPresent(leaking::add);
        }
        assertTrue(
                leaking.isEmpty(),
                "These configuration elements write a secret into toString(), where the start-up log and "
                        + "the administration pages can read it: " + leaking);
    }

    /**
     * Fills every secret-looking field of one element with the canary and reads its text back.
     *
     * @param element the settings class to try
     * @return the offending class and field, or empty when nothing of its own leaked
     */
    private Optional<String> plantAndRead(Class<?> element) {
        var secrets = secretFieldsOf(element);
        if (secrets.isEmpty()) return Optional.empty();

        Object instance;
        try {
            Constructor<?> constructor = element.getDeclaredConstructor();
            constructor.setAccessible(true);
            instance = constructor.newInstance();
            for (Field secret : secrets) {
                secret.setAccessible(true);
                secret.set(instance, CANARY);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }

        String printed = String.valueOf(instance);
        if (!printed.contains(CANARY)) return Optional.empty();
        return Optional.of(element.getSimpleName() + " "
                + secrets.stream().map(Field::getName).toList());
    }

    /** The declared string fields whose name says they hold a credential. */
    private List<Field> secretFieldsOf(Class<?> element) {
        var found = new ArrayList<Field>();
        for (Field field : element.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() != String.class) continue;
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (SECRET_NAME_PARTS.stream().anyMatch(name::contains)) found.add(field);
        }
        return found;
    }

    private List<Class<?>> configurationElements() {
        var elements = new ArrayList<Class<?>>();
        for (JavaClass imported : new ClassFileImporter().importPackages(ELEMENTS_PACKAGE)) {
            if (!imported.getPackageName().startsWith(ELEMENTS_PACKAGE)) continue;
            Class<?> reflected = imported.reflect();
            if (reflected.isInterface() || reflected.isEnum()) continue;
            if (Modifier.isAbstract(reflected.getModifiers())) continue;
            elements.add(reflected);
        }
        return elements;
    }
}
