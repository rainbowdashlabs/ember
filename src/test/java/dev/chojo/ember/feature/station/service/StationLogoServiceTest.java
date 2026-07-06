/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository.StationLogo;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationLogoServiceTest {

    private static final int STATION_ID = 7;
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-4000-a000-00000000000b");

    @TempDir
    Path tempDir;

    private StationRepository stationRepository;
    private StationLogoService logoService;

    @BeforeEach
    void setup() {
        var backend = new LocalStorageBackend(tempDir);
        var resolver = new StorageBackendResolver(backend);
        var storageService = new StorageService(resolver, backend);
        var variants = new ImageVariantService(storageService);

        stationRepository = Mockito.mock(StationRepository.class);
        when(stationRepository.resolveUid(STATION_ID)).thenReturn(STATION_UID);
        when(stationRepository.findLogo(STATION_ID)).thenReturn(Optional.empty());

        logoService = new StationLogoService(variants, stationRepository);
    }

    @Test
    void storeReadOriginalDeleteRoundTrip() throws IOException {
        logoService.store(STATION_ID, pngBytes(300, 200), "image/png");
        verify(stationRepository).deleteLogo(STATION_ID);

        assertTrue(logoService.exists(STATION_ID));

        var small = logoService.read(STATION_ID, 64);
        assertTrue(small.isPresent());
        assertEquals("image/png", small.orElseThrow().contentType());

        assertTrue(logoService.original(STATION_ID).isPresent());

        logoService.delete(STATION_ID);
        assertFalse(logoService.exists(STATION_ID));
    }

    @Test
    void readReturnsEmptyWhenNoLogo() {
        assertTrue(logoService.read(STATION_ID, 64).isEmpty());
        assertFalse(logoService.exists(STATION_ID));
    }

    @Test
    void lazilyMigratesRasterLegacyBlob() throws IOException {
        byte[] png = pngBytes(300, 200);
        when(stationRepository.findLogo(STATION_ID)).thenReturn(Optional.of(new StationLogo(png, "image/png")));

        var result = logoService.read(STATION_ID, 128);
        assertTrue(result.isPresent());

        verify(stationRepository).deleteLogo(STATION_ID);

        when(stationRepository.findLogo(STATION_ID)).thenReturn(Optional.empty());
        assertTrue(logoService.read(STATION_ID, 128).isPresent());
    }

    @Test
    void servesSvgLegacyBlobAsIs() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes();
        when(stationRepository.findLogo(STATION_ID)).thenReturn(Optional.of(new StationLogo(svg, "image/svg+xml")));

        var result = logoService.read(STATION_ID, 64);
        assertTrue(result.isPresent());
        assertEquals("image/svg+xml", result.orElseThrow().contentType());
        assertEquals(svg.length, result.orElseThrow().data().length);
    }

    @Test
    void existsReflectsLegacyBlob() {
        when(stationRepository.findLogo(STATION_ID))
                .thenReturn(Optional.of(new StationLogo(pngBytes(10, 10), "image/png")));
        assertTrue(logoService.exists(STATION_ID));
    }

    private static byte[] pngBytes(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(220, 70, 30));
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }
}
