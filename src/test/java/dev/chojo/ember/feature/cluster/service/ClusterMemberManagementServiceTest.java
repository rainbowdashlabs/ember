/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberDocumentService;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * What somebody looking after every station of a cluster may and may not do.
 *
 * <p>The two refusals are the point of the class, so most of this is about them.
 */
class ClusterMemberManagementServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static ClusterMemberManagementService service;

    @BeforeAll
    static void setup() {
        service = new ClusterMemberManagementService(
                stationMemberRepo,
                stationRepo,
                profileFieldService,
                new StationMemberInviteService(
                        stationMemberRepo,
                        memberGroupRepo,
                        new AccountInviteService(accountRepo, mock(AuthService.class))),
                memberDocumentRepo,
                documentService());
    }

    /** A document store backed by a local folder, which is all these stories need of one. */
    private static MemberDocumentService documentService() {
        var backend = new LocalStorageBackend();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        return new MemberDocumentService(memberDocumentRepo, storage, new ImageVariantService(storage), stationRepo);
    }

    private int freshCluster() {
        return clusterService
                .create("Kreisverband Leute " + NAMES.incrementAndGet(), null)
                .id();
    }

    private Account freshAccount() {
        int n = NAMES.incrementAndGet();
        return accountRepo.create("clustermanage" + n + "@test.com", "Ver", "Waltung" + n);
    }

    /** A station of the cluster with one ordinary member in it. */
    private record Peopled(Station station, StationMember member, Account account) {}

    private Peopled stationWithMember(int clusterId) {
        var station = clusterService.createStation(clusterId, "Wache Leute " + NAMES.incrementAndGet());
        var account = freshAccount();
        var member = stationMemberRepo.create(station.id(), account.id());
        return new Peopled(station, member, account);
    }

    @Test
    void everybodyAtEveryStationOfTheClusterIsFound() {
        int clusterId = freshCluster();
        var first = stationWithMember(clusterId);
        var second = stationWithMember(clusterId);

        var page = service.search(clusterId, null, null, null, false, 0, 50);

        assertEquals(2, page.total());
        assertTrue(page.members().stream()
                .anyMatch(row -> row.id() == first.member().id()));
        assertTrue(page.members().stream()
                .anyMatch(row -> row.id() == second.member().id()));
        assertTrue(
                page.members().stream().allMatch(row -> row.stationName().startsWith("Wache Leute")),
                "each row says which station the person is at");
    }

    @Test
    void aSearchNarrowsToOneStation() {
        int clusterId = freshCluster();
        var first = stationWithMember(clusterId);
        stationWithMember(clusterId);

        var page = service.search(clusterId, null, first.station().id(), null, false, 0, 50);

        assertEquals(1, page.total());
        assertEquals(first.member().id(), page.members().getFirst().id());
    }

    @Test
    void aSearchNarrowsByNameAndByType() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);

        assertEquals(
                1,
                service.search(clusterId, peopled.account().firstName(), null, null, false, 0, 50)
                        .total());
        assertEquals(
                0,
                service.search(clusterId, "niemand-mit-diesem-namen", null, null, false, 0, 50)
                        .total());
        assertEquals(
                1,
                service.search(clusterId, null, null, peopled.member().userType(), false, 0, 50)
                        .total());
    }

    @Test
    void peopleWhoHaveLeftAreOutOfTheWayUnlessAskedFor() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        stationMemberRepo.setFormer(peopled.member().id(), true);

        assertEquals(
                0, service.search(clusterId, null, null, null, false, 0, 50).total());
        assertEquals(1, service.search(clusterId, null, null, null, true, 0, 50).total());
    }

    @Test
    void aClusterSeesNobodyFromAnotherClustersStations() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        stationWithMember(otherClusterId);

        assertEquals(
                0, service.search(clusterId, null, null, null, false, 0, 50).total());
    }

    @Test
    void aManagerCannotEditTheirOwnMembership() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int ownAccountId = peopled.account().id();

        assertThrows(
                ForbiddenResponse.class,
                () -> service.setUserType(clusterId, peopled.member().id(), StationUserType.MANAGER, ownAccountId));
        assertThrows(
                ForbiddenResponse.class,
                () -> service.setPermissions(
                        clusterId,
                        peopled.member().id(),
                        Set.of(StationPermission.STATION_ADMINISTRATOR),
                        ownAccountId));
        assertThrows(
                ForbiddenResponse.class,
                () -> service.archive(clusterId, peopled.member().id(), ownAccountId));
    }

    @Test
    void aManagerCannotEditAStationsOwner() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        stationRepo.setOwner(peopled.station().id(), peopled.member().id());
        int strangerAccountId = freshAccount().id();

        assertThrows(
                ForbiddenResponse.class,
                () -> service.setUserType(
                        clusterId, peopled.member().id(), StationUserType.MANAGER, strangerAccountId));
        assertThrows(
                ForbiddenResponse.class,
                () -> service.archive(clusterId, peopled.member().id(), strangerAccountId));
    }

    @Test
    void anybodyElseCanBeEditedWithNoCeiling() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int strangerAccountId = freshAccount().id();

        service.setUserType(clusterId, peopled.member().id(), StationUserType.MANAGER, strangerAccountId);
        assertEquals(
                StationUserType.MANAGER,
                stationMemberRepo.findById(peopled.member().id()).orElseThrow().userType());

        // Up to and including the top of the station's own ladder, which is deliberate
        service.setPermissions(
                clusterId, peopled.member().id(), Set.of(StationPermission.STATION_ADMINISTRATOR), strangerAccountId);

        service.archive(clusterId, peopled.member().id(), strangerAccountId);
        assertTrue(
                stationMemberRepo.findById(peopled.member().id()).orElseThrow().former());
    }

    @Test
    void aMemberAtSomebodyElsesStationIsNotFoundAtAll() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var elsewhere = stationWithMember(otherClusterId);
        int strangerAccountId = freshAccount().id();

        assertThrows(
                NotFoundResponse.class,
                () -> service.setUserType(
                        clusterId, elsewhere.member().id(), StationUserType.MANAGER, strangerAccountId));
    }

    @Test
    void aProfileCarriesTheStationsQuestionsAndTheClustersTogether() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);

        profileFieldService.create(
                peopled.station().id(),
                "Spindnummer",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER);
        clusterProfileFieldService.create(
                clusterId,
                "Mitgliedsnummer",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);

        var profile = service.getMemberProfile(clusterId, peopled.member().id());

        assertEquals(peopled.member().id(), profile.member().id());
        assertTrue(
                profile.fields().stream()
                        .anyMatch(f -> "Spindnummer".equals(f.name()) && f.origin() == FieldOrigin.STATION),
                "the station's own question is there and says so");
        assertTrue(
                profile.fields().stream()
                        .anyMatch(f -> "Mitgliedsnummer".equals(f.name()) && f.origin() == FieldOrigin.CLUSTER),
                "the cluster's question is there and says so");
    }

    @Test
    void aClusterAnswersItsOwnQuestionEvenWhenTheStationMayNot() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int strangerAccountId = freshAccount().id();

        var field = clusterProfileFieldService.create(
                clusterId,
                "Mitgliedsnummer",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);

        service.updateMemberProfile(
                clusterId,
                peopled.member().id(),
                List.of(new FieldValueEntry(field.id(), "\"4711\"", FieldOrigin.CLUSTER)),
                strangerAccountId,
                peopled.member().id());

        var profile = service.getMemberProfile(clusterId, peopled.member().id());
        assertTrue(
                profile.values().stream().anyMatch(v -> v.fieldId() == field.id() && v.origin() == FieldOrigin.CLUSTER),
                "the answer is recorded against the cluster's own question");
    }

    @Test
    void theTwoRefusalsHoldForAnsweringToo() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int ownAccountId = peopled.account().id();

        assertThrows(
                ForbiddenResponse.class,
                () -> service.updateMemberProfile(
                        clusterId,
                        peopled.member().id(),
                        List.of(),
                        ownAccountId,
                        peopled.member().id()));

        stationRepo.setOwner(peopled.station().id(), peopled.member().id());
        int strangerAccountId = freshAccount().id();
        assertThrows(
                ForbiddenResponse.class,
                () -> service.updateMemberProfile(
                        clusterId,
                        peopled.member().id(),
                        List.of(),
                        strangerAccountId,
                        peopled.member().id()));
    }

    @Test
    void theStationsAManagerMayActInAreTheClustersOwn() {
        var cluster = clusterService.create("Kreisverband Reichweite " + NAMES.incrementAndGet(), null);
        var peopled = stationWithMember(cluster.id());

        var stations = service.reachableStations(cluster.id());

        assertEquals(1, stations.size());
        assertEquals(peopled.station().id(), stations.getFirst().id());
        assertFalse(
                stations.stream().anyMatch(station -> station.id() == cluster.homeStationId()),
                "the cluster's own shell is not one of them");
    }

    @Test
    void somebodyIsTakenOnAtTheStationTheyWereNamedFor() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Zugang " + NAMES.incrementAndGet());
        int n = NAMES.incrementAndGet();

        var made = service.createMember(
                clusterId, station.uid(), "Neu", "Zugang" + n, "zugang" + n + "@test.com", StationUserType.TEAM);

        var member = stationMemberRepo.findById(made.memberId()).orElseThrow();
        assertEquals(station.id(), member.stationId(), "they belong to the station they were named for");
        assertEquals(StationUserType.TEAM, member.userType());
        assertTrue(
                service.search(clusterId, "Zugang" + n, null, null, false, 0, 50)
                                .total()
                        > 0,
                "and the association's list finds them");
    }

    @Test
    void somebodyWithNoAddressIsStillTakenOn() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Ohne " + NAMES.incrementAndGet());
        int n = NAMES.incrementAndGet();

        var made = service.createMember(clusterId, station.uid(), "Ohne", "Adresse" + n, null, StationUserType.MEMBER);

        assertTrue(made.email().endsWith(".local"), "an address nobody can receive mail at stands in for one");
        assertEquals(
                station.id(),
                stationMemberRepo.findById(made.memberId()).orElseThrow().stationId());
    }

    @Test
    void aDocumentFiledFromTheAssociationBelongsToTheStationHoldingThePerson() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);

        var filed = service.fileDocument(
                clusterId,
                peopled.member().id(),
                "Einverständnis",
                "einverstaendnis.txt",
                "text/plain",
                "Unterschrieben".getBytes(),
                null);

        assertEquals(
                peopled.station().id(), filed.stationId(), "it stays with the station, which is where the person is");
        assertEquals("Unterschrieben", new String(service.readDocument(filed)), "and it can be read back from here");
        assertTrue(service.documentsOf(clusterId, peopled.member().id()).stream()
                .anyMatch(document -> document.id() == filed.id()));
    }

    @Test
    void anAssociationReadsNothingFiledAtSomebodyElsesStation() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var theirs = stationWithMember(otherClusterId);

        var filed = service.fileDocument(
                otherClusterId, theirs.member().id(), "Fremd", "fremd.txt", "text/plain", "Geheim".getBytes(), null);

        assertThrows(
                NotFoundResponse.class,
                () -> service.documentsOf(clusterId, theirs.member().id()),
                "somebody at another association's station is nobody here");
        assertThrows(
                NotFoundResponse.class,
                () -> service.requireDocumentOfCluster(clusterId, filed.id()),
                "and neither is what is filed about them");
    }

    @Test
    void anAssociationTakesNobodyOnAtSomebodyElsesStation() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var theirs = clusterService.createStation(otherClusterId, "Wache Fremd " + NAMES.incrementAndGet());

        assertThrows(
                NotFoundResponse.class,
                () -> service.createMember(
                        clusterId, theirs.uid(), "Neu", "Fremd", "fremd@test.com", StationUserType.MEMBER),
                "a station answering to somebody else is not one of this association's");
    }
}
