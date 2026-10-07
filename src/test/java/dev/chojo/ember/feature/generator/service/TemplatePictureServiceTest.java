/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** The picture of a template: its first page without a member, drawn once per version. */
class TemplatePictureServiceTest extends GeneratorTestBase {
    private static Wiring wiring;
    private static DocumentGeneratorService generator;
    private static TemplatePictureService pictures;
    private static int author;

    @BeforeAll
    static void setup() {
        wiring = wire("Template Picture Station");
        author = wiring.member("picture-author@test.com", "Pia", "Bild").id();
        var backend = localStorage();
        generator = spy(wiring.generator());
        pictures = new TemplatePictureService(
                wiring.templates(),
                generator,
                new ImageVariants(new StorageService(new StorageBackendResolver(backend), backend)),
                newOwnerStores());
    }

    private static int letterTemplate(String name, String text) {
        return wiring.templates()
                .create(wiring.owner(), letter(name, text).build(), author)
                .id();
    }

    @Test
    void aTemplateHasAPictureDrawnOnceForItsVersion() {
        int id = letterTemplate("Bild einmal", "Hallo {{member.firstName}}");
        clearInvocations(generator);

        var first = pictures.forStation(wiring.station().id(), id, 256).orElseThrow();
        var second = pictures.forStation(wiring.station().id(), id, 256).orElseThrow();

        assertTrue(first.contentType().startsWith("image/"));
        assertArrayEquals(first.data(), second.data());
        verify(generator, times(1)).drawWithoutMember(any());
    }

    @Test
    void aNewVersionGetsANewPicture() {
        int id = letterTemplate("Bild neu", "Erste Fassung");
        var before = pictures.forStation(wiring.station().id(), id, 256).orElseThrow();

        wiring.templates()
                .update(
                        wiring.owner(),
                        id,
                        letter("Bild neu", "Zweite, ganz andere und viel längere Fassung")
                                .build(),
                        author);
        var after = pictures.forStation(wiring.station().id(), id, 256).orElseThrow();

        assertFalse(java.util.Arrays.equals(before.data(), after.data()));
    }

    @Test
    void aTemplateOfAnotherStationHasNoPictureHere() {
        int id = letterTemplate("Bild fremd", "Text");
        var other = stationRepo.create("Template Picture Other Station");

        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE, () -> pictures.forStation(other.id(), id, 256));
        stationRepo.delete(other.id());
    }
}
