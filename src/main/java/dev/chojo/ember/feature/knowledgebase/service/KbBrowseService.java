/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileSummary;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One level of a station's knowledge base as a reader browses it: what they may see there, what
 * they may do with each entry, and how far each entry reaches beyond the station.
 */
@Singleton
public class KbBrowseService {
    private final KnowledgeBaseService knowledgeBase;
    private final KbAccessService access;
    private final KnowledgeBaseFederationService federation;
    private final StationService stations;

    @Inject
    public KbBrowseService(
            KnowledgeBaseService knowledgeBase,
            KbAccessService access,
            KnowledgeBaseFederationService federation,
            StationService stations) {
        this.knowledgeBase = knowledgeBase;
        this.access = access;
        this.federation = federation;
        this.stations = stations;
    }

    /**
     * The folders and files of one level.
     *
     * <p>A knowledge manager sees every entry; anybody else only what their access reaches.
     *
     * @param folderId  the folder, or null for the top level
     * @param reader    what the reader may do, as the grants resolve it
     * @param sees  whether the reader manages the knowledge base, which shows every entry
     */
    public BrowseResponse browse(int stationId, Integer folderId, MemberAccess reader, boolean sees) {
        var folders = knowledgeBase.findFolders(stationId, folderId);
        var files = knowledgeBase.findFiles(stationId, folderId);
        if (!sees) {
            folders = folders.stream()
                    .filter(folder -> access.canAccess(reader, folder.id(), null))
                    .toList();
            files = files.stream()
                    .filter(file -> access.canAccess(reader, null, file.id()))
                    .toList();
        }
        var levels = access.childLevels(
                reader,
                folderId,
                folders.stream()
                        .map(folder -> new KbAccessService.ChildNode(folder.id(), folder.restrictionMode()))
                        .toList(),
                files.stream()
                        .map(file -> new KbAccessService.ChildNode(file.id(), file.restrictionMode()))
                        .toList());
        var mode = stations.findById(stationId).map(Station::publicKbMode).orElse(PublicKbMode.OFF);
        return new BrowseResponse(
                folderId != null ? knowledgeBase.findFolder(folderId).orElse(null) : null,
                folders,
                files.stream().map(KbFileSummary::of).toList(),
                access.effectiveLevel(reader, folderId, null),
                levels.folders(),
                levels.files(),
                reachOf(stationId, folders.stream().map(KbFolder::id).toList(), mode, true),
                reachOf(stationId, files.stream().map(KbFile::id).toList(), mode, false));
    }

    /**
     * How far each entry of one level reaches: onto the public web, out to every partner station, or
     * out to some of them only.
     *
     * <p>An entry restricted to certain readers here counts as the narrow case even when it is shared
     * with every partner, because the sharper thing to know about it is that not everyone who meets it
     * may open it.
     */
    private Reach reachOf(int stationId, List<Integer> ids, PublicKbMode mode, boolean folders) {
        var narrowed = federation.narrowlyShared(stationId, folders);
        var opened = federation.broadlyShared(stationId, folders);
        var publicly = ids.stream()
                .filter(id -> access.isPubliclyVisible(mode, folders ? id : null, folders ? null : id))
                .collect(Collectors.toSet());
        var narrowly = ids.stream()
                .filter(id -> narrowed.contains(id)
                        || !access.findRestrictions(folders ? id : null, folders ? null : id)
                                .isEmpty())
                .collect(Collectors.toSet());
        var federated = ids.stream()
                .filter(opened::contains)
                .filter(id -> !narrowly.contains(id))
                .collect(Collectors.toSet());
        return new Reach(publicly, federated, narrowly);
    }

    /**
     * A folder's contents together with what the caller may do with each entry, so a listing can
     * offer exactly the actions that will be accepted rather than the ones the station permission
     * suggests. {@code currentLevel} is what the caller may do in the folder itself, which decides
     * whether anything may be created in it.
     */
    public record BrowseResponse(
            KbFolder currentFolder,
            List<KbFolder> folders,
            List<KbFileSummary> files,
            KbAccessLevel currentLevel,
            Map<Integer, KbAccessLevel> folderLevels,
            Map<Integer, KbAccessLevel> fileLevels,
            Reach folderReach,
            Reach fileReach) {}

    /**
     * How far each entry of one level reaches, so the screen can mark it.
     *
     * <p>Two facts per entry, and only two: whether it stands on the public wiki, and whether it is
     * shared beyond this station without being open to everyone here. Resolved once for the level
     * rather than once per drawn tile.
     *
     * @param publicly  the ids that are on the public wiki
     * @param federated the ids every partner station reads, which is not the same as nobody outside
     * @param narrowly  the ids shared with named stations, or restricted to some of this station's readers
     */
    public record Reach(Set<Integer> publicly, Set<Integer> federated, Set<Integer> narrowly) {}
}
