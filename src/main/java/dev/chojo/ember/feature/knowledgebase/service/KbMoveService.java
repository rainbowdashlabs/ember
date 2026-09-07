/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbRefusalReason;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService.PartnerReach;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Moving a knowledge-base folder or article somewhere else in the same station's tree.
 *
 * <p>A move is one column, and everything inside a folder follows without being written to. What
 * makes it more than an update is that all three visibility axes of the knowledge base hang off the
 * place in the tree: who may read an entry, whether it stands on the public wiki, and which partner
 * stations receive it all change the moment it lands somewhere else. So a move is refused where it
 * would widen a share past what somebody set on purpose, and is previewed before it is done.
 *
 * <p>It lives beside {@link KnowledgeBaseService} rather than inside it because the checks need
 * {@link KnowledgeBaseFederationService}, which reads the tree through that service in turn.
 */
@Singleton
public class KbMoveService {
    private static final Logger log = LoggerFactory.getLogger(KbMoveService.class);

    private final KnowledgeBaseRepository repository;
    private final KbAccessService accessService;
    private final KnowledgeBaseFederationService federationService;
    private final StationRepository stationRepository;

    @Inject
    public KbMoveService(
            KnowledgeBaseRepository repository,
            KbAccessService accessService,
            KnowledgeBaseFederationService federationService,
            StationRepository stationRepository) {
        this.repository = repository;
        this.accessService = accessService;
        this.federationService = federationService;
        this.stationRepository = stationRepository;
    }

    /**
     * Whether the caller may put anything into a target folder at all.
     *
     * <p>Asked once for a whole selection: a target that does not exist or may not be written in is
     * not one entry failing, it is the whole intention failing, and answering it per entry would
     * report the same thing twenty times.
     *
     * @param access         the caller's memberships and station rights
     * @param stationId      the caller's station
     * @param targetFolderId the folder entries would go into, or {@code null} for the tree root
     * @return the reason it cannot be used, or {@code null} when it can
     */
    public KbRefusalReason checkTarget(MemberAccess access, int stationId, Integer targetFolderId) {
        if (targetFolderId == null) return null;
        var target = repository.findFolderById(targetFolderId).orElse(null);
        if (target == null || target.stationId() != stationId) return KbRefusalReason.NOT_FOUND;
        if (!accessService.effectiveLevel(access, targetFolderId, null).covers(KbAccessLevel.WRITE)) {
            return KbRefusalReason.NO_PERMISSION;
        }
        return null;
    }

    /**
     * Moves a folder, with everything under it, into another folder of the same station.
     *
     * <p>Moving asks for full rights on the folder rather than the right to write in it: a move
     * changes who is responsible for an entry, not what it says, and the folder it lands in decides
     * who reads it from then on.
     *
     * @param access         the caller's memberships and station rights
     * @param stationId      the caller's station
     * @param folderId       the folder to move
     * @param targetFolderId the folder it should sit in, or {@code null} for the tree root
     * @return whether it moved, and why it did not
     */
    public MoveResult moveFolder(MemberAccess access, int stationId, int folderId, Integer targetFolderId) {
        var folder = repository.findFolderById(folderId).orElse(null);
        if (folder == null || folder.stationId() != stationId) {
            return MoveResult.refused(null, KbRefusalReason.NOT_FOUND);
        }
        if (!accessService.effectiveLevel(access, folderId, null).covers(KbAccessLevel.MANAGE)) {
            return MoveResult.refused(folder.name(), KbRefusalReason.NO_PERMISSION);
        }

        var moved = movedSubtree(folderId);
        if (targetFolderId != null && moved.folderIds().contains(targetFolderId)) {
            return MoveResult.refused(folder.name(), KbRefusalReason.TARGET_INSIDE);
        }
        if (repository.folderNameTaken(stationId, targetFolderId, folder.name(), folderId)) {
            return MoveResult.refused(folder.name(), KbRefusalReason.NAME_TAKEN);
        }
        if (federationService.wouldOverreach(stationId, moved.folderIds(), moved.fileIds(), targetFolderId)) {
            return MoveResult.refused(folder.name(), KbRefusalReason.SHARE_TOO_WIDE);
        }

        repository.moveFolder(folderId, targetFolderId);
        log.info("KB folder {} moved into folder {} of station {}", folderId, targetFolderId, stationId);
        return MoveResult.moved(folder.name());
    }

    /**
     * Moves an article into another folder of the same station.
     *
     * @param access         the caller's memberships and station rights
     * @param stationId      the caller's station
     * @param fileId         the article to move
     * @param targetFolderId the folder it should sit in, or {@code null} for the tree root
     * @return whether it moved, and why it did not
     */
    public MoveResult moveFile(MemberAccess access, int stationId, int fileId, Integer targetFolderId) {
        var file = repository.findFileById(fileId).orElse(null);
        if (file == null || file.stationId() != stationId) return MoveResult.refused(null, KbRefusalReason.NOT_FOUND);
        if (!accessService.effectiveLevel(access, null, fileId).covers(KbAccessLevel.MANAGE)) {
            return MoveResult.refused(file.name(), KbRefusalReason.NO_PERMISSION);
        }
        if (federationService.wouldOverreach(stationId, Set.of(), Set.of(fileId), targetFolderId)) {
            return MoveResult.refused(file.name(), KbRefusalReason.SHARE_TOO_WIDE);
        }

        repository.moveFile(fileId, targetFolderId);
        log.info("KB file {} moved into folder {} of station {}", fileId, targetFolderId, stationId);
        return MoveResult.moved(file.name());
    }

    /**
     * What a move would change about who reads an entry, both sides of it, so the dialog can say so
     * before anything happens rather than leave the reader to find out.
     *
     * @param stationId      the station the entry belongs to
     * @param folderId       the folder being moved, or {@code null} when moving a file
     * @param fileId         the file being moved, or {@code null} when moving a folder
     * @param targetFolderId the folder it would go into, or {@code null} for the tree root
     * @return how far it reaches now and how far it would reach there
     */
    public MovePreview preview(int stationId, Integer folderId, Integer fileId, Integer targetFolderId) {
        var mode =
                stationRepository.findById(stationId).map(Station::publicKbMode).orElse(PublicKbMode.OFF);
        Integer currentParent = folderId != null
                ? repository.findFolderById(folderId).map(f -> f.parentId()).orElse(null)
                : repository.findFileById(fileId).map(f -> f.folderId()).orElse(null);
        return new MovePreview(
                reachOf(mode, stationId, folderId, fileId, currentParent, true),
                reachOf(mode, stationId, folderId, fileId, targetFolderId, false));
    }

    /**
     * How far an entry reaches while it sits under one folder. The entry's own restrictions win over
     * everything else: an entry only some readers here may open is the narrow case even when a whole
     * federation could otherwise see it, because that is the sharper thing to know about it.
     */
    private KbReach reachOf(
            PublicKbMode mode, int stationId, Integer folderId, Integer fileId, Integer parentId, boolean current) {
        boolean publicly = current
                ? accessService.isPubliclyVisible(mode, folderId, fileId)
                : accessService.isPubliclyVisibleUnder(mode, parentId, folderId, fileId);
        if (publicly) return KbReach.PUBLIC;
        if (!accessService.findRestrictions(folderId, fileId).isEmpty()) return KbReach.NARROW;
        var partners = federationService.reachUnder(stationId, folderId, fileId, parentId);
        if (partners == PartnerReach.NAMED_STATIONS) return KbReach.NARROW;
        if (partners == PartnerReach.EVERY_PARTNER) return KbReach.FEDERATED;
        return KbReach.INTERNAL;
    }

    /**
     * The folders and articles a folder move takes with it, the folder itself included.
     */
    private MovedSubtree movedSubtree(int folderId) {
        var folderIds = new HashSet<Integer>();
        folderIds.add(folderId);
        folderIds.addAll(repository.descendantFolderIds(folderId));
        var fileIds = new HashSet<>(repository.findFileIdsInFolders(List.copyOf(folderIds)));
        return new MovedSubtree(folderIds, fileIds);
    }

    private record MovedSubtree(Set<Integer> folderIds, Set<Integer> fileIds) {}

    /**
     * Whether a move happened, and the entry it was about.
     *
     * @param moved  whether the entry sits somewhere else now
     * @param name   the entry's name, so a refusal can name it rather than count it, and
     *               {@code null} only when there was no entry to name
     * @param reason why it stayed where it was, or {@code null} when it moved
     */
    public record MoveResult(boolean moved, String name, KbRefusalReason reason) {
        public static MoveResult moved(String name) {
            return new MoveResult(true, name, null);
        }

        public static MoveResult refused(String name, KbRefusalReason reason) {
            return new MoveResult(false, name, reason);
        }
    }

    /**
     * How far an entry is read, on the one scale the wiki marks entries with.
     */
    public enum KbReach {
        /** Only this station reads it. */
        INTERNAL,
        /** Some readers of this station, or some partner stations, and no others. */
        NARROW,
        /** Every partner station reads it, the public does not. */
        FEDERATED,
        /** It stands on the public wiki. */
        PUBLIC
    }

    /**
     * How far an entry reaches now and how far it would reach after a move.
     */
    public record MovePreview(KbReach before, KbReach after) {}
}
