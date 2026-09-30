/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.image;

import dev.chojo.ember.feature.media.MediaLayoutFixtures;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageFormatTest {

    @Test
    void theBytesDecideTheFormat() {
        String avatar = "account/%s/images/avatars/%s/original.png"
                .formatted(MediaLayoutFixtures.ACCOUNT_UID, MediaLayoutFixtures.ACCOUNT_UID);
        String photo = "station/%s/images/lost-and-found/7/original.jpg".formatted(MediaLayoutFixtures.STATION_UID);

        assertEquals(Optional.of(ImageFormat.PNG), ImageFormat.sniff(MediaLayoutFixtures.stored(avatar)));
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.sniff(MediaLayoutFixtures.stored(photo)));
        assertEquals(Optional.of(ImageFormat.GIF), ImageFormat.sniff(MediaLayoutFixtures.picture("animated.gif")));
        assertEquals(Optional.of(ImageFormat.WEBP), ImageFormat.sniff(MediaLayoutFixtures.picture("picture.webp")));
    }

    @Test
    void anythingElseIsNoPicture() {
        assertEquals(Optional.empty(), ImageFormat.sniff(null));
        assertEquals(Optional.empty(), ImageFormat.sniff(new byte[] {1, 2}));
        assertEquals(Optional.empty(), ImageFormat.sniff("<svg></svg>".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.empty(), ImageFormat.sniff("RIFF0000WAVE".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Optional.empty(), ImageFormat.sniff("GIF8xa".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void namesAndTypesMapBothWays() {
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.ofExtension("JPEG"));
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.ofExtension("jpg"));
        assertEquals(Optional.of(ImageFormat.WEBP), ImageFormat.ofMimeType("Image/WebP; q=0.8"));
        assertEquals(Optional.empty(), ImageFormat.ofExtension(null));
        assertEquals(Optional.empty(), ImageFormat.ofExtension("pdf"));
        assertEquals(Optional.empty(), ImageFormat.ofMimeType(null));
        assertEquals(Optional.empty(), ImageFormat.ofMimeType("image/svg+xml"));
        assertEquals("png", ImageFormat.PNG.extension());
        assertEquals("image/gif", ImageFormat.GIF.mimeType());
    }

    @Test
    void theTableCoversTheLibraryKindsBeyondPictures() {
        assertEquals("jpg", MediaTypes.extensionFor("image/jpeg"));
        assertEquals("svg", MediaTypes.extensionFor("image/svg+xml"));
        assertEquals("pdf", MediaTypes.extensionFor("application/pdf"));
        assertEquals("bin", MediaTypes.extensionFor("application/zip"));
        assertEquals("bin", MediaTypes.extensionFor(null));
        assertEquals("image/svg+xml", MediaTypes.mimeTypeFor("svg"));
        assertEquals("application/pdf", MediaTypes.mimeTypeFor("PDF"));
        assertEquals(MediaTypes.UNTYPED, MediaTypes.mimeTypeFor("bin"));
        assertEquals(MediaTypes.UNTYPED, MediaTypes.mimeTypeFor(null));
    }

    @Test
    void aStoredTypeIsBelievedUnlessItSaysNothingOrTheFileIsWebp() {
        assertEquals("image/webp", MediaTypes.contentTypeOf(VariantFile.of("w128.webp"), "image/png"));
        assertEquals("image/png", MediaTypes.contentTypeOf(VariantFile.of("orig.png"), "image/png"));
        assertEquals("image/gif", MediaTypes.contentTypeOf(VariantFile.of("orig.gif"), MediaTypes.UNTYPED));
        assertEquals("application/pdf", MediaTypes.contentTypeOf(VariantFile.of("orig.pdf"), " "));
        assertEquals("image/jpeg", MediaTypes.contentTypeOf(VariantFile.of("orig.jpeg"), null));
    }
}
