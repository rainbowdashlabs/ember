/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingPicture;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.inventory.service.InventoryPictureService;
import dev.chojo.ember.feature.media.MediaLayoutFixtures;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The photos of lent gear between partners: the lender sends only photos of gear on offer, and the
 * borrower keeps one drawn copy per photo and asks the partner only once.
 */
class LendingPictureServiceTest {
    private static final int STATION = 4;
    private static final UUID PARTNER_STATION = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID PHOTO = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @TempDir
    Path root;

    private LendingService lending;
    private InventoryPictureService pictures;
    private FederationService federation;
    private FederationTransport transport;
    private LendingPictureService service;

    @BeforeEach
    void setUp() {
        lending = mock(LendingService.class);
        pictures = mock(InventoryPictureService.class);
        federation = mock(FederationService.class);
        transport = mock(FederationTransport.class);
        var backend = new LocalStorageBackend(root);
        service = new LendingPictureService(
                lending,
                pictures,
                federation,
                transport,
                new ImageVariants(new StorageService(new StorageBackendResolver(backend), backend)));
    }

    private static ServingPartner asking() {
        var row = mock(FederationPartner.class);
        when(row.stationId()).thenReturn(STATION);
        return new ServingPartner(row, PARTNER_STATION);
    }

    @Test
    void theLenderSendsOnlyPhotosOfGearOnOffer() {
        var partner = asking();
        when(lending.isPictureOffered(partner, "offered")).thenReturn(true);
        byte[] png = MediaLayoutFixtures.samplePng();
        when(pictures.readByKey(STATION, "offered", LendingPictureService.TRAVEL_SIDE))
                .thenReturn(new MediaContent(png, "image/png"));

        var sent = service.servePicture(partner, "offered");

        assertEquals("image/png", sent.mimeType());
        assertEquals(Base64.getEncoder().encodeToString(png), sent.base64());
        var hidden = assertThrows(RefusalResponse.class, () -> service.servePicture(partner, "elsewhere"));
        assertEquals(FederationRefusal.LENDING_PICTURE_NOT_OFFERED, hidden.refusal());
    }

    @Test
    void aMissingPhotoOfOfferedGearIsRefusedAsNotOffered() {
        var partner = asking();
        when(lending.isPictureOffered(partner, "gone")).thenReturn(true);
        when(pictures.readByKey(STATION, "gone", LendingPictureService.TRAVEL_SIDE))
                .thenThrow(InventoryRefusal.INVENTORY_PICTURE_NOT_HERE.raise());

        var refused = assertThrows(RefusalResponse.class, () -> service.servePicture(partner, "gone"));

        assertEquals(FederationRefusal.LENDING_PICTURE_NOT_OFFERED, refused.refusal());
    }

    @Test
    void aPhotoTooHeavyToKeepIsNotSent() {
        var partner = asking();
        when(lending.isPictureOffered(partner, "heavy")).thenReturn(true);
        when(pictures.readByKey(STATION, "heavy", LendingPictureService.TRAVEL_SIDE))
                .thenReturn(new MediaContent(new byte[LendingPictureService.MAX_PICTURE_BYTES + 1], "image/gif"));

        var refused = assertThrows(RefusalResponse.class, () -> service.servePicture(partner, "heavy"));

        assertEquals(FederationRefusal.LENDING_PICTURE_NOT_OFFERED, refused.refusal());
    }

    private FederationPartner lendingPartner(FederationPartner.FederationStatus status, boolean lends) {
        var partner = mock(FederationPartner.class);
        when(partner.partnerStationId()).thenReturn(PARTNER_STATION);
        when(partner.status()).thenReturn(status);
        when(federation.findPartners(STATION)).thenReturn(List.of(partner));
        when(federation.hasCapability(partner, CapabilityType.INVENTORY_LEND, Direction.IMPORT))
                .thenReturn(lends);
        return partner;
    }

    private static String gif() throws IOException {
        var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "gif", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void theBorrowerKeepsADrawnCopyAndAsksThePartnerOnce() throws IOException {
        var partner = lendingPartner(FederationPartner.FederationStatus.ACTIVE, true);
        String sent = gif();
        when(transport.get(eq(partner), any(FederationRequest.class), eq(RemoteLendingPicture.class)))
                .thenReturn(new RemoteLendingPicture("image/gif", sent));

        var first = service.federatedPicture(STATION, PARTNER_STATION, PHOTO, 0);
        var second = service.federatedPicture(STATION, PARTNER_STATION, PHOTO, 64);

        assertEquals("image/png", first.contentType(), "what came in is drawn again, never kept as it arrived");
        assertFalse(Arrays.equals(Base64.getDecoder().decode(sent), first.data()));
        assertTrue(second.data().length > 0);
        verify(transport, times(1)).get(eq(partner), any(FederationRequest.class), eq(RemoteLendingPicture.class));
    }

    @Test
    void aPartnerThatIsNotActiveOrDoesNotLendGetsNoPhotoAsked() {
        lendingPartner(FederationPartner.FederationStatus.SUSPENDED, true);
        assertEquals(
                FederationRefusal.LENDING_PICTURE_NOT_OFFERED,
                assertThrows(RefusalResponse.class, () -> service.federatedPicture(STATION, PARTNER_STATION, PHOTO, 64))
                        .refusal());

        lendingPartner(FederationPartner.FederationStatus.ACTIVE, false);
        assertEquals(
                FederationRefusal.LENDING_PICTURE_NOT_OFFERED,
                assertThrows(RefusalResponse.class, () -> service.federatedPicture(STATION, PARTNER_STATION, PHOTO, 64))
                        .refusal());
        verify(transport, times(0)).get(any(), any(FederationRequest.class), any());
    }

    @Test
    void aStationThatDoesNotLendToUsGetsNoPhotoAsked() {
        when(federation.findPartners(STATION)).thenReturn(List.of());

        var refused = assertThrows(
                RefusalResponse.class, () -> service.federatedPicture(STATION, PARTNER_STATION, PHOTO, 64));

        assertEquals(FederationRefusal.LENDING_PICTURE_NOT_OFFERED, refused.refusal());
        verify(transport, times(0)).get(any(), any(FederationRequest.class), any());
    }
}
