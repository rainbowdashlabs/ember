/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.equipment.repository.EquipmentAvailabilityRepository;
import dev.chojo.ember.feature.equipment.repository.EquipmentNeedRepository;
import dev.chojo.ember.feature.equipment.service.EquipmentAvailabilityService;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.InventoryShareRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteKind;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemotePiece;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteProductSummary;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.repository.InventorySearchRepository;
import dev.chojo.ember.feature.inventory.service.InventorySearchService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestFederationServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The lending catalog as a partner sees it: one product per shared inventory, counted per kind or listed
 * piece by piece, narrowed by days and by search, with a calendar per day, and asked for across a
 * partnership on this instance.
 */
class LendingCatalogServiceTest extends RepositoryTestBase {
    private static final LocalDate DAY = LocalDate.of(2030, 6, 12);

    private static LendingService lending;
    private static LendingCatalogService catalog;
    private static FederationService federation;
    private static FederationRepository federationRepo;
    private static InventoryShareService shares;

    private static Station lender;
    private static Station borrower;
    private static ServingPartner asking;

    private static int radios;
    private static int handheld;
    private static int charger;
    private static int handheldOne;
    private static int games;
    private static int catan;
    private static int secret;
    private static int hidden;
    private static int benches;
    private static Station quiet;
    private static ServingPartner askingQuiet;
    private static int borrowerMember;

    @BeforeAll
    static void setUp() {
        federationRepo = new FederationRepository();
        federation = TestFederationServices.of(federationRepo, stationRepo);
        shares = new InventoryShareService(new InventoryShareRepository(), federation, inventoryRepo, artRepo);
        var availability = new EquipmentAvailabilityService(
                new EquipmentAvailabilityRepository(), new EquipmentNeedRepository(), eventRepo, occurrenceCalendar);
        var transport = new FederationTestTransport(mock(FederationHttpClient.class), federationRepo, stationRepo);
        var fanout = new FederationFanout(new TaskScheduler());
        lending = new LendingService(
                new LendingRepository(),
                transport.transport(),
                federation,
                fanout,
                stationRepo,
                inventoryRepo,
                clusterRepo,
                itemCustodyService,
                borrowedGearService,
                shares,
                artRepo,
                lineTargetService,
                availability,
                new DomainEventBus(Set.of()),
                new InventorySearchService(new InventorySearchRepository(), stationRepo));
        catalog = new LendingCatalogService(
                lending,
                shares,
                inventoryRepo,
                artRepo,
                stationRepo,
                availability,
                federation,
                transport.transport(),
                fanout);
        transport.serve(lending, catalog);

        lender = stationRepo.create("Catalog Lender " + UUID.randomUUID());
        borrower = stationRepo.create("Catalog Borrower " + UUID.randomUUID());
        stationRepo.updateLocation(lender.id(), "Hauptstraße 1", "12345", "Musterstadt", "DE", null, null);
        var keyPair = federation.generateKeyPair();
        federation.acceptInvite(borrower.id(), lender.id(), federation.encodePublicKey(keyPair), null, null);
        for (var station : List.of(lender, borrower)) {
            for (var partner : federation.findPartners(station.id())) {
                federationRepo.upsertCapability(partner.id(), CapabilityType.INVENTORY_LEND, Direction.IMPORT, true);
                federationRepo.upsertCapability(partner.id(), CapabilityType.INVENTORY_LEND, Direction.EXPORT, true);
            }
        }
        var lenderSide = federation.findPartners(lender.id()).getFirst();
        asking = new ServingPartner(lenderSide, borrower.uid());

        radios = inventoryRepo
                .create(lender.id(), "Funk", InventoryType.INTERNAL, false, false)
                .id();
        inventoryRepo.setDescription(radios, "**Digitale** Handfunkgeräte\n\n- mit Akku\n- mit Clip");
        handheld = artRepo.create(radios, "FuG 8b", "intern", 0).id();
        artRepo.setDescription(handheld, "Mit Lautsprechermikrofon");
        charger = artRepo.create(radios, "Ladestation", "", 1).id();
        handheldOne =
                inventoryRepo.createItem(radios, "F-1", "FuG 8b 1", null, null).id();
        int handheldTwo =
                inventoryRepo.createItem(radios, "F-2", "FuG 8b 2", null, null).id();
        int chargerOne =
                inventoryRepo.createItem(radios, "L-1", "Lader 1", null, null).id();
        int handheldKept =
                inventoryRepo.createItem(radios, "F-3", "FuG 8b 3", null, null).id();
        artRepo.setArt(handheld, List.of(handheldOne, handheldTwo, handheldKept));
        artRepo.setArt(charger, List.of(chargerOne));
        artRepo.setPictureKey(handheld, "fug-photo");
        int cases = artRepo.create(radios, "Koffer", "", 2).id();
        int caseOne =
                inventoryRepo.createItem(radios, "K-1", "Koffer 1", null, null).id();
        artRepo.setArt(cases, List.of(caseOne));
        artRepo.setPictureKey(cases, "case-photo");
        shares.setInventoryShare(lender.id(), radios, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        shares.setItemShare(lender.id(), handheldKept, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());
        shares.setItemShare(lender.id(), caseOne, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());

        games = inventoryRepo
                .create(lender.id(), "Spiele", InventoryType.INTERNAL, false, false)
                .id();
        catan = inventoryRepo
                .createItem(games, "SP-1", "Siedler von Catan", null, null)
                .id();
        secret = inventoryRepo
                .createItem(games, "SP-2", "Geheimspiel", null, null)
                .id();
        inventoryRepo.setItemDescription(catan, "Rohstoffe handeln");
        inventoryRepo.setItemPictureKey(catan, "catan-photo");
        inventoryRepo.setItemPictureKey(secret, "secret-photo");
        shares.setInventoryShare(lender.id(), games, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        shares.setItemShare(lender.id(), secret, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());

        hidden = inventoryRepo
                .create(lender.id(), "Nicht verliehen", InventoryType.INTERNAL, true)
                .id();
        inventoryRepo.createItem(hidden, "N-1", "Nicht verliehen", null, null);
        inventoryRepo.setPictureKey(hidden, "hidden-photo");

        benches = inventoryRepo
                .create(lender.id(), "Bänke", InventoryType.INTERNAL, false)
                .id();
        for (int index = 1; index <= 3; index++) {
            inventoryRepo.createItem(benches, "B-" + index, "Bank " + index, null, null);
        }
        shares.setInventoryShare(lender.id(), benches, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());

        quiet = stationRepo.create("Catalog Quiet " + UUID.randomUUID());
        var quietKeys = federation.generateKeyPair();
        federation.acceptInvite(borrower.id(), quiet.id(), federation.encodePublicKey(quietKeys), null, null);
        askingQuiet = new ServingPartner(federation.findPartners(quiet.id()).getFirst(), borrower.uid());

        var account = accountRepo.create("catalog-" + UUID.randomUUID() + "@test.example", "Kata", "Log");
        borrowerMember = stationMemberRepo.create(borrower.id(), account.id()).id();
    }

    @AfterAll
    static void cleanup() {
        for (var station : List.of(lender, borrower)) {
            for (var partner : federation.findPartners(station.id())) federationRepo.deletePartner(partner.id());
        }
        for (var partner : federation.findPartners(quiet.id())) federationRepo.deletePartner(partner.id());
        stationRepo.delete(lender.id());
        stationRepo.delete(borrower.id());
        stationRepo.delete(quiet.id());
    }

    private static RemoteProductSummary summaryOf(List<RemoteProductSummary> products, int inventoryId) {
        return products.stream()
                .filter(p -> p.inventoryId() == inventoryId)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Every shared inventory is one product: kinds are counted, the pieces without a kind of an
     * inventory of different things are single pieces, and an inventory of copies is one count.
     */
    @Test
    void eachSharedInventoryIsOneProduct() {
        var products = catalog.serveProducts(asking, null, null, null);

        assertTrue(products.offersAnything());
        assertFalse(products.products().stream().anyMatch(p -> p.inventoryId() == hidden));
        var funk = summaryOf(products.products(), radios);
        assertEquals(2, funk.kinds());
        assertEquals(3, funk.free());
        assertEquals(0, funk.pieces());
        assertEquals("Digitale Handfunkgeräte mit Akku mit Clip", funk.teaser());
        var spiele = summaryOf(products.products(), games);
        assertEquals(0, spiele.kinds());
        assertEquals(1, spiele.pieces(), "the withheld game is not counted");
        var bank = summaryOf(products.products(), benches);
        assertEquals(1, bank.kinds());
        assertEquals(3, bank.free());
    }

    /** A search through a kind or a piece shows its name on the tile; a withheld piece shows nothing. */
    @Test
    void aSearchShowsWhatItMatched() {
        assertEquals(
                List.of("FuG 8b"),
                summaryOf(
                                catalog.serveProducts(asking, "Lautsprecher", null, null)
                                        .products(),
                                radios)
                        .matched());
        assertEquals(
                List.of("Siedler von Catan"),
                summaryOf(catalog.serveProducts(asking, "catan", null, null).products(), games)
                        .matched());
        assertTrue(catalog.serveProducts(asking, "Geheimspiel", null, null)
                .products()
                .isEmpty());
    }

    /** A blocked piece is not free over the days it is blocked, and a station-wide block empties the catalog. */
    @Test
    void blocksTakeWhatTheyCoverOutOfTheWindow() {
        var block = lending.createBlock(lender.id(), null, handheldOne, DAY, DAY, "Wartung");
        try {
            var funk = summaryOf(catalog.serveProducts(asking, null, DAY, DAY).products(), radios);
            assertEquals(2, funk.free());
            var product = catalog.serveProduct(asking, radios, DAY, DAY);
            assertEquals(1, kindOf(product.kinds(), handheld).free());
            assertEquals(
                    3,
                    summaryOf(
                                    catalog.serveProducts(asking, null, DAY.plusDays(1), DAY.plusDays(1))
                                            .products(),
                                    radios)
                            .free());
        } finally {
            lending.deleteBlock(block.id(), lender.id());
        }
        var all = lending.createBlock(lender.id(), null, null, DAY, DAY, "Feiertag");
        try {
            var products = catalog.serveProducts(asking, null, DAY, DAY);
            assertTrue(products.offersAnything());
            assertTrue(products.products().isEmpty());
        } finally {
            lending.deleteBlock(all.id(), lender.id());
        }
    }

    /** The product page lists kinds with their descriptions, the address, and never the internal note. */
    @Test
    void aProductWithKindsCountsThem() {
        var product = catalog.serveProduct(asking, radios, null, null);

        assertEquals(
                List.of(handheld, charger),
                product.kinds().stream().map(RemoteKind::artId).toList());
        assertEquals(
                "Mit Lautsprechermikrofon", kindOf(product.kinds(), handheld).description());
        assertEquals(2, kindOf(product.kinds(), handheld).free());
        assertEquals("Musterstadt", product.address().city());
        assertTrue(product.pieces().isEmpty());
    }

    /** The pieces without a kind are listed one by one with their own text and photo. */
    @Test
    void piecesWithoutAKindAreListedOneByOne() {
        var product = catalog.serveProduct(asking, games, null, null);

        assertTrue(product.kinds().isEmpty());
        assertEquals(
                List.of(catan),
                product.pieces().stream().map(RemotePiece::itemId).toList());
        RemotePiece piece = product.pieces().getFirst();
        assertEquals("Rohstoffe handeln", piece.description());
        assertEquals("catan-photo", piece.pictureKey());
        assertTrue(piece.free());
    }

    /** An inventory or a piece not offered to the partner is refused as not offered. */
    @Test
    void whatIsNotOfferedIsRefused() {
        var inventory = assertThrows(RefusalResponse.class, () -> catalog.serveProduct(asking, hidden, null, null));
        assertEquals(FederationRefusal.LENDING_PRODUCT_NOT_OFFERED, inventory.refusal());
        var piece = assertThrows(
                RefusalResponse.class, () -> catalog.serveProductDays(asking, games, YearMonth.from(DAY), secret));
        assertEquals(FederationRefusal.LENDING_PRODUCT_NOT_OFFERED, piece.refusal());
    }

    /**
     * The calendar counts each day of the month per kind, and a blocked day has nothing free. An
     * inventory of copies is one row, and single pieces are one row of how many of them are free.
     */
    @Test
    void theCalendarCountsEachDay() {
        var block = lending.createBlock(lender.id(), radios, null, DAY, DAY, "Übung");
        try {
            var days = catalog.serveProductDays(asking, radios, YearMonth.from(DAY), null)
                    .days();

            assertEquals(30, days.size());
            var blocked = days.stream()
                    .filter(day -> day.day().equals(DAY))
                    .findFirst()
                    .orElseThrow();
            assertTrue(blocked.blocked());
            assertTrue(blocked.counts().stream().allMatch(count -> count.free() == 0));
            var open = days.stream()
                    .filter(day -> day.day().equals(DAY.plusDays(1)))
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    2,
                    open.counts().stream()
                            .filter(c -> Integer.valueOf(handheld).equals(c.artId()))
                            .findFirst()
                            .orElseThrow()
                            .free());
        } finally {
            lending.deleteBlock(block.id(), lender.id());
        }
        var bankDay = catalog.serveProductDays(asking, benches, YearMonth.from(DAY), null)
                .days()
                .getFirst();
        assertEquals(1, bankDay.counts().size());
        assertEquals(3, bankDay.counts().getFirst().free());
        var gameDay = catalog.serveProductDays(asking, games, YearMonth.from(DAY), null)
                .days()
                .getFirst();
        assertEquals(1, gameDay.counts().getFirst().offered());
        var catanDay = catalog.serveProductDays(asking, games, YearMonth.from(DAY), catan)
                .days()
                .getFirst();
        assertEquals(1, catanDay.counts().getFirst().free());
    }

    /**
     * The photos the product page shows may be fetched: an offered piece's and a kind's with an offered
     * piece. A withheld piece's, a kind's whose pieces are all withheld and an unshared inventory's may not.
     */
    @Test
    void theProductPagePhotosAreOffered() {
        assertTrue(lending.isPictureOffered(asking, "catan-photo"));
        assertTrue(lending.isPictureOffered(asking, "fug-photo"));
        assertFalse(lending.isPictureOffered(asking, "secret-photo"));
        assertFalse(lending.isPictureOffered(asking, "case-photo"));
        assertFalse(lending.isPictureOffered(asking, "hidden-photo"));
    }

    /**
     * A claim that names a shared piece takes it out of the count even where the kind has more stock
     * than is shared, on the product page and in the calendar alike.
     */
    @Test
    void aNamedClaimTakesItsPieceOutWhereStockIsNotShared() {
        var block = lending.createBlock(lender.id(), null, handheldOne, DAY, DAY, "Wartung");
        try {
            assertEquals(
                    1,
                    kindOf(catalog.serveProduct(asking, radios, DAY, DAY).kinds(), handheld)
                            .free());
            var day = catalog.serveProductDays(asking, radios, YearMonth.from(DAY), null).days().stream()
                    .filter(entry -> entry.day().equals(DAY))
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    1,
                    day.counts().stream()
                            .filter(count -> Integer.valueOf(handheld).equals(count.artId()))
                            .findFirst()
                            .orElseThrow()
                            .free());
        } finally {
            lending.deleteBlock(block.id(), lender.id());
        }
    }

    /**
     * An approved request for a count of a kind sets pieces aside for its days, and the product counts
     * them out of those days. It has its own inventory, because approving hands the pieces to the loan.
     */
    @Test
    void anApprovedRequestTakesItsPiecesOutOfTheWindow() {
        int tents = inventoryRepo
                .create(lender.id(), "Zelte", InventoryType.INTERNAL, false, false)
                .id();
        int dome = artRepo.create(tents, "Kuppelzelt", "", 0).id();
        var pieces = List.of(
                inventoryRepo.createItem(tents, "Z-1", "Zelt 1", null, null).id(),
                inventoryRepo.createItem(tents, "Z-2", "Zelt 2", null, null).id(),
                inventoryRepo.createItem(tents, "Z-3", "Zelt 3", null, null).id());
        artRepo.setArt(dome, pieces);
        shares.setInventoryShare(lender.id(), tents, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
        LocalDate later = DAY.plusDays(5);
        var request = lending.createRequest(
                borrower.id(), lender.id(), later, later, borrowerMember, null, null, "Zeltlager");
        lending.addRequestItem(request.id(), tents, null, dome, 2, null);
        assertTrue(lending.approveRequest(request.id(), lender.id()));

        assertEquals(
                1,
                kindOf(catalog.serveProduct(asking, tents, later, later).kinds(), dome)
                        .free());
        var day = catalog.serveProductDays(asking, tents, YearMonth.from(later), null).days().stream()
                .filter(entry -> entry.day().equals(later))
                .findFirst()
                .orElseThrow();
        assertEquals(1, day.counts().getFirst().free());
    }

    /** A partner that shares nothing sees nothing and is refused every product. */
    @Test
    void aPartnerSharingNothingOffersNothing() {
        assertFalse(catalog.serveProducts(askingQuiet, null, null, null).offersAnything());
        var refused = assertThrows(RefusalResponse.class, () -> catalog.serveProduct(askingQuiet, radios, null, null));
        assertEquals(FederationRefusal.LENDING_PRODUCT_NOT_OFFERED, refused.refusal());
    }

    /** An unknown partner and days that run backwards are refused before anything is counted. */
    @Test
    void anUnknownPartnerAndBackwardDaysAreRefused() {
        var unknown = assertThrows(
                RefusalResponse.class, () -> catalog.findProduct(borrower.id(), UUID.randomUUID(), radios, null, null));
        assertEquals(FederationRefusal.LENDING_PARTNER_DOES_NOT_LEND, unknown.refusal());
        var backwards = assertThrows(
                RefusalResponse.class, () -> catalog.findProducts(borrower.id(), null, DAY, DAY.minusDays(1)));
        assertEquals(FederationRefusal.LENDING_WINDOW_BACKWARDS, backwards.refusal());
        assertThrows(RefusalResponse.class, () -> catalog.serveProducts(asking, null, DAY, DAY.minusDays(1)));
    }

    /** The borrowing station asks its partner and gets the partner's products with its name. */
    @Test
    void aPartnerOnThisInstanceIsAsked() {
        var list = catalog.findProducts(borrower.id(), null, null, null);

        assertNull(list.emptyReason());
        assertTrue(list.products().stream()
                .anyMatch(p -> p.product().inventoryId() == radios
                        && p.stationId().equals(lender.uid())
                        && p.stationName().equals(lender.name())));
        var detail = catalog.findProduct(borrower.id(), lender.uid(), games, null, null);
        assertEquals(
                List.of(catan),
                detail.product().pieces().stream().map(RemotePiece::itemId).toList());
        assertEquals(
                30,
                catalog.findProductDays(borrower.id(), lender.uid(), radios, "2030-06", null)
                        .days()
                        .size());
        var refused = assertThrows(
                RefusalResponse.class,
                () -> catalog.findProductDays(borrower.id(), lender.uid(), radios, "2030-13", null));
        assertEquals(FederationRefusal.LENDING_MONTH_NOT_A_MONTH, refused.refusal());
    }

    /** A partner's rank is held to what a search can produce, so no partner can push itself first. */
    @Test
    void aPartnersRankIsHeldToWhatASearchProduces() {
        assertEquals(LendingCatalogService.MAX_RANK, LendingCatalogService.trustedRank(1e9));
        assertEquals(0, LendingCatalogService.trustedRank(Double.NaN));
        assertEquals(0, LendingCatalogService.trustedRank(-5));
        assertEquals(1.2, LendingCatalogService.trustedRank(1.2));
    }

    /** A description is shown on a tile without its Markdown and cut at a word. */
    @Test
    void aTeaserIsPlainAndShort() {
        assertEquals("", LendingCatalogService.teaser(null));
        assertEquals("Mit Link", LendingCatalogService.teaser("Mit [Link](https://example.org)"));
        String teaser = LendingCatalogService.teaser("Wort ".repeat(100));
        assertTrue(teaser.length() <= LendingCatalogService.TEASER_LENGTH + 1);
        assertTrue(teaser.endsWith("…"));
    }

    private static RemoteKind kindOf(List<RemoteKind> kinds, int artId) {
        return kinds.stream()
                .filter(kind -> Integer.valueOf(artId).equals(kind.artId()))
                .findFirst()
                .orElseThrow();
    }
}
