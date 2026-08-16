/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataInitializerTest {

    @Test
    void bundledSectionsAreOfferedInOrderWithoutTheirPrefix() {
        var sections = DataInitializer.documentTemplates("tos", "de");
        assertFalse(sections.isEmpty(), "Ember ships German terms of service");
        assertEquals("grundlagen", sections.getFirst().displayName());
        assertTrue(sections.getFirst().content().startsWith("# Nutzungsbedingungen"));
    }

    @Test
    void everyLocaleOfADocumentIsOffered() {
        assertFalse(DataInitializer.documentTemplates("privacy", "en").isEmpty());
        assertFalse(DataInitializer.documentTemplates("consent", "de").isEmpty());
        assertFalse(DataInitializer.documentTemplates("imprint", "en").isEmpty());
    }

    @Test
    void generatedSectionsCarryNoTemplateToLoad() {
        var sections = DataInitializer.documentTemplates("privacy", "de");
        assertTrue(
                sections.stream().noneMatch(section -> section.displayName().equals("browser-storage")),
                "the generated section owns no content, so it must not be offered as a template");
    }

    @Test
    void anUnknownDocumentOrLocaleOffersNothing() {
        assertTrue(DataInitializer.documentTemplates("privacy", "xx").isEmpty());
        assertTrue(DataInitializer.documentTemplates("nonsense", "de").isEmpty());
    }
}
