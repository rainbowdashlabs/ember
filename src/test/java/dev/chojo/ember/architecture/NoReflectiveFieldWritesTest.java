/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The application writes its fields through code the compiler can see, never by reflection.
 *
 * <p>A field set by name compiles whatever the name, so a renamed field breaks at run time, on the one
 * request that reaches it. Configuration changes go through the typed setters of the configuration
 * elements instead. Tests stay out of it: reading a private constant to check it is what they are for.
 */
@AnalyzeClasses(packages = "dev.chojo.ember", importOptions = ImportOption.DoNotIncludeTests.class)
public class NoReflectiveFieldWritesTest {
    @ArchTest
    static final ArchRule fieldsAreNotOpenedOrWrittenByReflection = noClasses()
            .should()
            .callMethod(AccessibleObject.class, "setAccessible", boolean.class)
            .orShould()
            .callMethod(Field.class, "setAccessible", boolean.class)
            .orShould()
            .callMethod(Field.class, "set", Object.class, Object.class);
}
