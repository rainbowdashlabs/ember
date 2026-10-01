/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.KnowledgeBaseRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.knowledgebase.entity.KbFavourite;
import dev.chojo.ember.feature.knowledgebase.entity.KbFavouriteTarget;
import dev.chojo.ember.feature.knowledgebase.repository.KbFavouriteRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService.PartnerEntry;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * What one member marked in the wiki to reach quickly: files and folders of this station, and those
 * a partner shares.
 *
 * <p>This station's entries are followed live and filtered by the reader's access, so a favourite
 * of something the reader may no longer read is hidden rather than deleted and comes back with the
 * access. A partner's entries cannot be followed from here: what the partner said about one is kept
 * when it is marked and whenever it is opened again, and whether the reader may still see it is the
 * partner's answer at that moment.
 */
@Singleton
public class KbFavouriteService {
    private static final Logger log = LoggerFactory.getLogger(KbFavouriteService.class);

    private final KbFavouriteRepository repository;
    private final KnowledgeBaseService knowledgeBase;
    private final KbAccessService accessService;
    private final KnowledgeBaseFederationService federation;
    private final StationRepository stationRepository;

    @Inject
    public KbFavouriteService(
            KbFavouriteRepository repository,
            KnowledgeBaseService knowledgeBase,
            KbAccessService accessService,
            KnowledgeBaseFederationService federation,
            StationRepository stationRepository) {
        this.repository = repository;
        this.knowledgeBase = knowledgeBase;
        this.accessService = accessService;
        this.federation = federation;
        this.stationRepository = stationRepository;
    }

    /**
     * The reader's favourites, newest first: this station's entries they may currently read, and
     * every partner entry as it was last described.
     */
    public List<KbFavourite> list(MemberAccess access) {
        return repository.findByMember(access.memberId()).stream()
                .filter(favourite -> readable(access, favourite))
                .toList();
    }

    /**
     * Marks a file or folder of this station.
     *
     * @throws RefusalResponse when the entry is not this station's or the reader may not read it,
     *     which is the same answer the wiki gives for either
     */
    public KbFavourite markLocal(int stationId, MemberAccess access, KbFavouriteTarget target, int entryId) {
        if (target.isPartner() || !isOwnReadable(stationId, access, target, entryId)) {
            throw KnowledgeBaseRefusal.KB_FAVOURITE_ENTRY_NOT_HERE_OR_NOT_YOURS.raise();
        }
        repository.addLocal(access.memberId(), target, entryId);
        log.debug("Member {} marked KB {} {} as a favourite", access.memberId(), target, entryId);
        return repository
                .findLocal(access.memberId(), target, entryId)
                .orElseThrow(KnowledgeBaseRefusal.KB_FAVOURITE_NOT_READ_BACK_AFTER_MARKING::raise);
    }

    /**
     * Marks a partner's file or folder, after asking the partner about it. The partner's answer is
     * both the check that it is shared with this station and where the kept name comes from.
     *
     * @throws RefusalResponse when the partner does not share the entry with this station
     */
    public KbFavourite markPartner(
            int stationId,
            int memberId,
            @Nullable StationUserType readerUserType,
            KbFavouriteTarget target,
            UUID partnerStationUid,
            int entryId) {
        if (!target.isPartner()) throw KnowledgeBaseRefusal.KB_FAVOURITE_TARGET_NOT_AT_A_PARTNER.raise();
        PartnerEntry entry = target == KbFavouriteTarget.PARTNER_FILE
                ? federation.describePartnerFile(stationId, partnerStationUid, entryId)
                : federation.describePartnerFolder(stationId, partnerStationUid, entryId, readerUserType);
        repository.addPartner(
                memberId, target, partnerStationUid, entryId, entry.title(), entry.fileType(), entry.stationName());
        log.debug("Member {} marked partner {} {} of {} as a favourite", memberId, target, entryId, partnerStationUid);
        return repository
                .findPartner(memberId, target, partnerStationUid, entryId)
                .orElseThrow(KnowledgeBaseRefusal.KB_PARTNER_FAVOURITE_NOT_READ_BACK_AFTER_MARKING::raise);
    }

    /**
     * Removes one of the reader's favourites, whatever their access to its entry now is, so nobody
     * is left with a mark they cannot take off.
     *
     * @return whether it was theirs to remove
     */
    public boolean unmark(int memberId, int favouriteId) {
        boolean removed = repository.delete(favouriteId, memberId);
        if (removed) log.debug("Member {} removed favourite {}", memberId, favouriteId);
        return removed;
    }

    /**
     * Brings the kept name of a partner file up to date after the partner has served it, for every
     * member who marked it.
     */
    public void refreshPartnerFile(UUID partnerStationUid, int fileId, PartnerEntry entry) {
        repository.refreshPartner(
                KbFavouriteTarget.PARTNER_FILE,
                partnerStationUid,
                fileId,
                entry.title(),
                entry.fileType(),
                entry.stationName());
    }

    /**
     * Carries a favourite of a partner's file over to this station's copy of it, so copying a file
     * one keeps at hand does not lose it from the favourites.
     */
    public void carryOverToCopy(int memberId, int copiedFileId) {
        var copy = knowledgeBase.findFile(copiedFileId).orElse(null);
        Integer sourceFileId = copy == null ? null : copy.sourceFileId();
        Integer sourceStationId = copy == null ? null : copy.sourceStationId();
        if (sourceFileId == null || sourceStationId == null) return;
        UUID sourceStation = stationRepository.resolveUid(sourceStationId);
        if (sourceStation == null) return;
        boolean marked = repository
                .findPartner(memberId, KbFavouriteTarget.PARTNER_FILE, sourceStation, sourceFileId)
                .isPresent();
        if (marked) repository.addLocal(memberId, KbFavouriteTarget.FILE, copiedFileId);
    }

    private boolean readable(MemberAccess access, KbFavourite favourite) {
        return switch (favourite.target()) {
            case FILE -> accessService.canAccess(access, null, favourite.entryId());
            case FOLDER -> accessService.canAccess(access, favourite.entryId(), null);
            case PARTNER_FILE, PARTNER_FOLDER -> true;
        };
    }

    private boolean isOwnReadable(int stationId, MemberAccess access, KbFavouriteTarget target, int entryId) {
        return switch (target) {
            case FILE ->
                knowledgeBase
                                .findFile(entryId)
                                .filter(file -> file.stationId() == stationId)
                                .isPresent()
                        && accessService.canAccess(access, null, entryId);
            case FOLDER ->
                knowledgeBase
                                .findFolder(entryId)
                                .filter(folder -> folder.stationId() == stationId)
                                .isPresent()
                        && accessService.canAccess(access, entryId, null);
            case PARTNER_FILE, PARTNER_FOLDER -> false;
        };
    }
}
