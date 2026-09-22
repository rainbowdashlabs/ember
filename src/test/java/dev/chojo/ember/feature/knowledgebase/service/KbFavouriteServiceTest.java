/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.service.ClusterAutoShareService;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.knowledgebase.entity.KbFavourite;
import dev.chojo.ember.feature.knowledgebase.entity.KbFavouriteTarget;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.UrlMetadata;
import dev.chojo.ember.feature.knowledgebase.repository.KbFavouriteRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService.PartnerEntry;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A member's favourites in the wiki: this station's files and folders followed live and through
 * the reader's access, a partner's entries kept as the partner last described them.
 */
class KbFavouriteServiceTest extends RepositoryTestBase {
    private static final UUID PARTNER = UUID.fromString("00000000-0000-4000-a000-0000000000f1");

    private static KnowledgeBaseService knowledgeBase;
    private static KbTrashService trash;
    private static KbAccessService access;
    private static KnowledgeBaseFederationService federation;
    private static KbFavouriteService favourites;
    private static Station station;
    private static Station otherStation;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        var fileStorage = mock(KbFileStorageService.class);
        var linkMetadata = mock(KbLinkMetadataService.class);
        when(linkMetadata.fetchUrlMetadata(anyString())).thenReturn(new UrlMetadata(null, null));
        var storageConfig = new Storage();
        var search = new KbSearchService(knowledgeBaseRepo, stationRepo);
        var content = new KbContentService(
                knowledgeBaseRepo,
                new ContentBlockService(contentContainerRepo),
                noCellDescriptions(),
                stationRepo,
                fileStorage,
                search);
        access = new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo);
        knowledgeBase = new KnowledgeBaseService(
                knowledgeBaseRepo,
                fileStorage,
                content,
                access,
                new KbPresentationService(knowledgeBaseRepo, fileStorage, content),
                linkMetadata,
                new PresentationCompressor(storageConfig),
                new PdfCompressor(storageConfig),
                new ClusterAutoShareService(new ClusterRepository(), new FederationRepository()));
        trash = new KbTrashService(
                knowledgeBaseRepo,
                fileStorage,
                content,
                search,
                access,
                new KbAuthorNameService(stationMemberRepo, accountRepo),
                pageRepo);
        federation = mock(KnowledgeBaseFederationService.class);
        favourites =
                new KbFavouriteService(new KbFavouriteRepository(), knowledgeBase, access, federation, stationRepo);
        station = stationRepo.create("KbFavouriteStation");
        otherStation = stationRepo.create("KbFavouriteOtherStation");
        account = accountRepo.create("kb-favourite@test.com", "Kb", "Favourite");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        accountRepo.delete(account.id());
    }

    @AfterEach
    void forgetEverything() {
        favourites.list(reader()).forEach(favourite -> favourites.unmark(member.id(), favourite.id()));
        reset(federation);
    }

    private static MemberAccess reader() {
        return access.memberAccess(member.id(), StationUserType.MEMBER);
    }

    private static KbFile file(int stationId, String name) {
        return knowledgeBase.createMarkdownFile(stationId, null, name, "", "# " + name, member.id());
    }

    private static List<Integer> listedIds() {
        return favourites.list(reader()).stream().map(KbFavourite::entryId).toList();
    }

    @Test
    void aFileAndAFolderCanBeMarkedAndAreListedWithTheirNames() {
        var file = file(station.id(), "Hydrantenplan");
        var folder = knowledgeBase.createFolder(station.id(), null, "Einsatz", "", member.id());

        favourites.markLocal(station.id(), reader(), KbFavouriteTarget.FILE, file.id());
        favourites.markLocal(station.id(), reader(), KbFavouriteTarget.FOLDER, folder.id());

        var listed = favourites.list(reader());
        assertEquals(
                List.of("Einsatz", "Hydrantenplan"),
                listed.stream().map(KbFavourite::title).toList());
    }

    @Test
    void markingTheSameEntryTwiceKeepsOneFavourite() {
        var file = file(station.id(), "Doppelt");

        var first = favourites.markLocal(station.id(), reader(), KbFavouriteTarget.FILE, file.id());
        var second = favourites.markLocal(station.id(), reader(), KbFavouriteTarget.FILE, file.id());

        assertEquals(first.id(), second.id());
        assertEquals(1, listedIds().size());
    }

    /** What was deleted is gone from every way of reaching it, and back with it when restored. */
    @Test
    void aFileInTheTrashIsNotListedAndReturnsWhenRestored() {
        var file = file(station.id(), "Papierkorb");
        favourites.markLocal(station.id(), reader(), KbFavouriteTarget.FILE, file.id());

        trash.deleteFile(file.id(), member.id());
        assertFalse(listedIds().contains(file.id()));

        trash.restoreFile(file.id());
        assertTrue(listedIds().contains(file.id()));
    }

    /** Losing access hides a favourite without taking it away, so it returns with the access. */
    @Test
    void aFileTheReaderLostAccessToIsHiddenAndComesBackWithTheAccess() {
        var file = file(station.id(), "Nur Mitglieder");
        favourites.markLocal(station.id(), reader(), KbFavouriteTarget.FILE, file.id());

        access.setRestrictions(
                null,
                file.id(),
                new RestrictionSelection(List.of(StationUserType.TEAM), List.of(), List.of(), List.of(), null));
        assertFalse(listedIds().contains(file.id()));

        access.setRestrictions(null, file.id(), RestrictionSelection.empty());
        assertTrue(listedIds().contains(file.id()));
    }

    @Test
    void aFileTheReaderMayNotReadCannotBeMarked() {
        var file = file(station.id(), "Verborgen");
        access.setRestrictions(
                null,
                file.id(),
                new RestrictionSelection(List.of(StationUserType.TEAM), List.of(), List.of(), List.of(), null));

        assertThrows(
                NotFoundResponse.class,
                () -> favourites.markLocal(station.id(), reader(), KbFavouriteTarget.FILE, file.id()));
    }

    @Test
    void anotherStationsFileCannotBeMarkedAsOneOfThisStations() {
        var foreign = file(otherStation.id(), "Fremd");

        assertThrows(
                NotFoundResponse.class,
                () -> favourites.markLocal(station.id(), reader(), KbFavouriteTarget.FILE, foreign.id()));
    }

    /** The name comes from the partner's answer, never from whatever the page sent. */
    @Test
    void aPartnerFileIsKeptAsThePartnerDescribedIt() {
        when(federation.describePartnerFile(station.id(), PARTNER, 42))
                .thenReturn(new PartnerEntry("Atemschutz", "PDF", "Nachbarwache"));

        favourites.markPartner(
                station.id(), member.id(), StationUserType.MEMBER, KbFavouriteTarget.PARTNER_FILE, PARTNER, 42);

        var kept = favourites.list(reader()).getFirst();
        assertEquals("Atemschutz", kept.title());
        assertEquals("Nachbarwache", kept.stationName());
        assertEquals(PARTNER, kept.partnerStationUid());
    }

    @Test
    void aPartnerFolderIsKeptAsThePartnerDescribedIt() {
        when(federation.describePartnerFolder(station.id(), PARTNER, 7, StationUserType.MEMBER))
                .thenReturn(new PartnerEntry("Ausbildung", null, "Nachbarwache"));

        var kept = favourites.markPartner(
                station.id(), member.id(), StationUserType.MEMBER, KbFavouriteTarget.PARTNER_FOLDER, PARTNER, 7);

        assertEquals("Ausbildung", kept.title());
        assertNull(kept.fileType());
    }

    @Test
    void aPartnerEntryThePartnerDoesNotShareIsRefused() {
        when(federation.describePartnerFile(station.id(), PARTNER, 43)).thenThrow(new NotFoundResponse());

        assertThrows(
                NotFoundResponse.class,
                () -> favourites.markPartner(
                        station.id(),
                        member.id(),
                        StationUserType.MEMBER,
                        KbFavouriteTarget.PARTNER_FILE,
                        PARTNER,
                        43));
        assertTrue(listedIds().isEmpty());
    }

    /** A renamed partner file must not go on showing the name it had when it was marked. */
    @Test
    void servingAPartnerFileAgainBringsItsKeptNameUpToDate() {
        when(federation.describePartnerFile(station.id(), PARTNER, 44))
                .thenReturn(new PartnerEntry("Alt", "PDF", "Nachbarwache"));
        favourites.markPartner(
                station.id(), member.id(), StationUserType.MEMBER, KbFavouriteTarget.PARTNER_FILE, PARTNER, 44);

        favourites.refreshPartnerFile(PARTNER, 44, new PartnerEntry("Neu", "PDF", "Nachbarwache"));

        assertEquals("Neu", favourites.list(reader()).getFirst().title());
    }

    /** Nobody is left with a mark they cannot take off. */
    @Test
    void aFavouriteCanBeRemovedWithoutAccessToWhatItPointsAt() {
        var file = file(station.id(), "Entzogen");
        var favourite = favourites.markLocal(station.id(), reader(), KbFavouriteTarget.FILE, file.id());
        access.setRestrictions(
                null,
                file.id(),
                new RestrictionSelection(List.of(StationUserType.TEAM), List.of(), List.of(), List.of(), null));

        assertTrue(favourites.unmark(member.id(), favourite.id()));
        access.setRestrictions(null, file.id(), RestrictionSelection.empty());
        assertFalse(listedIds().contains(file.id()));
    }

    @Test
    void someoneElsesFavouriteCannotBeRemoved() {
        var file = file(station.id(), "Fremde Hand");
        var favourite = favourites.markLocal(station.id(), reader(), KbFavouriteTarget.FILE, file.id());

        assertFalse(favourites.unmark(member.id() + 100_000, favourite.id()));
        assertTrue(listedIds().contains(file.id()));
    }
}
