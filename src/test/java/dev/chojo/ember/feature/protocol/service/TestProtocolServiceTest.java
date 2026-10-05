/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.TestProtocolRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.FederationTestContracts;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import dev.chojo.ember.feature.protocol.route.RemoteTestProtocolRoutes;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestFederationServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProtocolServiceTest extends RepositoryTestBase {
    private static TestProtocolService service;
    private static FederationRepository federationRepo;
    private static FederationService federationService;
    private static FederationHttpClient httpClient;
    private static FederationTestTransport transport;
    private static Station station;
    private static Station stationB;
    private static Station stationC;
    private static Account account;
    private static StationMember member;
    private static int protocolId;
    private static int sectionId;
    private static int itemId;
    private static int runId;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        federationService = TestFederationServices.of(federationRepo, stationRepo);
        httpClient = mock(FederationHttpClient.class);
        when(httpClient.canSign(anyInt())).thenReturn(true);
        transport = new FederationTestTransport(httpClient, federationRepo, stationRepo);
        service = new TestProtocolService(
                testProtocolRepo,
                federationService,
                federationRepo,
                stationRepo,
                new FederationFanout(new TaskScheduler()),
                new FederationEntityResolver(federationRepo),
                transport.transport());
        transport.serve(service);
        station = stationRepo.create("ProtocolSvcStation");
        stationB = stationRepo.create("ProtocolSvcStationB");
        stationC = stationRepo.create("ProtocolSvcStationC");
        account = accountRepo.create("protocol-svc@test.com", "Protocol", "SvcTester");
        member = stationMemberRepo.create(station.id(), account.id());

        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                station.id(), stationB.id(), federationService.encodePublicKey(keyPair), null, null);
        int partnerIdAtoB = partner.id();

        var keyPairC = federationService.generateKeyPair();
        federationService.acceptInvite(
                station.id(),
                stationC.id(),
                federationService.encodePublicKey(keyPairC),
                "https://remote-proto.example.com",
                null);
        FederationTestContracts.storeCurrentContractOnRemotePartners(federationService, federationRepo, station.id());
    }

    @AfterAll
    static void cleanup() {
        for (var p : federationService.findPartners(station.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(stationB.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(stationC.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(station.id());
        stationRepo.delete(stationB.id());
        stationRepo.delete(stationC.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createProtocol() {
        var p = service.createProtocol(station.id(), "Driving Test", "Road safety", 75);
        assertNotNull(p);
        assertEquals("Driving Test", p.name());
        protocolId = p.id();
    }

    @Test
    @Order(2)
    void findProtocols() {
        var protocols = service.findProtocols(station.id());
        assertTrue(protocols.stream().anyMatch(p -> p.id() == protocolId));
    }

    @Test
    @Order(3)
    void findProtocol() {
        assertTrue(service.findProtocol(protocolId).isPresent());
        assertTrue(service.findProtocol(99999).isEmpty());
    }

    @Test
    @Order(4)
    void searchProtocols() {
        var results = service.searchProtocols(station.id(), "Driving");
        assertTrue(results.stream().anyMatch(p -> p.id() == protocolId));

        var empty = service.searchProtocols(station.id(), "xyzzy_xyz99999");
        assertTrue(empty.isEmpty());
    }

    @Test
    @Order(5)
    void updateProtocol() {
        assertTrue(service.updateProtocol(protocolId, "Updated Test", "Updated", 80));
    }

    @Test
    @Order(10)
    void createSection() {
        var section = service.createSection(protocolId, null, "Theory", "Theory section", 50, 30, 0);
        assertNotNull(section);
        assertEquals("Theory", section.name());
        sectionId = section.id();
    }

    @Test
    @Order(11)
    void findSections() {
        assertFalse(service.findSections(protocolId).isEmpty());
    }

    @Test
    @Order(12)
    void updateSection() {
        assertTrue(service.updateSection(sectionId, "Theory Updated", "Updated", 60, 40, 1));
    }

    @Test
    @Order(20)
    void createItem() {
        var item = service.createItem(sectionId, "Knows stop signs", "Stop sign check", 10.0, 0, false);
        assertNotNull(item);
        assertEquals("Knows stop signs", item.label());
        itemId = item.id();
    }

    @Test
    @Order(21)
    void findItems() {
        assertFalse(service.findItems(sectionId).isEmpty());
    }

    @Test
    @Order(22)
    void findAllItemsByProtocol() {
        var items = service.findAllItemsByProtocol(protocolId);
        assertTrue(items.stream().anyMatch(i -> i.id() == itemId));
    }

    @Test
    @Order(23)
    void updateItem() {
        assertTrue(service.updateItem(itemId, "Updated label", "Updated desc", 12.0, false, 1));
    }

    @Test
    @Order(30)
    void createRun() {
        var run = service.createRun(protocolId, station.id(), "Run 2026-01", LocalDate.of(2026, 1, 15), member.id());
        assertNotNull(run);
        assertEquals("Run 2026-01", run.name());
        runId = run.id();
    }

    @Test
    @Order(31)
    void findRuns() {
        assertTrue(service.findRuns(station.id()).stream().anyMatch(r -> r.id() == runId));
    }

    @Test
    @Order(32)
    void findRun() {
        assertTrue(service.findRun(runId).isPresent());
    }

    @Test
    @Order(33)
    void updateRun() {
        assertTrue(service.updateRun(runId, "Updated Run", LocalDate.of(2026, 2, 1)));
    }

    @Test
    @Order(40)
    void addRunMember() {
        var rm = service.addRunMember(runId, member.id());
        assertNotNull(rm);
    }

    @Test
    @Order(41)
    void addRunMembers() {
        service.addRunMembers(runId, List.of(member.id()));
        var members = service.findRunMembers(runId);
        assertFalse(members.isEmpty());
    }

    @Test
    @Order(42)
    void findRunMember() {
        assertTrue(service.findRunMember(runId, member.id()).isPresent());
        assertTrue(service.findRunMember(runId, 99999).isEmpty());
    }

    @Test
    @Order(43)
    void lockAndUnlock() {
        assertTrue(service.lockMember(runId, member.id(), member.id()));
        assertTrue(service.unlockMember(runId, member.id()));
    }

    @Test
    @Order(44)
    void lockNonexistentMember() {
        assertFalse(service.lockMember(runId, 99999, member.id()));
    }

    @Test
    @Order(50)
    void saveChecks() {
        service.saveChecks(runId, member.id(), Map.of(itemId, true), member.id(), protocolId);
    }

    @Test
    @Order(51)
    void saveChecksForNonexistentMember() {
        service.saveChecks(runId, 99999, Map.of(itemId, true), member.id(), protocolId);
    }

    @Test
    @Order(52)
    void findChecks() {
        var checks = service.findChecks(runId, member.id());
        assertFalse(checks.isEmpty());
    }

    @Test
    @Order(53)
    void findChecksForNonexistentMember() {
        assertTrue(service.findChecks(runId, 99999).isEmpty());
    }

    @Test
    @Order(60)
    void toggleSectionDone() {
        service.toggleSectionDone(runId, member.id(), protocolId, sectionId, member.id());
        var done = service.findDoneSections(runId, member.id());
        assertTrue(done.contains(sectionId));

        service.toggleSectionDone(runId, member.id(), protocolId, sectionId, member.id());
        done = service.findDoneSections(runId, member.id());
        assertFalse(done.contains(sectionId));
    }

    @Test
    @Order(61)
    void toggleSectionDoneNonexistentMember() {
        service.toggleSectionDone(runId, 99999, protocolId, sectionId, member.id());
    }

    @Test
    @Order(62)
    void findDoneSectionsNonexistentMember() {
        assertTrue(service.findDoneSections(runId, 99999).isEmpty());
    }

    @Test
    @Order(63)
    void countDoneSections() {
        var rm = testProtocolRepo.findRunMember(runId, member.id()).orElseThrow();
        assertEquals(0, service.countDoneSections(rm.id()));
    }

    /** Ticks alone do not finish an examination: every top-level section has to be marked as checked. */
    @Test
    @Order(70)
    void completeMember() {
        service.saveChecks(runId, member.id(), Map.of(itemId, true), member.id(), protocolId);
        var refused = assertThrows(RefusalResponse.class, () -> service.completeMember(runId, member.id(), protocolId));
        assertEquals(TestProtocolRefusal.PROTOCOL_MEMBER_SECTIONS_OPEN, refused.refusal());

        var done = service.findDoneSections(runId, member.id());
        service.findSections(protocolId).stream()
                .filter(section -> section.parentId() == null && !done.contains(section.id()))
                .forEach(section ->
                        service.toggleSectionDone(runId, member.id(), protocolId, section.id(), member.id()));
        assertTrue(service.completeMember(runId, member.id(), protocolId));
    }

    /** The mark that leaves no section open finishes the examination, with the score of its ticks. */
    @Test
    @Order(65)
    void markingTheLastSectionFinishesTheExamination() {
        int sheet = service.createProtocol(station.id(), "Sheet", "", 50).id();
        int knots =
                service.createSection(sheet, null, "Knots", "", null, null, 0).id();
        int radio =
                service.createSection(sheet, null, "Radio", "", null, null, 1).id();
        int knot = service.createItem(knots, "Bowline", "", 4.0, 0, false).id();
        service.createItem(radio, "Call sign", "", 6.0, 0, false);
        int run = service.createRun(sheet, station.id(), "Autumn", LocalDate.of(2026, 9, 1), member.id())
                .id();
        service.addRunMember(run, member.id());
        service.saveChecks(run, member.id(), Map.of(knot, true), member.id(), sheet);

        service.toggleSectionDone(run, member.id(), sheet, knots, member.id());
        assertFalse(service.findRunMember(run, member.id()).orElseThrow().completed());

        service.toggleSectionDone(run, member.id(), sheet, radio, member.id());
        var finished = service.findRunMember(run, member.id()).orElseThrow();
        assertTrue(finished.completed());
        assertEquals(4.0, finished.totalScore());
    }

    @Test
    @Order(71)
    void completeMemberNonexistent() {
        assertFalse(service.completeMember(runId, 99999, protocolId));
    }

    @Test
    @Order(72)
    void closeRun() {
        assertTrue(service.closeRun(runId));
    }

    @Test
    @Order(90)
    void deleteItem() {
        assertTrue(service.deleteItem(itemId));
    }

    @Test
    @Order(91)
    void deleteSection() {
        assertTrue(service.deleteSection(sectionId));
    }

    @Test
    @Order(92)
    void deleteRun() {
        assertTrue(service.deleteRun(runId, station.id()));
    }

    @Test
    @Order(99)
    void deleteProtocol() {
        assertTrue(service.deleteProtocol(protocolId, station.id()));
    }

    @Test
    @Order(200)
    void browseSharedProtocolsWithShare() {
        var fedProto = testProtocolRepo.createProtocol(stationB.id(), "FedProtocol", "shared desc", 70);
        federationRepo.createProtocolShare(stationB.id(), fedProto.id(), ShareScope.ALL_PARTNERS);
        var shared = service.browseSharedProtocols(station.id());
        assertTrue(shared.stream().anyMatch(s -> s.name().equals("FedProtocol")));
        testProtocolRepo.deleteProtocol(fedProto.id(), stationB.id());
    }

    /** Sharing what is already shared, or unsharing what is not, changes nothing. */
    @Test
    @Order(200)
    void sharingIsAStateNotACount() {
        var proto = testProtocolRepo.createProtocol(stationB.id(), "Geteilt", "", null);

        service.setShared(stationB.id(), proto.id(), true);
        service.setShared(stationB.id(), proto.id(), true);
        assertEquals(List.of(proto.id()), service.findSharedProtocolIds(stationB.id()));
        assertEquals(1, federationRepo.findProtocolShares(stationB.id()).size());

        service.setShared(stationB.id(), proto.id(), false);
        service.setShared(stationB.id(), proto.id(), false);
        assertTrue(service.findSharedProtocolIds(stationB.id()).isEmpty());

        testProtocolRepo.deleteProtocol(proto.id(), stationB.id());
    }

    @Test
    @Order(201)
    void browseSharedProtocolsEmptyNoShares() {
        var shared = service.browseSharedProtocols(station.id());
        assertTrue(shared.isEmpty());
    }

    @Test
    @Order(202)
    void browseSharedProtocolViewsResolvesStationName() {
        var fedProto = testProtocolRepo.createProtocol(stationB.id(), "ViewProtocol", "view desc", 70);
        federationRepo.createProtocolShare(stationB.id(), fedProto.id(), ShareScope.ALL_PARTNERS);
        var views = service.browseSharedProtocolViews(station.id());
        var view = views.stream()
                .filter(v -> v.name().equals("ViewProtocol"))
                .findFirst()
                .orElseThrow();
        assertEquals("view desc", view.description());
        assertNotNull(view.stationName());
        assertFalse(view.stationName().isBlank());
        testProtocolRepo.deleteProtocol(fedProto.id(), stationB.id());
    }

    @Test
    @Order(210)
    void getFederatedProtocolLocal() {
        var fedProto = testProtocolRepo.createProtocol(stationB.id(), "FedDetailProto", "detail desc", 80);
        var sec = testProtocolRepo.createSection(fedProto.id(), null, "FedSection", "sec desc", 100, 50, 0);
        testProtocolRepo.createItem(sec.id(), "FedItem", "item desc", 10.0, 0);
        var share = federationRepo.createProtocolShare(stationB.id(), fedProto.id(), ShareScope.ALL_PARTNERS);
        var result = service.getFederatedProtocol(station.id(), stationB.uid(), fedProto.id());
        assertNotNull(result);
        assertNotNull(result.protocol());
        assertEquals(1, result.sections().size());
        assertEquals(1, result.items().size());
        var asking = federationRepo
                .findPartnerByStationAndRemoteUid(station.id(), stationB.uid())
                .orElseThrow();
        transport.assertParity(
                asking,
                RemoteTestProtocolRoutes.GET_PROTOCOL.at(fedProto.id()),
                null,
                RemoteTestProtocolRoutes.RemoteProtocolDetail.class);
        transport.assertParity(
                asking,
                RemoteTestProtocolRoutes.BROWSE_PROTOCOLS.at(),
                null,
                RemoteTestProtocolRoutes.RemoteProtocolSummary.class);
        federationRepo.deleteProtocolShare(share.id(), stationB.id());
        testProtocolRepo.deleteProtocol(fedProto.id(), stationB.id());
    }

    /**
     * A partner on this instance used to read any protocol of the station it is paired with, shared
     * or not. It is refused now, as a partner on another instance always was.
     */
    @Test
    @Order(212)
    void anUnsharedProtocolOfAPartnerHereIsRefused() {
        var unshared = testProtocolRepo.createProtocol(stationB.id(), "Unshared", "never shared", 60);
        var refused = assertThrows(
                RefusalResponse.class, () -> service.getFederatedProtocol(station.id(), stationB.uid(), unshared.id()));
        assertEquals(TestProtocolRefusal.REMOTE_PROTOCOL_NOT_SHARED, refused.refusal());
        testProtocolRepo.deleteProtocol(unshared.id(), stationB.id());
    }

    /** A share naming another station's protocol shows nothing at a partner on this instance either. */
    @Test
    @Order(213)
    void aShareNamingAnotherStationsProtocolShowsNothing() {
        var foreign = testProtocolRepo.createProtocol(station.id(), "Foreign", "the asker's own", 60);
        var share = federationRepo.createProtocolShare(stationB.id(), foreign.id(), ShareScope.ALL_PARTNERS);
        assertTrue(service.browseSharedProtocols(station.id()).stream().noneMatch(p -> p.id() == foreign.id()));
        federationRepo.deleteProtocolShare(share.id(), stationB.id());
        testProtocolRepo.deleteProtocol(foreign.id(), station.id());
    }

    /**
     * The partner may or may not exist due to cross-test interference; either way the call must reject
     * access, for wrong ownership or an unknown partner.
     */
    @Test
    @Order(211)
    void getFederatedProtocolWrongStation() {
        var localProto = testProtocolRepo.createProtocol(station.id(), "LocalOnly", "local", 60);

        assertThrows(
                Exception.class, () -> service.getFederatedProtocol(station.id(), stationB.uid(), localProto.id()));

        testProtocolRepo.deleteProtocol(localProto.id(), station.id());
    }

    /** A shared protocol is copied as the partner serves it, every level under its own parent. */
    @Test
    @Order(220)
    void copyProtocol() {
        var srcProto = testProtocolRepo.createProtocol(stationB.id(), "CopySource", "copy desc", 75);
        var parentSec = testProtocolRepo.createSection(srcProto.id(), null, "ParentSection", "parent desc", 100, 50, 0);
        var childSec =
                testProtocolRepo.createSection(srcProto.id(), parentSec.id(), "ChildSection", "child desc", 50, 25, 1);
        var grandchildSec =
                testProtocolRepo.createSection(srcProto.id(), childSec.id(), "GrandchildSection", "", null, null, 0);
        testProtocolRepo.createItem(parentSec.id(), "ParentItem", "parent item", 10.0, 0);
        testProtocolRepo.createItem(childSec.id(), "ChildItem", "child item", 5.0, 0);
        testProtocolRepo.createItem(grandchildSec.id(), "GrandchildItem", "", 2.0, 0, true);
        var share = federationRepo.createProtocolShare(stationB.id(), srcProto.id(), ShareScope.ALL_PARTNERS);

        var copied = service.copyFederatedProtocol(station.id(), stationB.uid(), srcProto.id());
        assertEquals("CopySource", copied.name());
        assertEquals(station.id(), copied.stationId());

        var copiedSections = service.findSections(copied.id());
        assertEquals(3, copiedSections.size());
        assertEquals(
                sectionNamed(copiedSections, "ChildSection").id(),
                sectionNamed(copiedSections, "GrandchildSection").parentId());
        var copiedItems = service.findAllItemsByProtocol(copied.id());
        assertEquals(3, copiedItems.size());
        assertEquals(
                List.of("GrandchildItem"),
                copiedItems.stream()
                        .filter(TestProtocolItem::bonus)
                        .map(TestProtocolItem::label)
                        .toList());

        federationRepo.deleteProtocolShare(share.id(), stationB.id());
        testProtocolRepo.deleteProtocol(srcProto.id(), stationB.id());
        testProtocolRepo.deleteProtocol(copied.id(), station.id());
    }

    private static TestProtocolSection sectionNamed(List<TestProtocolSection> sections, String name) {
        return sections.stream()
                .filter(section -> section.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /** A protocol the partner does not share cannot be copied by its number, and nothing is created. */
    @Test
    @Order(221)
    void anUnsharedProtocolIsNotCopied() {
        var unshared = testProtocolRepo.createProtocol(stationB.id(), "NotShared", "", 60);
        int before = service.findProtocols(station.id()).size();

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.copyFederatedProtocol(station.id(), stationB.uid(), unshared.id()));
        assertEquals(TestProtocolRefusal.REMOTE_PROTOCOL_NOT_SHARED, refused.refusal());
        assertEquals(before, service.findProtocols(station.id()).size());

        testProtocolRepo.deleteProtocol(unshared.id(), stationB.id());
    }

    @Test
    @Order(230)
    void sharedProtocolItemRecord() {
        var item = new TestProtocolService.SharedProtocolItem(1, "Test Protocol", "A description", 42, 7);
        assertEquals(1, item.id());
        assertEquals("Test Protocol", item.name());
        assertEquals("A description", item.description());
        assertEquals(42, item.sourceStationId());
        assertEquals(7, item.partnerId());
    }

    @Test
    @Order(240)
    void browseSharedProtocolsViaHttp() {
        when(httpClient.getList(
                        eq("https://remote-proto.example.com"),
                        pathIs("/remote/protocols"),
                        any(),
                        eq(station.id()),
                        eq(RemoteTestProtocolRoutes.RemoteProtocolSummary.class)))
                .thenReturn(List.of(new RemoteTestProtocolRoutes.RemoteProtocolSummary(
                        99, "RemoteProto", "remote desc", "2026-01-01")));
        var items = service.browseSharedProtocols(station.id());
        assertTrue(items.stream().anyMatch(i -> i.name().equals("RemoteProto")));
    }

    @Test
    @Order(241)
    void getFederatedProtocolRemote() {
        var remoteResult = new RemoteTestProtocolRoutes.RemoteProtocolDetail(
                new TestProtocol(77, 0, "RemoteProto", "desc", 80, null, null), List.of(), List.of());
        when(httpClient.get(
                        eq("https://remote-proto.example.com"),
                        pathIs("/remote/protocols/77"),
                        any(),
                        eq(station.id()),
                        any()))
                .thenReturn(remoteResult);
        var result = service.getFederatedProtocol(station.id(), stationC.uid(), 77);
        assertNotNull(result);
        assertNotNull(result.protocol());
    }

    /** Sections are sorted per level: sorting the top level leaves the sections inside one alone. */
    @Test
    @Order(300)
    void sectionsAreSortedPerLevel() {
        int sorted =
                service.createProtocol(station.id(), "Sortierung", "", null).id();
        int first =
                service.createSection(sorted, null, "Erster", "", null, null, 0).id();
        int second = service.createSection(sorted, null, "Zweiter", "", null, null, 1)
                .id();
        int third = service.createSection(sorted, null, "Dritter", "", null, null, 2)
                .id();
        int innerA = service.createSection(sorted, first, "Innen A", "", null, null, 0)
                .id();
        int innerB = service.createSection(sorted, first, "Innen B", "", null, null, 1)
                .id();

        service.reorderSections(sorted, List.of(third, first, second));
        service.reorderSections(sorted, List.of(innerB, innerA));

        assertEquals(List.of(third, first, second), idsAt(sorted, null));
        assertEquals(List.of(innerB, innerA), idsAt(sorted, first));
    }

    /** An order that leaves one out, names one twice or reaches into another level moves nothing. */
    @Test
    @Order(301)
    void anOrderThatIsNotTheWholeLevelMovesNothing() {
        int sorted =
                service.createProtocol(station.id(), "Unvollständig", "", null).id();
        int first =
                service.createSection(sorted, null, "Erster", "", null, null, 0).id();
        int second = service.createSection(sorted, null, "Zweiter", "", null, null, 1)
                .id();
        int inner =
                service.createSection(sorted, first, "Innen", "", null, null, 0).id();

        for (var order :
                List.of(List.of(second), List.of(second, second), List.of(second, first, inner), List.<Integer>of())) {
            var refused = assertThrows(RefusalResponse.class, () -> service.reorderSections(sorted, order));
            assertEquals(TestProtocolRefusal.PROTOCOL_ORDER_OUT_OF_DATE, refused.refusal());
        }
        assertEquals(List.of(first, second), idsAt(sorted, null));
    }

    /** Points are sorted within their section, and only all of them at once. */
    @Test
    @Order(302)
    void pointsAreSortedWithinTheirSection() {
        int sorted = service.createProtocol(station.id(), "Punkte", "", null).id();
        int section = service.createSection(sorted, null, "Abschnitt", "", null, null, 0)
                .id();
        int a = service.createItem(section, "A", "", 1, 0, false).id();
        int b = service.createItem(section, "B", "", 1, 1, false).id();
        int c = service.createItem(section, "C", "", 1, 2, false).id();

        service.reorderItems(section, List.of(c, a, b));
        var refused = assertThrows(RefusalResponse.class, () -> service.reorderItems(section, List.of(a, b)));

        assertEquals(TestProtocolRefusal.PROTOCOL_ORDER_OUT_OF_DATE, refused.refusal());
        assertEquals(
                List.of(c, a, b),
                service.findItems(section).stream().map(item -> item.id()).toList());
    }

    /** Changing a section or a point without naming a place keeps the place it was sorted to. */
    @Test
    @Order(303)
    void aChangeKeepsThePlace() {
        int sorted = service.createProtocol(station.id(), "Stelle", "", null).id();
        int first =
                service.createSection(sorted, null, "Erster", "", null, null, 0).id();
        int second = service.createSection(sorted, null, "Zweiter", "", null, null, 1)
                .id();
        int a = service.createItem(second, "A", "", 1, 3, false).id();
        int b = service.createItem(second, "B", "", 1, 4, false).id();

        service.updateSection(second, "Abschnitt vorne im Alphabet", "", null, null, null);
        service.updateItem(b, "B, umbenannt", "", 2, false, null);

        assertEquals(List.of(first, second), idsAt(sorted, null));
        assertEquals(
                List.of(a, b),
                service.findItems(second).stream().map(item -> item.id()).toList());
    }

    /**
     * A top-level section moves under a section two levels down, goes to the end of its new level, and
     * the top level it left closes up behind it.
     */
    @Test
    @Order(304)
    void aSectionMovesUnderAnotherAtAnyDepth() {
        int moving = service.createProtocol(station.id(), "Umzug", "", null).id();
        int a = service.createSection(moving, null, "A", "", null, null, 0).id();
        int b = service.createSection(moving, null, "B", "", null, null, 1).id();
        int c = service.createSection(moving, null, "C", "", null, null, 2).id();
        int deep = service.createSection(moving, c, "C1", "", null, null, 0).id();
        int deeper =
                service.createSection(moving, deep, "C1a", "", null, null, 0).id();

        service.moveSection(a, deeper);

        assertEquals(List.of(b, c), idsAt(moving, null));
        assertEquals(List.of(a), idsAt(moving, deeper));
        assertEquals(
                List.of(0, 1),
                service.findSections(moving).stream()
                        .filter(section -> section.parentId() == null)
                        .map(TestProtocolSection::position)
                        .sorted()
                        .toList());

        service.moveSection(deeper, null);
        assertEquals(List.of(b, c, deeper), idsAt(moving, null));
        assertEquals(List.of(a), idsAt(moving, deeper));
    }

    /** A section cannot go into itself, one of its own subsections, or a section of another protocol. */
    @Test
    @Order(305)
    void aSectionDoesNotMoveIntoItselfOrElsewhere() {
        int moving = service.createProtocol(station.id(), "Kreis", "", null).id();
        int other = service.createProtocol(station.id(), "Anderer", "", null).id();
        int top = service.createSection(moving, null, "Oben", "", null, null, 0).id();
        int inner =
                service.createSection(moving, top, "Innen", "", null, null, 0).id();
        int foreign =
                service.createSection(other, null, "Fremd", "", null, null, 0).id();

        for (int target : List.of(top, inner)) {
            var refused = assertThrows(RefusalResponse.class, () -> service.moveSection(top, target));
            assertEquals(TestProtocolRefusal.PROTOCOL_SECTION_MOVED_INTO_ITSELF, refused.refusal());
        }
        var elsewhere = assertThrows(RefusalResponse.class, () -> service.moveSection(top, foreign));
        assertEquals(TestProtocolRefusal.PROTOCOL_SECTION_PARENT_ELSEWHERE_ON_MOVE, elsewhere.refusal());
        var created = assertThrows(
                RefusalResponse.class, () -> service.createSection(moving, foreign, "X", "", null, null, 0));
        assertEquals(TestProtocolRefusal.PROTOCOL_SECTION_PARENT_ELSEWHERE_ON_CREATE, created.refusal());
        assertEquals(List.of(top), idsAt(moving, null));
    }

    private static List<Integer> idsAt(int protocol, Integer parent) {
        return service.findSections(protocol).stream()
                .filter(section -> Objects.equals(section.parentId(), parent))
                .map(section -> section.id())
                .toList();
    }
}
