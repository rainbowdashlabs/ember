/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbRefusalReason;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Doing one thing to several knowledge-base entries at once.
 *
 * <p>Each entry is judged on its own and what can be done is done, rather than the whole selection
 * falling because one entry in it is out of reach: somebody sorting a branch marks what they see,
 * and refusing twenty moves over one read-only article helps nobody. What was refused comes back by
 * name, so the reader is not left to walk their own selection to find out which one it was.
 *
 * <p>The one thing judged for the selection as a whole is the folder it is going to. A target that
 * cannot be written in is not one entry failing but the intention failing, and the route answers it
 * as a refusal of the request rather than as twenty identical refusals.
 */
@Singleton
public class KbBulkService {
    private static final Logger log = LoggerFactory.getLogger(KbBulkService.class);

    /**
     * How many refused entries a result names. Past this the message stops being something anybody
     * reads, so it stops naming and the total says how many more there were.
     */
    private static final int NAMED_REFUSALS = 10;

    private final KnowledgeBaseRepository repository;
    private final KbMoveService moveService;
    private final KbTagService tagService;
    private final KbAccessService accessService;

    @Inject
    public KbBulkService(
            KnowledgeBaseRepository repository,
            KbMoveService moveService,
            KbTagService tagService,
            KbAccessService accessService) {
        this.repository = repository;
        this.moveService = moveService;
        this.tagService = tagService;
        this.accessService = accessService;
    }

    /**
     * Moves a mixed selection of folders and articles into one folder.
     *
     * @param access         the caller's memberships and station rights
     * @param stationId      the caller's station
     * @param folderIds      the folders picked
     * @param fileIds        the articles picked
     * @param targetFolderId the folder they should go into, or {@code null} for the tree root
     * @return what moved and what did not
     */
    public BulkOutcome move(
            MemberAccess access,
            int stationId,
            List<Integer> folderIds,
            List<Integer> fileIds,
            Integer targetFolderId) {
        var collector = new Collector();
        for (int folderId : folderIds) {
            collector.folder(folderId, moveService.moveFolder(access, stationId, folderId, targetFolderId));
        }
        for (int fileId : fileIds) {
            collector.file(fileId, moveService.moveFile(access, stationId, fileId, targetFolderId));
        }
        var outcome = collector.outcome();
        log.info(
                "Station {} moved {} folder(s) and {} article(s) into folder {}, refusing {}",
                stationId,
                outcome.doneFolderIds().size(),
                outcome.doneFileIds().size(),
                targetFolderId,
                outcome.refusedTotal());
        return outcome;
    }

    /**
     * Adds and removes tags across a mixed selection of folders and articles.
     *
     * <p>Adding and removing rather than setting: the editor that sets the whole list of one entry
     * would, used here, wipe every other tag off twenty entries in order to add one.
     *
     * @param access     the caller's memberships and station rights
     * @param stationId  the caller's station
     * @param folderIds  the folders picked
     * @param fileIds    the articles picked
     * @param addTags    the tag names every picked entry should carry
     * @param removeTags the tag names no picked entry should carry
     * @return what was tagged and what was not
     */
    public BulkOutcome tag(
            MemberAccess access,
            int stationId,
            List<Integer> folderIds,
            List<Integer> fileIds,
            List<String> addTags,
            List<String> removeTags) {
        var collector = new Collector();
        for (int folderId : folderIds) {
            var folder = repository.findFolderById(folderId).orElse(null);
            if (folder == null || folder.stationId() != stationId) {
                collector.refuse(null, KbRefusalReason.NOT_FOUND);
                continue;
            }
            if (!accessService.effectiveLevel(access, folderId, null).covers(KbAccessLevel.WRITE)) {
                collector.refuse(folder.name(), KbRefusalReason.NO_PERMISSION);
                continue;
            }
            tagService.addFolderTags(folderId, addTags, stationId);
            tagService.removeFolderTags(folderId, removeTags, stationId);
            collector.doneFolder(folderId);
        }
        for (int fileId : fileIds) {
            var file = repository.findFileById(fileId).orElse(null);
            if (file == null || file.stationId() != stationId) {
                collector.refuse(null, KbRefusalReason.NOT_FOUND);
                continue;
            }
            if (!accessService.effectiveLevel(access, null, fileId).covers(KbAccessLevel.WRITE)) {
                collector.refuse(file.name(), KbRefusalReason.NO_PERMISSION);
                continue;
            }
            tagService.addFileTags(fileId, addTags, stationId);
            tagService.removeFileTags(fileId, removeTags, stationId);
            collector.doneFile(fileId);
        }
        var outcome = collector.outcome();
        log.info(
                "Station {} tagged {} folder(s) and {} article(s), refusing {}",
                stationId,
                outcome.doneFolderIds().size(),
                outcome.doneFileIds().size(),
                outcome.refusedTotal());
        return outcome;
    }

    /**
     * Gathers what a run over a selection did, so both actions report the same shape.
     */
    private static final class Collector {
        private final List<Integer> doneFolderIds = new ArrayList<>();
        private final List<Integer> doneFileIds = new ArrayList<>();
        private final List<RefusedEntry> refused = new ArrayList<>();

        void folder(int folderId, KbMoveService.MoveResult result) {
            if (result.moved()) doneFolderIds.add(folderId);
            else refuse(result.name(), result.reason());
        }

        void file(int fileId, KbMoveService.MoveResult result) {
            if (result.moved()) doneFileIds.add(fileId);
            else refuse(result.name(), result.reason());
        }

        void doneFolder(int folderId) {
            doneFolderIds.add(folderId);
        }

        void doneFile(int fileId) {
            doneFileIds.add(fileId);
        }

        void refuse(String name, KbRefusalReason reason) {
            refused.add(new RefusedEntry(name, reason));
        }

        BulkOutcome outcome() {
            var named = refused.size() <= NAMED_REFUSALS
                    ? List.copyOf(refused)
                    : List.copyOf(refused.subList(0, NAMED_REFUSALS));
            return new BulkOutcome(List.copyOf(doneFolderIds), List.copyOf(doneFileIds), named, refused.size());
        }
    }

    /**
     * What a bulk action did.
     *
     * @param doneFolderIds the folders it worked on
     * @param doneFileIds   the articles it worked on
     * @param refused       the entries it left alone, named, up to the point where naming them all
     *                      would stop being readable
     * @param refusedTotal  how many were left alone altogether, which is larger than the named list
     *                      when the selection had more refusals than a message can carry
     */
    public record BulkOutcome(
            List<Integer> doneFolderIds, List<Integer> doneFileIds, List<RefusedEntry> refused, int refusedTotal) {}

    /**
     * One entry a bulk action left alone, by name and reason. The name is {@code null} only when
     * there was no entry behind the id to name.
     */
    public record RefusedEntry(String name, KbRefusalReason reason) {}
}
