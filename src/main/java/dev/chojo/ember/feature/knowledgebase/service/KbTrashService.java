/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository.TrashedFile;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository.TrashedFolder;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import dev.chojo.ember.feature.page.repository.PageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * The trash of a station's knowledge base: what deleting now does, what taking it back does, and
 * what finally clearing it out does.
 *
 * <p>Deleting marks the entry and leaves everything else exactly where it stands. That is what makes
 * the reader who deleted something the one who can put it back: permission is read along the folder
 * path, and the path is still there, so nothing here has to look up rights a second way.
 *
 * <p>Clearing out for good runs article by article rather than as one statement, because the row is
 * the smaller half of an article: the bytes in storage and the blocks a rich article is built from
 * belong to nobody the database cascade can reach, and a folder emptied by the cascade alone leaves
 * both behind.
 */
@Singleton
public class KbTrashService {
    private static final Logger log = LoggerFactory.getLogger(KbTrashService.class);

    private final KnowledgeBaseRepository repository;
    private final KbFileStorageService fileStorage;
    private final KbContentService contentService;
    private final KbSearchService searchService;
    private final KbAccessService accessService;
    private final KbAuthorNameService authorNameService;
    private final PageRepository pageRepository;

    @Inject
    public KbTrashService(
            KnowledgeBaseRepository repository,
            KbFileStorageService fileStorage,
            KbContentService contentService,
            KbSearchService searchService,
            KbAccessService accessService,
            KbAuthorNameService authorNameService,
            PageRepository pageRepository) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.contentService = contentService;
        this.searchService = searchService;
        this.accessService = accessService;
        this.authorNameService = authorNameService;
        this.pageRepository = pageRepository;
    }

    /**
     * Puts an article in the trash.
     *
     * <p>Its search index row goes at the same moment. A filter in the search query would have to be
     * repeated by every query written afterwards; a row that is not there cannot be forgotten.
     *
     * @param fileId   the article
     * @param memberId who deleted it, {@code null} when nobody in particular did
     * @return {@code true} when the article was in use until now
     */
    public boolean deleteFile(int fileId, Integer memberId) {
        if (!repository.softDeleteFile(fileId, memberId)) return false;
        repository.deleteSearchIndex(List.of(fileId));
        log.info("KB file {} moved to the trash by member {}", fileId, memberId);
        return true;
    }

    /**
     * Puts a folder in the trash, with everything inside it.
     *
     * @param folderId the folder
     * @param memberId who deleted it, {@code null} when nobody in particular did
     * @return {@code true} when the folder was in use until now
     */
    public boolean deleteFolder(int folderId, Integer memberId) {
        if (!repository.softDeleteFolder(folderId, memberId)) return false;
        var files = repository.markSubtreeDeleted(folderId, memberId);
        repository.deleteSearchIndex(files);
        log.info(
                "KB folder {} moved to the trash by member {}, taking {} article(s)", folderId, memberId, files.size());
        return true;
    }

    /**
     * Takes an article back out of the trash.
     *
     * @param fileId the article
     * @return what happened to it
     */
    public RestoreResult restoreFile(int fileId) {
        var file = repository.findDeletedFileById(fileId).orElse(null);
        if (file == null) return RestoreResult.missing();
        boolean toRoot = inTrash(file.folderId());
        repository.restoreFile(fileId, toRoot);
        reindex(fileId);
        log.info("KB file {} restored{}", fileId, toRoot ? " to the top level" : "");
        return new RestoreResult(true, file.name(), toRoot);
    }

    /**
     * Takes a folder back out of the trash, with everything that went down with it.
     *
     * @param folderId the folder
     * @return what happened to it
     */
    public RestoreResult restoreFolder(int folderId) {
        var folder = repository.findDeletedFolderById(folderId).orElse(null);
        if (folder == null) return RestoreResult.missing();
        boolean toRoot = inTrash(folder.parentId());
        repository.restoreFolder(folderId, toRoot);
        repository.restoreSubtree(folderId).forEach(this::reindex);
        log.info("KB folder {} restored{}", folderId, toRoot ? " to the top level" : "");
        return new RestoreResult(true, folder.name(), toRoot);
    }

    /**
     * Clears one article out of the trash for good, bytes and blocks included.
     *
     * @param fileId the article
     * @return {@code true} when it was in the trash
     */
    public boolean purgeFile(int fileId) {
        var file = repository.findDeletedFileById(fileId).orElse(null);
        if (file == null) return false;
        dropPayload(file);
        boolean purged = repository.purgeFile(fileId);
        if (purged) log.info("KB file {} cleared out of the trash", fileId);
        return purged;
    }

    /**
     * Clears one folder out of the trash for good, with everything below it.
     *
     * <p>Every article in the branch is walked and its bytes and blocks dropped before the row goes.
     * Leaving that to the cascade would take the rows and leave the storage full.
     *
     * @param folderId the folder
     * @return {@code true} when it was in the trash
     */
    public boolean purgeFolder(int folderId) {
        var folder = repository.findDeletedFolderById(folderId).orElse(null);
        if (folder == null) return false;
        var files = repository.findFilesInSubtree(folderId);
        files.forEach(this::dropPayload);
        boolean purged = repository.purgeFolder(folderId);
        if (purged) log.info("KB folder {} cleared out of the trash with {} article(s)", folderId, files.size());
        return purged;
    }

    /**
     * What one reader sees in a station's trash.
     *
     * <p>Only the entries they may manage, which is the same right that let them delete something in
     * the first place, and only the ones that were deleted in their own right: a folder with two
     * hundred articles in it is one line, not two hundred and one.
     *
     * @param access    the reader's memberships and station rights
     * @param stationId the station
     * @return the entries and what they take up
     */
    public TrashView list(MemberAccess access, int stationId) {
        var folders = repository.findTrashedFolders(stationId);
        var files = repository.findTrashedFiles(stationId);
        var childFolders = new HashMap<Integer, List<TrashedFolder>>();
        for (var folder : folders) {
            if (folder.parentId() != null) {
                childFolders
                        .computeIfAbsent(folder.parentId(), key -> new ArrayList<>())
                        .add(folder);
            }
        }
        var folderFiles = new HashMap<Integer, List<TrashedFile>>();
        for (var file : files) {
            if (file.folderId() != null) {
                folderFiles
                        .computeIfAbsent(file.folderId(), key -> new ArrayList<>())
                        .add(file);
            }
        }

        var entries = new ArrayList<TrashEntry>();
        long bytes = 0;
        for (var folder : folders) {
            if (folder.deletedWithFolder()) continue;
            if (!mayManage(access, folder.id(), null)) continue;
            var branch = branchOf(folder, childFolders, folderFiles);
            entries.add(new TrashEntry(
                    true,
                    folder.id(),
                    folder.name(),
                    folder.description(),
                    null,
                    folder.deletedAt(),
                    nameOf(folder.deletedBy()),
                    branch.bytes(),
                    branch.entries()));
            bytes += branch.bytes();
        }
        for (var file : files) {
            if (file.deletedWithFolder()) continue;
            if (!mayManage(access, null, file.id())) continue;
            entries.add(new TrashEntry(
                    false,
                    file.id(),
                    file.name(),
                    file.description(),
                    file.fileType(),
                    file.deletedAt(),
                    nameOf(file.deletedBy()),
                    file.fileSize(),
                    0));
            bytes += file.fileSize();
        }
        entries.sort((left, right) -> right.deletedAt().compareTo(left.deletedAt()));
        return new TrashView(List.copyOf(entries), bytes);
    }

    /**
     * Clears out everything one reader sees in the trash, which is everything they could have put
     * there.
     *
     * @param access    the reader's memberships and station rights
     * @param stationId the station
     * @return how many entries went
     */
    public int empty(MemberAccess access, int stationId) {
        int cleared = 0;
        for (var entry : list(access, stationId).entries()) {
            boolean gone = entry.folder() ? purgeFolder(entry.id()) : purgeFile(entry.id());
            if (gone) cleared++;
        }
        log.info("Station {} emptied {} entries out of its knowledge-base trash", stationId, cleared);
        return cleared;
    }

    /**
     * Clears out every entry whose time in the trash is up, across every station.
     *
     * <p>Capped, because the first run after a long outage would otherwise be one long file
     * operation at boot. What does not fit falls on the next run, which is an hour away.
     *
     * @param retentionDays how long an entry is kept
     * @param limit         how many entries one run may take
     * @return how many entries went
     */
    public int sweepExpired(int retentionDays, int limit) {
        int cleared = 0;
        for (var ref : repository.findExpiredTrash(retentionDays, limit)) {
            boolean gone = ref.folder() ? purgeFolder(ref.id()) : purgeFile(ref.id());
            if (gone) cleared++;
        }
        if (cleared > 0) log.info("Cleared {} expired knowledge-base trash entries", cleared);
        return cleared;
    }

    /**
     * How much a selection would take with it, counting what is inside the folders in it, so a
     * confirmation can name the true number rather than the number of ticked boxes.
     *
     * <p>It also names the pages of the station that put one of those articles on themselves. A page
     * cell holds the article's number in its settings with no foreign key behind it, so a deleted
     * article turns into a stand-in title on a page nobody thought to look at. Saying so before the
     * delete is the only moment anybody would notice.
     *
     * @param stationId the station
     * @param folderIds the folders picked
     * @param fileIds   the articles picked
     * @return how many folders and articles would go, and which pages would lose something
     */
    public DeleteImpact impactOf(int stationId, List<Integer> folderIds, List<Integer> fileIds) {
        var seenFolders = new HashSet<Integer>();
        var seenFiles = new HashSet<Integer>();
        for (int fileId : fileIds) {
            repository
                    .findFileById(fileId)
                    .filter(file -> file.stationId() == stationId)
                    .ifPresent(file -> seenFiles.add(file.id()));
        }
        for (int folderId : folderIds) {
            var folder = repository.findFolderById(folderId).orElse(null);
            if (folder == null || folder.stationId() != stationId) continue;
            seenFolders.add(folderId);
            var descendants = repository.descendantFolderIds(folderId);
            seenFolders.addAll(descendants);
            var branch = new ArrayList<>(descendants);
            branch.add(folderId);
            seenFiles.addAll(repository.findFileIdsInFolders(branch));
        }
        var pages = pageRepository.findPagesEmbeddingArticles(stationId, List.copyOf(seenFiles));
        return new DeleteImpact(
                seenFolders.size(),
                seenFiles.size(),
                pages.stream().map(PageRepository.EmbeddingPage::title).toList(),
                pages.stream().anyMatch(PageRepository.EmbeddingPage::published));
    }

    private boolean mayManage(MemberAccess access, Integer folderId, Integer fileId) {
        return accessService.effectiveLevel(access, folderId, fileId).covers(KbAccessLevel.MANAGE);
    }

    private String nameOf(Integer memberId) {
        return memberId == null ? null : authorNameService.resolveMemberName(memberId);
    }

    /**
     * Walks a deleted folder's branch, counting what followed it down and how much of the station's
     * storage it is still holding.
     */
    private Branch branchOf(
            TrashedFolder root,
            Map<Integer, List<TrashedFolder>> childFolders,
            Map<Integer, List<TrashedFile>> folderFiles) {
        int entries = 0;
        long bytes = 0;
        var pending = new ArrayDeque<TrashedFolder>();
        pending.add(root);
        while (!pending.isEmpty()) {
            var folder = pending.poll();
            for (var file : folderFiles.getOrDefault(folder.id(), List.of())) {
                if (!file.deletedWithFolder()) continue;
                entries++;
                bytes += file.fileSize();
            }
            for (var child : childFolders.getOrDefault(folder.id(), List.of())) {
                if (!child.deletedWithFolder()) continue;
                entries++;
                pending.add(child);
            }
        }
        return new Branch(entries, bytes);
    }

    /**
     * Puts an article back into the search index, which is what a restore has to redo because the
     * deletion took the row out rather than hiding it.
     */
    private void reindex(int fileId) {
        searchService.reindex(fileId, contentService.getMarkdownContent(fileId).orElse(null));
    }

    /**
     * Drops what the database cascade cannot reach: the bytes in storage and, for a rich article,
     * the container its blocks live in, which the article owns rather than the other way round.
     */
    private void dropPayload(KbFile file) {
        fileStorage.delete(file.stationId(), file.id());
        contentService.deleteBlocks(file);
    }

    private boolean inTrash(Integer folderId) {
        return folderId != null && repository.findDeletedFolderById(folderId).isPresent();
    }

    private record Branch(int entries, long bytes) {}

    /**
     * One entry of the trash as a reader sees it.
     *
     * @param fileType  what kind of article it is, {@code null} for a folder
     * @param bytes     what it is still holding in storage, its own branch included
     * @param contained how many entries went down with it, zero for an article
     */
    public record TrashEntry(
            boolean folder,
            int id,
            String name,
            String description,
            KbFileType fileType,
            Instant deletedAt,
            String deletedByName,
            long bytes,
            int contained) {}

    /**
     * A station's trash as one reader sees it.
     *
     * @param bytes what the visible entries are still holding in storage, which is space the station
     *              gets back by emptying the trash rather than space that is already free
     */
    public record TrashView(List<TrashEntry> entries, long bytes) {}

    /**
     * What a restore did.
     *
     * @param movedToRoot whether the entry had to come back at the top level because the folder it
     *                    was deleted from is itself in the trash
     */
    public record RestoreResult(boolean restored, String name, boolean movedToRoot) {
        static RestoreResult missing() {
            return new RestoreResult(false, null, false);
        }
    }

    /**
     * How much a delete would really take.
     *
     * @param embeddedOn the pages that carry one of the articles being deleted
     * @param onPublicPage whether any of those pages is one the public can read, which is the case
     *                     worth a stronger word than the rest
     */
    public record DeleteImpact(int folders, int files, List<String> embeddedOn, boolean onPublicPage) {}
}
