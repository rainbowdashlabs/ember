/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Api;
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
import dev.chojo.ember.feature.protocol.route.RemoteTestProtocolRoutes;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
        federationService = new FederationService(federationRepo, stationRepo, TestStationKeys.store(), new Api());
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
        var item = service.createItem(sectionId, "Knows stop signs", "Stop sign check", 10.0, 0);
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
        assertTrue(service.updateItem(itemId, "Updated label", "Updated desc", 12.0, 1));
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
        service.toggleSectionDone(runId, member.id(), sectionId, member.id());
        var done = service.findDoneSections(runId, member.id());
        assertTrue(done.contains(sectionId));

        service.toggleSectionDone(runId, member.id(), sectionId, member.id());
        done = service.findDoneSections(runId, member.id());
        assertFalse(done.contains(sectionId));
    }

    @Test
    @Order(61)
    void toggleSectionDoneNonexistentMember() {
        service.toggleSectionDone(runId, 99999, sectionId, member.id());
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

    @Test
    @Order(70)
    void completeMember() {
        service.saveChecks(runId, member.id(), Map.of(itemId, true), member.id(), protocolId);
        assertTrue(service.completeMember(runId, member.id(), protocolId));
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
        assertEquals(Refusal.REMOTE_PROTOCOL_NOT_SHARED, refused.refusal());
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

    @Test
    @Order(220)
    void copyProtocol() {
        var srcProto = testProtocolRepo.createProtocol(stationB.id(), "CopySource", "copy desc", 75);
        var parentSec = testProtocolRepo.createSection(srcProto.id(), null, "ParentSection", "parent desc", 100, 50, 0);
        var childSec =
                testProtocolRepo.createSection(srcProto.id(), parentSec.id(), "ChildSection", "child desc", 50, 25, 1);
        testProtocolRepo.createItem(parentSec.id(), "ParentItem", "parent item", 10.0, 0);
        testProtocolRepo.createItem(childSec.id(), "ChildItem", "child item", 5.0, 0);

        var copied = service.copyProtocol(srcProto.id(), station.id());
        assertNotNull(copied);
        assertEquals("CopySource", copied.name());
        assertEquals(station.id(), copied.stationId());

        var copiedSections = service.findSections(copied.id());
        assertEquals(2, copiedSections.size());

        var copiedItems = service.findAllItemsByProtocol(copied.id());
        assertEquals(2, copiedItems.size());

        testProtocolRepo.deleteProtocol(srcProto.id(), stationB.id());
        testProtocolRepo.deleteProtocol(copied.id(), station.id());
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
}
