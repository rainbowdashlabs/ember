/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.media.MediaLayoutFixtures;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.image.AcceptedFormats;
import dev.chojo.ember.feature.media.image.ImageEncoder;
import dev.chojo.ember.feature.media.image.ImageFormat;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.util.OversizedPictures;
import dev.chojo.ember.util.WebpEncoder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageVariantsTest {
    private static final int STATION_ID = 1;
    private static final StorageScope.Account AVATAR_SCOPE = new StorageScope.Account(MediaLayoutFixtures.ACCOUNT_UID);
    private static final String AVATAR_KEY = MediaLayoutFixtures.ACCOUNT_UID.toString();
    private static final StorageScope.Station STATION_SCOPE =
            new StorageScope.Station(STATION_ID, MediaLayoutFixtures.STATION_UID);
    private static final String AVATAR_DIR =
            "account/%s/images/avatars/%s/".formatted(MediaLayoutFixtures.ACCOUNT_UID, MediaLayoutFixtures.ACCOUNT_UID);
    private static final String LOST_ITEM_DIR =
            "station/%s/images/lost-and-found/7/".formatted(MediaLayoutFixtures.STATION_UID);
    private static final String LIBRARY_DIR = "station/%s/media/files/".formatted(MediaLayoutFixtures.STATION_UID);

    @TempDir
    Path root;

    private StorageService storage;
    private Storage config;
    private MediaStorageService library;
    private ImageVariants images;

    @BeforeEach
    void setUp() {
        var backend = new LocalStorageBackend(root);
        storage = new StorageService(new StorageBackendResolver(backend), backend);
        config = Mockito.mock(Storage.class);
        Mockito.when(config.imageVariantsEnabled()).thenReturn(true);
        Mockito.when(config.imageVariantsWebp()).thenReturn(true);
        Mockito.when(config.imageVariantsWidthList()).thenReturn(List.of(128, 256, 512));
        var stations = Mockito.mock(StationRepository.class);
        Mockito.when(stations.resolveUid(STATION_ID)).thenReturn(MediaLayoutFixtures.STATION_UID);
        library = new MediaStorageService(storage, stations, backend);
        images = new ImageVariants(storage, config, new ImageEncoder());
    }

    @Nested
    class SizedFamilies {

        @Test
        void aPngUploadWritesItsOriginalAndEverySize() throws IOException {
            storeAvatar(png(300, 200));

            assertEquals(
                    List.of("1024.png", "128.png", "256.png", "512.png", "64.png", "original.png"),
                    storedNames(AVATAR_DIR));
        }

        @Test
        void theStoredSizedLayoutIsReadByItsNames() {
            MediaLayoutFixtures.copyInto(root);

            assertServed(AVATAR_DIR + "64.png", avatar(64));
            assertServed(AVATAR_DIR + "128.png", avatar(100));
            assertServed(AVATAR_DIR + "original.png", avatar(0));
            assertServed(AVATAR_DIR + "original.png", avatar(2000));
            assertTrue(images.exists(ImageProfile.ICON_SET, AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY));

            var jpeg = images.read(
                            ImageProfile.CONTENT,
                            STATION_SCOPE,
                            StorageCategory.IMAGE_LOST_AND_FOUND,
                            MediaLayoutFixtures.LOST_ITEM_KEY,
                            200)
                    .orElseThrow();
            assertArrayEquals(MediaLayoutFixtures.stored(LOST_ITEM_DIR + "256.jpg"), jpeg.data());
            assertEquals("image/jpeg", jpeg.contentType());
        }

        @Test
        void aWebpUploadReplacesThePreviousPicture() throws IOException {
            storeAvatar(png(300, 200));
            byte[] webp = MediaLayoutFixtures.picture("picture.webp");

            storeAvatar(webp);

            var original = avatar(0).orElseThrow();
            assertArrayEquals(webp, original.data());
            assertEquals("image/webp", original.contentType());
            assertFalse(storedNames(AVATAR_DIR).contains("original.png"));
            assertEquals(WebpEncoder.isAvailable(), storedNames(AVATAR_DIR).contains("128.webp"));
        }

        @Test
        void anAnimatedGifIsKeptAsItCame() throws IOException {
            byte[] gif = MediaLayoutFixtures.picture("animated.gif");

            storeAvatar(gif);

            assertArrayEquals(gif, avatar(0).orElseThrow().data());
            assertArrayEquals(gif, avatar(128).orElseThrow().data());
            assertEquals(List.of("original.gif"), storedNames(AVATAR_DIR));
        }

        @Test
        void anUploadThatCannotBeEncodedKeepsThePreviousPicture() throws IOException {
            var failing = Mockito.spy(new ImageEncoder());
            Mockito.doThrow(new IOException("no encoder"))
                    .when(failing)
                    .encode(Mockito.any(), Mockito.eq(ImageFormat.PNG));
            images = new ImageVariants(storage, config, failing);
            byte[] gif = MediaLayoutFixtures.picture("animated.gif");
            storeAvatar(gif);

            assertThrows(IOException.class, () -> storeAvatar(png(300, 200)));

            assertArrayEquals(gif, avatar(0).orElseThrow().data());
        }

        @Test
        void anUploadThatIsNoTakenPictureIsRefusedBeforeAnythingIsWritten() throws IOException {
            storeAvatar(png(40, 40));

            assertRefused(Refusal.PICTURE_KIND_NOT_TAKEN, "<svg/>".getBytes(StandardCharsets.UTF_8), 0);
            assertRefused(Refusal.PICTURE_KIND_NOT_TAKEN, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}, 0);
            assertRefused(Refusal.PICTURE_TOO_LARGE, png(40, 40), 10);
            assertRefused(Refusal.PICTURE_TOO_MANY_PIXELS, OversizedPictures.pngClaiming(30_000, 30_000), 0);
            assertThrows(
                    RefusalResponse.class,
                    () -> images.store(
                            ImageProfile.CONTENT,
                            STATION_SCOPE,
                            StorageCategory.DEMO_AVATAR,
                            "x",
                            MediaLayoutFixtures.picture("animated.gif"),
                            0));

            assertTrue(avatar(0).isPresent());
        }

        @Test
        void theLibraryIsNeverStoredAsASizedSet() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> images.store(
                            ImageProfile.LIBRARY, STATION_SCOPE, StorageCategory.MEDIA_FILES, "h", png(4, 4), 0));
        }

        @Test
        void readsNothingForAMissingKeyOrAScopeTheCategoryDoesNotTake() {
            assertTrue(avatar(64).isEmpty());
            assertTrue(images.read(ImageProfile.ICON_SET, AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, "", 64)
                    .isEmpty());
            assertTrue(images.read(ImageProfile.ICON_SET, AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, null, 64)
                    .isEmpty());
            assertFalse(images.exists(ImageProfile.ICON_SET, AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY));
            assertFalse(images.exists(ImageProfile.ICON_SET, AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, " "));
            assertTrue(images.read(ImageProfile.ICON_SET, STATION_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY, 64)
                    .isEmpty());
            assertFalse(images.exists(ImageProfile.ICON_SET, STATION_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY));
        }

        @Test
        void deleteRemovesTheWholeSet() throws IOException {
            storeAvatar(png(40, 40));

            images.delete(AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY);
            images.delete(AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, "");

            assertTrue(avatar(0).isEmpty());
        }

        private void assertRefused(Refusal refusal, byte[] data, int maxBytes) {
            var refused = assertThrows(
                    RefusalResponse.class,
                    () -> images.store(
                            ImageProfile.ICON_SET,
                            AVATAR_SCOPE,
                            StorageCategory.IMAGE_AVATAR,
                            AVATAR_KEY,
                            data,
                            maxBytes));
            assertEquals(refusal, refused.refusal());
        }
    }

    @Nested
    class Library {

        @Test
        void addsWebpSizesBelowTheSourceWidthOnly() throws IOException {
            Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available");
            String hash = upload(png(200, 100), "image/png");

            assertEquals(List.of("orig.png", "w128.webp"), storedNames(LIBRARY_DIR + hash));
        }

        @Test
        void aJpegOrWebpSourceGetsWebpSizesToo() throws IOException {
            Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available");
            String jpeg = upload(jpeg(300, 200), "image/jpeg");
            String webp = upload(MediaLayoutFixtures.picture("picture.webp"), "image/webp");

            assertEquals(List.of("orig.jpg", "w128.webp", "w256.webp"), storedNames(LIBRARY_DIR + jpeg));
            assertEquals(List.of("orig.webp", "w128.webp", "w256.webp"), storedNames(LIBRARY_DIR + webp));
        }

        @Test
        void addsNothingWhenSwitchedOffOrForKindsWithoutSizes() throws IOException {
            Mockito.when(config.imageVariantsEnabled()).thenReturn(false);
            String off = upload(png(800, 600), "image/png");
            Mockito.when(config.imageVariantsEnabled()).thenReturn(true);
            Mockito.when(config.imageVariantsWebp()).thenReturn(false);
            String noWebp = upload(png(801, 600), "image/png");
            Mockito.when(config.imageVariantsWebp()).thenReturn(true);
            String gif = upload(MediaLayoutFixtures.picture("animated.gif"), "image/gif");
            String svg = upload("<svg/>".getBytes(StandardCharsets.UTF_8), "image/svg+xml");
            String zip = upload("PK".getBytes(StandardCharsets.UTF_8), "application/zip");
            String bomb = upload(OversizedPictures.pngClaiming(30_000, 30_000), "image/png");
            String junk = upload(new byte[] {(byte) 0xFF, (byte) 0xD8, 0, 0}, "image/png");
            String broken = upload("%PDF-broken".getBytes(StandardCharsets.UTF_8), "application/pdf");

            for (String hash : List.of(off, noWebp, gif, svg, zip, bomb, junk, broken)) {
                assertEquals(1, storedNames(LIBRARY_DIR + hash).size(), hash);
            }
        }

        @Test
        void theStoredWidthLayoutIsReadByItsNames() {
            MediaLayoutFixtures.copyInto(root);
            String photo = MediaLayoutFixtures.LIBRARY_PHOTO;
            String sheet = MediaLayoutFixtures.LIBRARY_SHEET;

            assertServed(LIBRARY_DIR + photo + "/w256.webp", read(photo, 200, AcceptedFormats.EVERY_FORMAT));
            assertServed(LIBRARY_DIR + photo + "/orig.png", read(photo, 200, AcceptedFormats.WITHOUT_WEBP));
            assertServed(LIBRARY_DIR + photo + "/orig.png", read(photo, 0, AcceptedFormats.EVERY_FORMAT));
            assertServed(LIBRARY_DIR + photo + "/orig.png", read(photo, 4000, AcceptedFormats.EVERY_FORMAT));
            assertServed(LIBRARY_DIR + photo + "/w128.webp", picture(photo, "image/png", 100));
            assertServed(LIBRARY_DIR + photo + "/orig.webp", picture(photo, "image/png", 0));
            assertServed(LIBRARY_DIR + sheet + "/page1-w128.webp", picture(sheet, "application/pdf", 64));
            assertServed(LIBRARY_DIR + sheet + "/page1.webp", picture(sheet, "application/pdf", 300));
            assertServed(LIBRARY_DIR + sheet + "/orig.pdf", read(sheet, 128, AcceptedFormats.EVERY_FORMAT));
        }

        @Test
        void theWebpSettingHoldsBackEverySize() {
            MediaLayoutFixtures.copyInto(root);
            Mockito.when(config.imageVariantsWebp()).thenReturn(false);

            assertServed(
                    LIBRARY_DIR + MediaLayoutFixtures.LIBRARY_PHOTO + "/orig.png",
                    read(MediaLayoutFixtures.LIBRARY_PHOTO, 200, AcceptedFormats.EVERY_FORMAT));
        }

        @Test
        void theDrawnPageIsAPictureAndNeverTheDocument() throws IOException {
            Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available");
            byte[] pdf = pdf();
            String hash = upload(pdf, "application/pdf");

            assertTrue(picture(hash, "application/pdf", 0).isPresent());
            assertTrue(picture(hash, "application/pdf", 128).isPresent());
            assertArrayEquals(
                    pdf,
                    read(hash, 0, AcceptedFormats.EVERY_FORMAT).orElseThrow().data());
            assertArrayEquals(
                    pdf,
                    read(hash, 128, AcceptedFormats.EVERY_FORMAT).orElseThrow().data());
        }

        @Test
        void aFileWithoutAPictureAnswersNothing() {
            String svg =
                    upload("<svg><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8), "image/svg+xml");
            String zip = upload("PK".getBytes(StandardCharsets.UTF_8), "application/zip");

            assertTrue(picture(svg, "image/svg+xml", 0).isEmpty());
            assertTrue(picture(zip, "application/zip", 0).isEmpty());
            assertTrue(picture(zip, null, 0).isEmpty());
            assertFalse(ImageVariants.canHavePicture(null));
            assertTrue(ImageVariants.canHavePicture("image/gif"));
        }

        @Test
        void anImageWithoutSizesIsItsOwnPicture() throws IOException {
            byte[] png = png(64, 64);
            String hash = upload(png, "image/png");

            assertArrayEquals(png, picture(hash, "image/png", 0).orElseThrow().data());
            assertTrue(read("deadbeef", 256, AcceptedFormats.EVERY_FORMAT).isEmpty());
        }

        private String upload(byte[] data, String mimeType) {
            String hash = MediaStorageService.hash(data);
            library.store(STATION_ID, hash, data, mimeType);
            var at = library.locate(STATION_ID, hash);
            images.addSizes(at.scope(), at.category(), at.key(), data, mimeType);
            return hash;
        }

        private Optional<MediaContent> read(String hash, int width, AcceptedFormats accepted) {
            var at = library.locate(STATION_ID, hash);
            return images.read(ImageProfile.LIBRARY, at.scope(), at.category(), at.key(), width, accepted);
        }

        private Optional<MediaContent> picture(String hash, String mimeType, int width) {
            var at = library.locate(STATION_ID, hash);
            return images.picture(at.scope(), at.category(), at.key(), mimeType, width);
        }
    }

    private void storeAvatar(byte[] data) throws IOException {
        images.store(ImageProfile.ICON_SET, AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY, data, 0);
    }

    private Optional<MediaContent> avatar(int size) {
        return images.read(ImageProfile.ICON_SET, AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY, size);
    }

    private static void assertServed(String relative, Optional<MediaContent> served) {
        assertArrayEquals(
                MediaLayoutFixtures.stored(relative), served.orElseThrow().data(), relative);
    }

    private List<String> storedNames(String relativeDir) {
        try (Stream<Path> files = Files.list(root.resolve(relativeDir))) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> !name.endsWith(".meta.json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] png(int width, int height) throws IOException {
        return encoded(width, height, "png");
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        return encoded(width, height, "jpg");
    }

    private static byte[] encoded(int width, int height, String format) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] pdf() throws IOException {
        try (var document = new PDDocument()) {
            document.addPage(new PDPage());
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
