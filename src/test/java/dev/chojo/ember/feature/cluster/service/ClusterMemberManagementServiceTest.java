/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.FormerMemberService;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.members.service.UserTypeChangeService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                        newGroupMemberships(),
                        new AccountInviteService(accountRepo, mock(AuthService.class))),
                new UserTypeChangeService(stationMemberRepo, newGroupMemberships()),
                memberDocumentRepo,
                documentService(),
                new FormerMemberService(
                        stationMemberRepo,
                        accountRepo,
                        inventoryRepo,
                        itemMovementService,
                        memberGroupRepo,
                        userTagRepo,
                        attendanceRepo,
                        profileFieldRepo,
                        documentService(),
                        selfCheckService));
    }

    /** A document store backed by a local folder, which is all these stories need of one. */
    private static DocumentService documentService() {
        var backend = localStorage();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        return new DocumentService(memberDocumentRepo, storage, new ImageVariants(storage), stationRepo);
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
                RefusalResponse.class,
                () -> service.setUserType(clusterId, peopled.member().id(), StationUserType.MANAGER, ownAccountId));
        assertThrows(
                RefusalResponse.class,
                () -> service.setPermissions(
                        clusterId,
                        peopled.member().id(),
                        Set.of(StationPermission.STATION_ADMINISTRATOR),
                        ownAccountId));
        assertThrows(
                RefusalResponse.class,
                () -> service.archive(clusterId, peopled.member().id(), ownAccountId));
    }

    @Test
    void aManagerCannotEditAStationsOwner() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        stationRepo.setOwner(peopled.station().id(), peopled.member().id());
        int strangerAccountId = freshAccount().id();

        assertThrows(
                RefusalResponse.class,
                () -> service.setUserType(
                        clusterId, peopled.member().id(), StationUserType.MANAGER, strangerAccountId));
        assertThrows(
                RefusalResponse.class,
                () -> service.archive(clusterId, peopled.member().id(), strangerAccountId));
    }

    /** A cluster changing somebody's type takes them out of the station's groups that do not take it. */
    @Test
    void aTypeChangeLeavesTheGroupsBoundToOtherTypes() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int memberId = peopled.member().id();
        var children = memberGroupRepo.create(peopled.member().stationId(), "Kinder");
        memberGroupRepo.replaceUserTypes(children.id(), List.of(StationUserType.MEMBER));
        stationMemberRepo.setUserType(memberId, StationUserType.MEMBER);
        memberGroupRepo.addMember(children.id(), memberId);

        service.setUserType(
                clusterId, memberId, StationUserType.TEAM, freshAccount().id());

        assertTrue(memberGroupRepo.findMembers(children.id()).isEmpty());
    }

    /** The missing ceiling is deliberate: it reaches up to and including the top of the station's own ladder. */
    @Test
    void anybodyElseCanBeEditedWithNoCeiling() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int strangerAccountId = freshAccount().id();

        service.setUserType(clusterId, peopled.member().id(), StationUserType.MANAGER, strangerAccountId);
        assertEquals(
                StationUserType.MANAGER,
                stationMemberRepo.findById(peopled.member().id()).orElseThrow().userType());

        service.setPermissions(
                clusterId, peopled.member().id(), Set.of(StationPermission.STATION_ADMINISTRATOR), strangerAccountId);
        assertTrue(stationMemberRepo.findPermissions(peopled.member().id()).stream()
                .anyMatch(permission -> permission.permission() == StationPermission.STATION_ADMINISTRATOR));
    }

    /** Archiving means the same whoever presses the button: the station's whole leaving routine runs. */
    @Test
    void anArchivedMemberLeavesTheWayTheStationLetsThemGo() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int memberId = peopled.member().id();
        int strangerAccountId = freshAccount().id();
        service.setPermissions(
                clusterId, memberId, Set.of(StationPermission.USER, StationPermission.LOGIN), strangerAccountId);
        var group = memberGroupRepo.create(peopled.station().id(), "Jugend " + NAMES.incrementAndGet());
        memberGroupRepo.addMember(group.id(), memberId);

        service.archive(clusterId, memberId, strangerAccountId);

        var archived = stationMemberRepo.findById(memberId).orElseThrow();
        assertTrue(archived.former());
        assertNull(archived.accountId(), "the login is taken away with the account");
        assertTrue(stationMemberRepo.findPermissions(memberId).isEmpty());
        assertTrue(memberGroupRepo.findGroupsForMember(memberId).isEmpty());
    }

    @Test
    void somebodyTheStationCouldNotArchiveIsNotArchivedByTheAssociationEither() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int strangerAccountId = freshAccount().id();
        service.setPermissions(
                clusterId, peopled.member().id(), Set.of(StationPermission.STATION_ADMINISTRATOR), strangerAccountId);

        var refusal = assertThrows(
                RefusalResponse.class,
                () -> service.archive(clusterId, peopled.member().id(), strangerAccountId));

        assertEquals(ClusterRefusal.CLUSTER_MANAGED_MEMBER_NOT_ARCHIVED, refusal.refusal());
        assertFalse(
                stationMemberRepo.findById(peopled.member().id()).orElseThrow().former());
    }

    @Test
    void aMemberAtSomebodyElsesStationIsNotFoundAtAll() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var elsewhere = stationWithMember(otherClusterId);
        int strangerAccountId = freshAccount().id();

        assertThrows(
                RefusalResponse.class,
                () -> service.setUserType(
                        clusterId, elsewhere.member().id(), StationUserType.MANAGER, strangerAccountId));
    }

    @Test
    void aProfileCarriesTheStationsQuestionsAndTheClustersTogether() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);

        var own = profileFieldService.create(
                peopled.station().id(), "Spindnummer", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null);
        profileFieldService.assignToRole(own.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        var shared = clusterProfileFieldService.create(
                clusterId,
                "Mitgliedsnummer",
                FieldType.TEXT,
                ProfileFieldConfig.empty(),
                false,
                false,
                null,
                true,
                false,
                null);
        clusterProfileFieldService.assignToRole(clusterId, shared.id(), ProfileFieldScope.MEMBER, 0, null, null, null);

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
                FieldType.TEXT,
                ProfileFieldConfig.empty(),
                false,
                false,
                null,
                true,
                false,
                null);
        clusterProfileFieldService.assignToRole(clusterId, field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);

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
    void anAssociationCannotAnswerAnotherStationsQuestion() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        var elsewhere = stationWithMember(clusterId);
        var field = profileFieldService.create(
                elsewhere.station().id(),
                "Spindnummer",
                FieldType.TEXT,
                ProfileFieldConfig.empty(),
                false,
                false,
                null);
        profileFieldService.assignToRole(field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        int strangerAccountId = freshAccount().id();

        var refusal = assertThrows(
                RefusalResponse.class,
                () -> service.updateMemberProfile(
                        clusterId,
                        peopled.member().id(),
                        List.of(new FieldValueEntry(field.id(), "\"12\"", FieldOrigin.STATION)),
                        strangerAccountId,
                        peopled.member().id()));

        assertEquals(MemberRefusal.PROFILE_FIELD_NOT_HERE_ON_ANSWER, refusal.refusal());
        assertTrue(profileFieldService.findValues(peopled.member().id()).isEmpty());
    }

    @Test
    void anAssociationAnswersOnlyWhatTheMemberIsAskedAndPassesTheirStationsLock() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int stationId = peopled.station().id();
        var notAsked = profileFieldService.create(
                stationId, "Funkrufname", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null);
        profileFieldService.assignToRole(notAsked.id(), ProfileFieldScope.TEAM, 0, null, null, null);
        var locked = profileFieldService.create(
                stationId, "Dienstgrad", FieldType.TEXT, ProfileFieldConfig.empty(), false, true, null);
        profileFieldService.assignToRole(locked.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        int strangerAccountId = freshAccount().id();

        service.updateMemberProfile(
                clusterId,
                peopled.member().id(),
                List.of(
                        new FieldValueEntry(notAsked.id(), "\"Florian 1\"", FieldOrigin.STATION),
                        new FieldValueEntry(locked.id(), "\"Brandmeister\"", FieldOrigin.STATION)),
                strangerAccountId,
                peopled.member().id());

        var answered = profileFieldService.findValues(peopled.member().id()).stream()
                .map(ProfileFieldService.MergedValue::fieldId)
                .toList();
        assertEquals(List.of(locked.id()), answered);
    }

    @Test
    void theTwoRefusalsHoldForAnsweringToo() {
        int clusterId = freshCluster();
        var peopled = stationWithMember(clusterId);
        int ownAccountId = peopled.account().id();

        assertThrows(
                RefusalResponse.class,
                () -> service.updateMemberProfile(
                        clusterId,
                        peopled.member().id(),
                        List.of(),
                        ownAccountId,
                        peopled.member().id()));

        stationRepo.setOwner(peopled.station().id(), peopled.member().id());
        int strangerAccountId = freshAccount().id();
        assertThrows(
                RefusalResponse.class,
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

        assertNull(made.email(), "somebody with no address is written down with none, not with one made up");
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
                RefusalResponse.class,
                () -> service.documentsOf(clusterId, theirs.member().id()),
                "somebody at another association's station is nobody here");
        var hidden = assertThrows(
                RefusalResponse.class,
                () -> service.requireDocumentOfCluster(clusterId, filed.id()),
                "and neither is what is filed about them");
        assertEquals(ClusterRefusal.CLUSTER_MANAGED_DOCUMENT_NOT_HERE, hidden.refusal());
    }

    @Test
    void anAssociationTakesNobodyOnAtSomebodyElsesStation() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var theirs = clusterService.createStation(otherClusterId, "Wache Fremd " + NAMES.incrementAndGet());

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.createMember(
                        clusterId, theirs.uid(), "Neu", "Fremd", "fremd@test.com", StationUserType.MEMBER),
                "a station answering to somebody else is not one of this association's");
        assertEquals(ClusterRefusal.CLUSTER_MANAGED_MEMBER_STATION_NOT_HERE, refused.refusal());
    }
}
