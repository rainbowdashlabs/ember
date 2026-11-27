/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.service.BorrowedGearService;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryExportService;
import dev.chojo.ember.feature.inventory.service.InventoryIntakeService;
import dev.chojo.ember.feature.inventory.service.InventoryLossService;
import dev.chojo.ember.feature.inventory.service.InventoryPictureService;
import dev.chojo.ember.feature.inventory.service.LossReportService;
import dev.chojo.ember.feature.inventory.service.MemberGearService;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
import dev.chojo.ember.feature.media.MediaLayoutFixtures;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Request;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Inventory photos over HTTP: an editor sends and removes them for an inventory and a kind, a reader
 * sees them on the piece but may not change them, deleting an inventory or a kind takes the files
 * along, and what is no usable picture or belongs to another station is refused.
 */
class InventoryPictureRoutesTest extends RepositoryTestBase {
    private static final int LIMIT = 200_000;

    @TempDir
    Path root;

    private Station station;
    private Station elsewhere;
    private Account account;
    private StationMember member;
    private int inventoryId;
    private RouteHarness harness;

    @BeforeEach
    void setUp() {
        station = stationRepo.create("PictureRouteStation");
        elsewhere = stationRepo.create("PictureRouteElsewhere");
        account = accountRepo.create("pictureroute@test.example", "Picture", "Route");
        member = stationMemberRepo.create(station.id(), account.id());
        inventoryId = inventoryRepo
                .create(station.id(), "Hüpfburgen", InventoryType.INTERNAL, false)
                .id();
        var backend = new LocalStorageBackend(root);
        var pictures = new InventoryPictureService(
                new ImageVariants(new StorageService(new StorageBackendResolver(backend), backend)),
                inventoryRepo,
                artRepo,
                stationRepo);
        var api = mock(Api.class);
        when(api.maxImageSizeBytes()).thenReturn(LIMIT);
        harness = RouteHarness.serving(
                        new InventoryPictureRoutes(pictures, inventoryService, api),
                        new InventoryArtRoutes(artService, inventoryService, pictures),
                        new InventoryRoutes(
                                inventoryService,
                                mock(InventoryCheckService.class),
                                mock(InventoryExportService.class),
                                mock(InventoryContainerService.class),
                                mock(MemberIdentityFactory.class),
                                mock(StationMemberService.class),
                                mock(InventoryLossService.class),
                                mock(LossReportService.class),
                                mock(InventoryIntakeService.class),
                                mock(BorrowedGearService.class),
                                mock(SelfCheckService.class),
                                mock(MemberGearService.class),
                                pictures))
                .withStations(stationRepo);
    }

    @AfterEach
    void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
        accountRepo.delete(account.id());
    }

    private Path filesOf(String key) {
        return root.resolve("station/" + stationRepo.requireUid(station.id()) + "/images/inventory/" + key);
    }

    private Consumer<Request.Builder> editor() {
        return harness.as(signedIn(
                member,
                StationPermission.INVENTORY_READ,
                StationPermission.INVENTORY_EDIT,
                StationPermission.INVENTORY_MANAGER));
    }

    private Consumer<Request.Builder> reader() {
        return harness.as(signedIn(member, StationPermission.INVENTORY_READ));
    }

    private Response put(String path, Consumer<Request.Builder> who, String field, String type, byte[] data) {
        return harness.request(client ->
                client.request(PREFIX + path, who.andThen(TestUploads.multipartPut(field, "burg.png", type, data))));
    }

    private Response putPicture(String path) {
        return put(path, editor(), "image", "image/png", MediaLayoutFixtures.samplePng());
    }

    private Response delete(String path) {
        return harness.request(client -> client.delete(PREFIX + path, null, editor()));
    }

    private String inventoryPicture() {
        return "/inventories/%d/picture".formatted(inventoryId);
    }

    @Test
    void anEditorSendsAPhotoAndAReaderSeesItOnThePiece() {
        var item = inventoryRepo.createItem(inventoryId, "H-1", "Hüpfburg", null, null);

        var stored = putPicture(inventoryPicture());
        assertEquals(200, stored.code());
        String key = json(stored).get("pictureKey").asString();
        assertEquals(key, inventoryRepo.findById(inventoryId).orElseThrow().pictureKey());

        var seen = harness.request(
                client -> client.get(PREFIX + "/inventory-items/%d/picture?size=64".formatted(item.id()), reader()));
        assertEquals(200, seen.code());
        assertTrue(header(seen, "Content-Type").startsWith("image/"), "a sized copy of the photo");

        assertEquals(204, delete(inventoryPicture()).code());
        assertNull(inventoryRepo.findById(inventoryId).orElseThrow().pictureKey());
        assertFalse(Files.exists(filesOf(key)));
    }

    @Test
    void aKindGetsItsOwnPhotoAndLosesItAgain() {
        var kind = artRepo.create(inventoryId, "Groß", "", 0);
        String path = "/inventories/%d/arts/%d/picture".formatted(inventoryId, kind.id());

        var stored = putPicture(path);
        assertEquals(200, stored.code());
        String key = json(stored).get("pictureKey").asString();
        assertEquals(key, artRepo.findById(kind.id()).orElseThrow().pictureKey());
        assertEquals(
                200,
                harness.request(client -> client.get(PREFIX + path, reader())).code());

        assertEquals(204, delete(path).code());
        assertNull(artRepo.findById(kind.id()).orElseThrow().pictureKey());
        assertFalse(Files.exists(filesOf(key)));
    }

    /** A piece gets a photo of its own, shows it, loses it again, and takes it along when it is deleted. */
    @Test
    void aPieceGetsItsOwnPhotoAndTakesItAlong() {
        var piece = inventoryRepo.createItem(inventoryId, "H-7", "Hüpfburg Pirat", null, null);
        String path = "/inventory-items/%d/picture".formatted(piece.id());

        assertEquals(
                403,
                put(path, reader(), "image", "image/png", MediaLayoutFixtures.samplePng())
                        .code());
        String first = json(putPicture(path)).get("pictureKey").asString();
        assertEquals(
                first,
                inventoryRepo.findItemPresentation(piece.id()).orElseThrow().pictureKey());
        assertEquals(204, delete(path).code());
        assertNull(inventoryRepo.findItemPresentation(piece.id()).orElseThrow().pictureKey());
        assertFalse(Files.exists(filesOf(first)));

        String second = json(putPicture(path)).get("pictureKey").asString();
        assertEquals(204, delete("/inventory-items/%d".formatted(piece.id())).code());
        assertFalse(Files.exists(filesOf(second)), "the photo goes with the piece");
    }

    /** Descriptions are set on the inventory, a kind and a piece; an editor may, a reader may not. */
    @Test
    void descriptionsAreSetForInventoryKindAndPiece() {
        var kind = artRepo.create(inventoryId, "Groß", "", 0);
        var piece = inventoryRepo.createItem(inventoryId, "H-8", "Hüpfburg Drache", null, null);
        String text = "{\"description\":\"  **Aufbau** zu zweit  \"}";

        var inventory = harness.request(client ->
                client.put(PREFIX + "/inventories/%d/description".formatted(inventoryId), body(text), editor()));
        assertEquals("**Aufbau** zu zweit", json(inventory).get("description").asString());
        var art = harness.request(client -> client.put(
                PREFIX + "/inventories/%d/arts/%d/description".formatted(inventoryId, kind.id()),
                body(text),
                editor()));
        assertEquals("**Aufbau** zu zweit", json(art).get("description").asString());
        var item = harness.request(client ->
                client.put(PREFIX + "/inventory-items/%d/description".formatted(piece.id()), body(text), editor()));
        assertEquals("**Aufbau** zu zweit", json(item).get("description").asString());
        var read = harness.request(
                client -> client.get(PREFIX + "/inventory-items/%d/presentation".formatted(piece.id()), reader()));
        assertEquals("**Aufbau** zu zweit", json(read).get("description").asString());

        var forbidden = harness.request(client ->
                client.put(PREFIX + "/inventories/%d/description".formatted(inventoryId), body(text), reader()));
        assertEquals(403, forbidden.code());
        String tooLong = "{\"description\":\"" + "x".repeat(DescriptionRequest.MAX_LENGTH + 1) + "\"}";
        assertEquals(
                InventoryRefusal.DESCRIPTION_TOO_LONG,
                refusalOf(harness.request(client -> client.put(
                        PREFIX + "/inventories/%d/description".formatted(inventoryId), body(tooLong), editor()))));
    }

    /**
     * Another station's piece, kind or inventory looks gone on every new route, a kind of another
     * inventory is refused, a reader may change nothing, and the association's piece keeps the
     * association's text and photo.
     */
    @Test
    void theNewRoutesGuardWhatIsNotTheirs() {
        var strangerInventory = inventoryRepo
                .create(elsewhere.id(), "Fremdes", InventoryType.INTERNAL, false)
                .id();
        var strangerArt = artRepo.create(strangerInventory, "Fremd", "", 0);
        var strangerPiece = inventoryRepo.createItem(strangerInventory, "F-1", "Fremdgerät", null, null);
        var ownArt = artRepo.create(inventoryId, "Eigen", "", 0);
        var ownPiece = inventoryRepo.createItem(inventoryId, "E-1", "Eigengerät", null, null);
        var association = clusterService.create("Kreisverband Burgen " + UUID.randomUUID(), null);
        var external = inventoryRepo
                .create(station.id(), "Burgen des Kreises", InventoryType.EXTERNAL, false)
                .id();
        var associations =
                inventoryRepo.createItem(external, "K-1", "Kreisburg", null, null, ItemOwner.CLUSTER, association.id());
        String text = "{\"description\":\"Text\"}";

        assertEquals(
                404,
                putJson("/inventories/%d/description".formatted(strangerInventory), text, editor())
                        .code());
        assertEquals(
                404,
                putJson(
                                "/inventories/%d/arts/%d/description".formatted(strangerInventory, strangerArt.id()),
                                text,
                                editor())
                        .code());
        assertEquals(
                InventoryRefusal.ITEM_KIND_NOT_IN_INVENTORY,
                refusalOf(putJson(
                        "/inventories/%d/arts/%d/description".formatted(inventoryId, strangerArt.id()),
                        text,
                        editor())));
        assertEquals(
                404,
                putJson("/inventory-items/%d/description".formatted(strangerPiece.id()), text, editor())
                        .code());
        assertEquals(
                404,
                harness.request(client -> client.get(
                                PREFIX + "/inventory-items/%d/presentation".formatted(strangerPiece.id()), reader()))
                        .code());
        assertEquals(
                404,
                putPicture("/inventory-items/%d/picture".formatted(strangerPiece.id()))
                        .code());
        assertEquals(
                404,
                delete("/inventory-items/%d/picture".formatted(strangerPiece.id()))
                        .code());

        assertEquals(
                403,
                putJson("/inventory-items/%d/description".formatted(ownPiece.id()), text, reader())
                        .code());
        assertEquals(
                403,
                putJson("/inventories/%d/arts/%d/description".formatted(inventoryId, ownArt.id()), text, reader())
                        .code());

        assertEquals(
                InventoryRefusal.INVENTORY_ASSOCIATION_GEAR_NOT_YOURS_TO_DESCRIBE,
                refusalOf(putJson("/inventory-items/%d/description".formatted(associations.id()), text, editor())));
        assertEquals(
                InventoryRefusal.INVENTORY_ASSOCIATION_GEAR_NOT_YOURS_TO_DESCRIBE,
                refusalOf(putPicture("/inventory-items/%d/picture".formatted(associations.id()))));
        assertEquals(
                InventoryRefusal.INVENTORY_ASSOCIATION_GEAR_NOT_YOURS_TO_DESCRIBE,
                refusalOf(delete("/inventory-items/%d/picture".formatted(associations.id()))));
    }

    private Response putJson(String path, String json, Consumer<Request.Builder> who) {
        return harness.request(client -> client.put(PREFIX + path, body(json), who));
    }

    @Test
    void deletingAKindOrAnInventoryTakesItsPhotosAlong() {
        var kind = artRepo.create(inventoryId, "Klein", "", 0);
        var other = artRepo.create(inventoryId, "Mittel", "", 1);
        String kindKey = json(putPicture("/inventories/%d/arts/%d/picture".formatted(inventoryId, kind.id())))
                .get("pictureKey")
                .asString();
        String otherKey = json(putPicture("/inventories/%d/arts/%d/picture".formatted(inventoryId, other.id())))
                .get("pictureKey")
                .asString();
        String inventoryKey =
                json(putPicture(inventoryPicture())).get("pictureKey").asString();

        assertEquals(
                204,
                delete("/inventories/%d/arts/%d".formatted(inventoryId, kind.id()))
                        .code());
        assertFalse(Files.exists(filesOf(kindKey)));
        assertTrue(Files.exists(filesOf(otherKey)));

        assertEquals(204, delete("/inventories/%d".formatted(inventoryId)).code());
        assertFalse(Files.exists(filesOf(otherKey)));
        assertFalse(Files.exists(filesOf(inventoryKey)));
    }

    @Test
    void aReaderMayNotSendOne() {
        var answer = put(inventoryPicture(), reader(), "image", "image/png", MediaLayoutFixtures.samplePng());

        assertEquals(403, answer.code());
        assertNull(inventoryRepo.findById(inventoryId).orElseThrow().pictureKey());
    }

    @Test
    void whatIsNoUsablePictureIsRefused() {
        assertEquals(
                "I-301",
                refusalOf(put(inventoryPicture(), editor(), "image", "application/pdf", new byte[] {1, 2, 3}))
                        .code());
        assertEquals(
                "I-300",
                refusalOf(put(inventoryPicture(), editor(), "file", "image/png", MediaLayoutFixtures.samplePng()))
                        .code());
        assertEquals(
                "G-014",
                refusalOf(put(inventoryPicture(), editor(), "image", "image/png", new byte[LIMIT + 1]))
                        .code());
        assertNull(inventoryRepo.findById(inventoryId).orElseThrow().pictureKey());
    }

    @Test
    void aPhotoIsReadByItsKeyOnlyAtItsOwnStation() {
        String key = json(putPicture(inventoryPicture())).get("pictureKey").asString();

        var own = harness.request(client -> client.get(PREFIX + "/inventory-pictures/" + key + "?size=64", reader()));
        assertEquals(200, own.code());

        var unknown =
                harness.request(client -> client.get(PREFIX + "/inventory-pictures/" + UUID.randomUUID(), reader()));
        assertEquals("I-299", refusalOf(unknown).code());

        var kind = artRepo.create(inventoryId, "Rutsche", "", 0);
        String kindKey = json(putPicture("/inventories/%d/arts/%d/picture".formatted(inventoryId, kind.id())))
                .get("pictureKey")
                .asString();
        var ownKind = harness.request(client -> client.get(PREFIX + "/inventory-pictures/" + kindKey, reader()));
        assertEquals(200, ownKind.code());

        var lendingManager = harness.request(client -> client.get(
                PREFIX + "/inventory-pictures/" + kindKey,
                harness.as(signedIn(member, StationPermission.INVENTORY_LENDING_MANAGER))));
        assertEquals(200, lendingManager.code(), "the overview of what is offered shows the photos too");

        var otherMember = stationMemberRepo.create(elsewhere.id(), account.id());
        for (String foreignKey : new String[] {key, kindKey}) {
            var foreign = harness.request(client -> client.get(
                    PREFIX + "/inventory-pictures/" + foreignKey,
                    harness.as(signedIn(otherMember, StationPermission.INVENTORY_READ))));
            assertEquals("I-299", refusalOf(foreign).code());
        }
    }

    @Test
    void anotherStationsInventoryIsRefusedAsGone() {
        int strangerInventory = inventoryRepo
                .create(elsewhere.id(), "Fremdes", InventoryType.INTERNAL, false)
                .id();

        var answer = putPicture("/inventories/%d/picture".formatted(strangerInventory));

        assertEquals("I-296", refusalOf(answer).code());
        assertNull(inventoryRepo.findById(strangerInventory).orElseThrow().pictureKey());
    }
}
