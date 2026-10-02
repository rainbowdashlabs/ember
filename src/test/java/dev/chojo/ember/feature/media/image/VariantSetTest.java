/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.image;

import dev.chojo.ember.feature.storage.entity.StorageCategory;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariantSetTest {
    private static final List<String> LEGACY_AVATAR = List.of(
            "a1/original.png", "a1/1024.png", "a1/512.png", "a1/256.png", "a1/128.png", "a1/64.png", "a1/.source-hash");
    private static final List<String> LIBRARY_PHOTO =
            List.of("h/orig.png", "h/orig.webp", "h/w128.webp", "h/w256.webp", "h/page1.webp", "h/page1-w128.webp");

    @Test
    void theOneParserReadsEveryStoredName() {
        assertEquals(OptionalInt.of(512), VariantLayout.SIZED.sizeOf(VariantFile.of("512.png")));
        assertEquals(OptionalInt.of(512), VariantLayout.SIZED.sizeOf(VariantFile.of("512.webp")));
        assertEquals(OptionalInt.of(256), VariantLayout.LIBRARY.sizeOf(VariantFile.of("w256.webp")));
        assertEquals(OptionalInt.of(512), VariantLayout.LIBRARY_PAGE.sizeOf(VariantFile.of("page1-w512.webp")));
        assertEquals(OptionalInt.empty(), VariantLayout.LIBRARY.sizeOf(VariantFile.of("page1-w512.webp")));
        assertEquals(OptionalInt.empty(), VariantLayout.SIZED.sizeOf(VariantFile.of("original.png")));
        assertEquals(OptionalInt.empty(), VariantLayout.SIZED.sizeOf(VariantFile.of("0.png")));
        assertEquals(OptionalInt.empty(), VariantLayout.SIZED.sizeOf(VariantFile.of("999999.png")));
        assertEquals(OptionalInt.empty(), VariantLayout.LIBRARY.sizeOf(VariantFile.of("w.webp")));
        assertEquals(OptionalInt.empty(), VariantLayout.LIBRARY.sizeOf(VariantFile.of("w-5.webp")));
        assertTrue(VariantLayout.SIZED.isOriginal(VariantFile.of("x/original.jpg")));
        assertTrue(VariantLayout.LIBRARY.isOriginal(VariantFile.of("orig.pdf")));
        assertTrue(VariantLayout.LIBRARY_PAGE.isOriginal(VariantFile.of("page1.webp")));
    }

    @Test
    void namesAreWrittenTheWayTheyAreRead() {
        assertEquals("original.png", VariantLayout.SIZED.originalName("png"));
        assertEquals("64.webp", VariantLayout.SIZED.sizeName(64, ImageFormat.WEBP));
        assertEquals("w128.webp", VariantLayout.LIBRARY.sizeName(128, ImageFormat.WEBP));
        assertEquals("page1-w128.webp", VariantLayout.LIBRARY_PAGE.sizeName(128, ImageFormat.WEBP));
        assertEquals("page1", VariantLayout.LIBRARY_PAGE.originalBase());
        var noExtension = VariantFile.of("content");
        assertEquals("", noExtension.extension());
        assertEquals(Optional.empty(), noExtension.format());
    }

    @Test
    void theSmallestSizeAtOrAboveTheRequestAnswersElseTheOriginal() {
        var set = VariantSet.of(VariantLayout.SIZED, LEGACY_AVATAR);

        assertEquals("64.png", chosen(set, 1));
        assertEquals("64.png", chosen(set, 64));
        assertEquals("128.png", chosen(set, 65));
        assertEquals("1024.png", chosen(set, 1024));
        assertEquals("original.png", chosen(set, 1025));
        assertEquals("original.png", chosen(set, 0));
        assertFalse(set.isEmpty());
        assertTrue(
                VariantSet.of(VariantLayout.SIZED, List.of("a1/.source-hash")).isEmpty());
    }

    @Test
    void aReaderThatTakesNoWebpIsHandedTheSourceInstead() {
        var set = VariantSet.of(VariantLayout.LIBRARY, LIBRARY_PHOTO);

        assertEquals(
                "w256.webp",
                set.choose(200, AcceptedFormats.EVERY_FORMAT).orElseThrow().fileName());
        assertEquals(
                "orig.png",
                set.choose(200, AcceptedFormats.WITHOUT_WEBP).orElseThrow().fileName());
        assertEquals("orig.png", set.original().orElseThrow().fileName());
        assertEquals("orig.webp", set.original(ImageFormat.WEBP).orElseThrow().fileName());
        assertEquals(Optional.empty(), set.original(ImageFormat.GIF));
        assertEquals(Optional.empty(), set.size(0, AcceptedFormats.EVERY_FORMAT));
    }

    @Test
    void aWebpOnlyOriginalIsStillTheOriginal() {
        var set = VariantSet.of(VariantLayout.SIZED, List.of("k/original.webp", "k/128.webp"));
        assertEquals(
                "original.webp",
                set.choose(0, AcceptedFormats.WITHOUT_WEBP).orElseThrow().fileName());
        assertEquals(
                "original.webp",
                set.choose(100, AcceptedFormats.WITHOUT_WEBP).orElseThrow().fileName());
    }

    @Test
    void theAcceptHeaderOnlyCountsAnExplicitWebp() {
        assertEquals(AcceptedFormats.EVERY_FORMAT, AcceptedFormats.fromAcceptHeader("image/avif,IMAGE/WEBP,*/*"));
        assertEquals(AcceptedFormats.WITHOUT_WEBP, AcceptedFormats.fromAcceptHeader("*/*"));
        assertEquals(AcceptedFormats.WITHOUT_WEBP, AcceptedFormats.fromAcceptHeader(null));
        assertTrue(AcceptedFormats.WITHOUT_WEBP.accepts(VariantFile.of("orig.pdf")));
        assertFalse(AcceptedFormats.WITHOUT_WEBP.accepts(VariantFile.of("w1.webp")));
    }

    @Test
    void layoutsMeasureAndScaleTheirOwnSide() throws IOException {
        var wide = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        var tall = new BufferedImage(200, 400, BufferedImage.TYPE_INT_RGB);

        assertEquals(400, VariantLayout.SIZED.measure(tall));
        assertEquals(200, VariantLayout.LIBRARY.measure(tall));
        var sized = VariantLayout.SIZED.scale(tall, 100);
        assertEquals(50, sized.getWidth());
        assertEquals(100, sized.getHeight());
        var library = VariantLayout.LIBRARY.scale(wide, 100);
        assertEquals(100, library.getWidth());
        assertEquals(50, library.getHeight());
    }

    @Test
    void everyCategoryOfPictureSetsHasItsFamily() {
        assertEquals(Optional.of(ImageProfile.ICON_SET), ImageProfile.of(StorageCategory.IMAGE_AVATAR));
        assertEquals(Optional.of(ImageProfile.ICON_SET), ImageProfile.of(StorageCategory.IMAGE_STATION_LOGO));
        assertEquals(Optional.of(ImageProfile.CONTENT), ImageProfile.of(StorageCategory.IMAGE_LOST_AND_FOUND));
        assertEquals(Optional.of(ImageProfile.CONTENT), ImageProfile.of(StorageCategory.IMAGE_KB_FILE_PICTURE));
        assertEquals(Optional.of(ImageProfile.LIBRARY), ImageProfile.of(StorageCategory.INSTANCE_MEDIA_FILES));
        assertEquals(Optional.empty(), ImageProfile.of(StorageCategory.MEMBER_DOCUMENTS));
        assertEquals(VariantLayout.SIZED, ImageProfile.CONTENT.layout());
        assertEquals(List.of(), ImageProfile.LIBRARY.sizes());
        assertEquals(2048, ImageProfile.ICON_SET.maxOriginalSide());
    }

    private static String chosen(VariantSet set, int requested) {
        return set.choose(requested, AcceptedFormats.EVERY_FORMAT).orElseThrow().fileName();
    }
}
