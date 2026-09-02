/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Covers what deleting a wiki entry does now that it is a mark rather than a removal: where the
 * entry stops appearing, what a folder takes down with it, what comes back, and what finally goes
 * when the trash is cleared out.
 */
class KbTrashServiceTest extends RepositoryTestBase {
    private static KbTrashService service;
    private static KbAccessService accessService;
    private static KbSearchService searchService;
    private static KbContentService contentService;
    private static KbFileStorageService fileStorage;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        fileStorage = mock(KbFileStorageService.class);
        searchService = new KbSearchService(knowledgeBaseRepo, stationRepo);
        contentService = new KbContentService(
                knowledgeBaseRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                fileStorage,
                searchService);
        accessService = new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo);
        service = new KbTrashService(
                knowledgeBaseRepo,
                fileStorage,
                contentService,
                searchService,
                accessService,
                new KbAuthorNameService(stationMemberRepo, accountRepo),
                pageRepo);
        station = stationRepo.create("KbTrashStation");
        account = accountRepo.create("kb-trash@test.com", "Kb", "Trasher");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static MemberAccess manager() {
        return new MemberAccess(member.id(), StationUserType.MEMBER, List.of(), List.of(), true, true);
    }

    private static KbFolder folder(Integer parentId, String name) {
        return knowledgeBaseRepo.createFolder(station.id(), parentId, name, "", member.id());
    }

    private static KbFile file(Integer folderId, String name) {
        return knowledgeBaseRepo.createFile(
                station.id(), folderId, name, "", KbFileType.MARKDOWN, "text/markdown", 7, null, member.id());
    }

    /**
     * The whole point: what was deleted is gone from every way of reaching it, and nothing about it
     * has actually been thrown away.
     */
    @Test
    void aDeletedArticleLeavesTheListingTheSearchAndTheFavourites() {
        var article = file(null, "trash-searchable");
        contentService.initialiseMarkdown(article.id(), "Loeschbarer Hydrantenplan", member.id());
        knowledgeBaseRepo.addFavourite(member.id(), article.id());
        assertFalse(searchService.search(station.id(), "Hydrantenplan").isEmpty());

        assertTrue(service.deleteFile(article.id(), member.id()));

        assertTrue(knowledgeBaseRepo.findFileById(article.id()).isEmpty());
        assertTrue(knowledgeBaseRepo.findFiles(station.id(), null).stream().noneMatch(f -> f.id() == article.id()));
        assertTrue(searchService.search(station.id(), "Hydrantenplan").isEmpty());
        assertTrue(knowledgeBaseRepo.findFavourites(member.id()).stream().noneMatch(f -> f.id() == article.id()));
        assertFalse(knowledgeBaseRepo.isFavourite(member.id(), article.id()));
        assertTrue(knowledgeBaseRepo.findDeletedFileById(article.id()).isPresent());

        service.purgeFile(article.id());
    }

    /**
     * A deletion that can be taken back is only worth having if what comes back is whole, search
     * included: the index row went with the deletion, so the restore has to write it again.
     */
    @Test
    void restoringAnArticleMakesItFindableAgain() {
        var article = file(null, "trash-restorable");
        contentService.initialiseMarkdown(article.id(), "Alarmierungswege der Wache", member.id());
        service.deleteFile(article.id(), member.id());

        var result = service.restoreFile(article.id());

        assertTrue(result.restored());
        assertEquals("trash-restorable", result.name());
        assertFalse(result.movedToRoot());
        assertTrue(knowledgeBaseRepo.findFileById(article.id()).isPresent());
        assertFalse(searchService.search(station.id(), "Alarmierungswege").isEmpty());

        service.deleteFile(article.id(), member.id());
        service.purgeFile(article.id());
    }

    /**
     * A folder with two hundred articles is one line in the trash, not two hundred and one, and it
     * comes back the same way it went.
     */
    @Test
    void aFolderIsOneEntryAndComesBackWithEverythingInIt() {
        var outer = folder(null, "trash-outer");
        var inner = folder(outer.id(), "trash-inner");
        var top = file(outer.id(), "trash-top-article");
        var deep = file(inner.id(), "trash-deep-article");

        assertTrue(service.deleteFolder(outer.id(), member.id()));

        var view = service.list(manager(), station.id());
        var entry = view.entries().stream()
                .filter(e -> e.folder() && e.id() == outer.id())
                .findFirst()
                .orElseThrow();
        assertEquals(3, entry.contained(), "the folder inside and both articles went down with it");
        assertEquals(14, entry.bytes());
        assertEquals("Kb Trasher", entry.deletedByName());
        assertTrue(view.entries().stream().noneMatch(e -> !e.folder() && e.id() == deep.id()));

        assertTrue(service.restoreFolder(outer.id()).restored());

        assertTrue(knowledgeBaseRepo.findFolderById(inner.id()).isPresent());
        assertTrue(knowledgeBaseRepo.findFileById(top.id()).isPresent());
        assertTrue(knowledgeBaseRepo.findFileById(deep.id()).isPresent());

        service.deleteFolder(outer.id(), member.id());
        service.purgeFolder(outer.id());
    }

    /**
     * An article somebody deleted on purpose stays deleted when the folder around it later follows
     * it: restoring the folder is not a way to undo somebody else's decision.
     */
    @Test
    void anArticleDeletedOnItsOwnDoesNotComeBackWithItsFolder() {
        var parent = folder(null, "trash-mixed");
        var early = file(parent.id(), "trash-early");
        var late = file(parent.id(), "trash-late");
        service.deleteFile(early.id(), member.id());
        service.deleteFolder(parent.id(), member.id());

        service.restoreFolder(parent.id());

        assertTrue(knowledgeBaseRepo.findFileById(late.id()).isPresent(), "what went down with the folder came back");
        assertTrue(knowledgeBaseRepo.findFileById(early.id()).isEmpty(), "what was deleted on its own stayed deleted");

        service.purgeFile(early.id());
        service.deleteFolder(parent.id(), member.id());
        service.purgeFolder(parent.id());
    }

    /**
     * The one case a restore cannot honour literally. Putting the article back where it was would
     * leave it alive inside a folder nobody can open, which is worse than losing it: it would be
     * unreachable and still counted. The top level is always reachable, and the answer says so.
     */
    @Test
    void anArticleWhoseFolderIsAlsoInTheTrashComesBackAtTheTopLevel() {
        var parent = folder(null, "trash-vanished-parent");
        var article = file(parent.id(), "trash-homeless");
        service.deleteFile(article.id(), member.id());
        service.deleteFolder(parent.id(), member.id());

        var result = service.restoreFile(article.id());

        assertTrue(result.restored());
        assertTrue(result.movedToRoot());
        assertNull(knowledgeBaseRepo.findFileById(article.id()).orElseThrow().folderId());

        service.deleteFile(article.id(), member.id());
        service.purgeFile(article.id());
        service.purgeFolder(parent.id());
    }

    /**
     * The bug the trash had to fix on the way past. Emptying a folder through the database cascade
     * alone takes the rows and leaves the uploaded bytes and the blocks of every article in it
     * behind, where nothing ever looks for them again.
     */
    @Test
    void clearingAFolderOutTakesTheBytesAndTheBlocksOfEveryArticleInIt() {
        var branch = folder(null, "trash-payload-branch");
        var nested = folder(branch.id(), "trash-payload-nested");
        var plain = file(branch.id(), "trash-payload-plain");
        var rich = file(nested.id(), "trash-payload-rich");
        contentService.initialiseMarkdown(rich.id(), "Ein Absatz", member.id());
        int containerId = contentService.switchToRich(rich.id()).orElseThrow().containerId();

        service.deleteFolder(branch.id(), member.id());
        assertTrue(service.purgeFolder(branch.id()));

        verify(fileStorage).delete(station.id(), plain.id());
        verify(fileStorage).delete(station.id(), rich.id());
        assertTrue(contentContainerRepo.findById(containerId).isEmpty(), "the blocks went with the article");
        assertTrue(knowledgeBaseRepo.findDeletedFolderById(branch.id()).isEmpty());
        assertTrue(knowledgeBaseRepo.findDeletedFileById(rich.id()).isEmpty());
    }

    /**
     * The trash is not a second permission system. A reader sees exactly the entries they could have
     * deleted, so nobody reads the name of something they were never allowed to open.
     */
    @Test
    void theTrashShowsOnlyWhatTheReaderCouldHaveDeleted() {
        var readable = file(null, "trash-readable");
        accessService.setGrants(
                null,
                readable.id(),
                List.of(new KbAccessService.GrantEntry(StationUserType.MEMBER, null, null, null, KbAccessLevel.READ)));
        var ownable = file(null, "trash-ownable");
        service.deleteFile(readable.id(), member.id());
        service.deleteFile(ownable.id(), member.id());

        var editor = new MemberAccess(member.id(), StationUserType.MEMBER, List.of(), List.of(), true, false);
        var entries = service.list(editor, station.id()).entries();

        assertTrue(entries.stream().anyMatch(e -> e.id() == ownable.id()));
        assertTrue(entries.stream().noneMatch(e -> e.id() == readable.id()));

        service.purgeFile(readable.id());
        service.purgeFile(ownable.id());
    }

    /**
     * What is in the trash is still on disk, so it still counts, and the view says how much of the
     * station's storage emptying it would give back.
     */
    @Test
    void theTrashSaysHowMuchStorageItIsStillHolding() {
        var article = file(null, "trash-weight");
        service.deleteFile(article.id(), member.id());

        var view = service.list(manager(), station.id());
        long weighed = view.entries().stream()
                .filter(entry -> entry.id() == article.id() && !entry.folder())
                .mapToLong(KbTrashService.TrashEntry::bytes)
                .sum();

        assertEquals(7, weighed, "what waits in the trash is still on disk, so it still counts");
        assertTrue(view.bytes() >= weighed);
        assertTrue(service.empty(manager(), station.id()) >= 1);
        assertTrue(service.list(manager(), station.id()).entries().isEmpty());
    }

    /**
     * The confirmation has to name what a delete really takes, which is not the number of ticked
     * boxes: a folder brings everything under it.
     */
    @Test
    void theImpactOfASelectionCountsWhatIsInsideTheFolders() {
        var branch = folder(null, "trash-impact");
        var nested = folder(branch.id(), "trash-impact-nested");
        var inside = file(nested.id(), "trash-impact-inside");
        var picked = file(null, "trash-impact-picked");

        var impact = service.impactOf(station.id(), List.of(branch.id()), List.of(picked.id()));

        assertEquals(2, impact.folders());
        assertEquals(2, impact.files());
        assertEquals(
                0, service.impactOf(station.id(), List.of(999_999), List.of()).folders());

        service.deleteFolder(branch.id(), member.id());
        service.purgeFolder(branch.id());
        service.deleteFile(picked.id(), member.id());
        service.purgeFile(picked.id());
        assertTrue(knowledgeBaseRepo.findDeletedFileById(inside.id()).isEmpty());
    }

    /**
     * Nothing is measured against the last run, only against how old an entry is, so an instance
     * that was off catches up by itself. The cap is what keeps that catching up from being one long
     * run at boot.
     */
    @Test
    void theSweepTakesWhatIsDueAndNoMoreThanItsCapAtOnce() {
        var stale = file(null, "trash-stale");
        var fresh = file(null, "trash-fresh");
        service.deleteFile(stale.id(), member.id());
        service.deleteFile(fresh.id(), member.id());
        ageTrash(stale.id(), 40);

        assertEquals(0, service.sweepExpired(30, 0), "a cap of nothing takes nothing");
        assertEquals(1, service.sweepExpired(30, 10));

        assertTrue(knowledgeBaseRepo.findDeletedFileById(stale.id()).isEmpty());
        assertTrue(knowledgeBaseRepo.findDeletedFileById(fresh.id()).isPresent());

        service.purgeFile(fresh.id());
    }

    /**
     * A folder waiting in the trash keeps its name but stops reserving it, so clearing up and
     * starting again does not collide with something nobody can see.
     */
    @Test
    void aFolderInTheTrashStopsReservingItsName() {
        var first = folder(null, "trash-name-clash");
        service.deleteFolder(first.id(), member.id());

        var second = folder(null, "trash-name-clash");

        assertFalse(knowledgeBaseRepo.folderNameTaken(station.id(), null, "trash-name-clash", second.id()));

        service.purgeFolder(first.id());
        service.deleteFolder(second.id(), member.id());
        service.purgeFolder(second.id());
    }

    /**
     * Asking for something that is not in the trash is an answer, not a failure.
     */
    @Test
    void nothingHappensToAnEntryThatIsNotInTheTrash() {
        var article = file(null, "trash-untouched");

        assertFalse(service.restoreFile(article.id()).restored());
        assertFalse(service.restoreFolder(999_999).restored());
        assertFalse(service.purgeFile(article.id()));
        assertFalse(service.purgeFolder(999_999));
        assertFalse(service.deleteFile(999_999, member.id()));
        assertFalse(service.deleteFolder(999_999, member.id()));

        service.deleteFile(article.id(), member.id());
        assertFalse(service.deleteFile(article.id(), member.id()), "deleting twice is one deletion");
        service.purgeFile(article.id());
    }

    /**
     * Backdates a deletion so a retention window can be tested without waiting for one.
     */
    private static void ageTrash(int fileId, int days) {
        query("UPDATE kb_file SET deleted_at = now() - make_interval(days := :days) WHERE id = :id;")
                .single(call().bind("id", fileId).bind("days", days))
                .update();
    }
}
