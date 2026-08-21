/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Where a cluster's own knowledge, news and events live, and who signs them.
 *
 * <p>They live on the cluster's own station, which is the whole point of that station existing: the knowledge
 * base, the news list and the calendar are the ones a station already has, and they reach the member stations
 * over the federation pairs the cluster made. Nothing is copied and nothing is a second implementation.
 *
 * <p>The byline is the one thing that needed thinking about. An article names its author as a station member,
 * and a cluster member is not one. So writing at the cluster gives the writer a member row on the cluster's
 * own station, made the first time they write and never again. That is not the membership §1 refuses to
 * invent: the cluster's station is one nobody joins, appears in no switcher and has no member list anybody
 * browses. The row is a signature, and it carries no permission anywhere.
 */
@Singleton
public class ClusterContentService {
    private static final Logger log = LoggerFactory.getLogger(ClusterContentService.class);

    private final ClusterRepository clusterRepository;
    private final StationRepository stationRepository;
    private final StationMemberRepository memberRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final FederationRepository federationRepository;

    @Inject
    public ClusterContentService(
            ClusterRepository clusterRepository,
            StationRepository stationRepository,
            StationMemberRepository memberRepository,
            KnowledgeBaseService knowledgeBaseService,
            FederationRepository federationRepository) {
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
        this.memberRepository = memberRepository;
        this.knowledgeBaseService = knowledgeBaseService;
        this.federationRepository = federationRepository;
    }

    /**
     * The station a cluster's content lives on.
     *
     * @param clusterId the cluster
     * @return its own station
     */
    public int homeStationOf(int clusterId) {
        return requireCluster(clusterId).homeStationId();
    }

    /**
     * The member row that signs what somebody writes at the cluster, made on first use.
     *
     * @param clusterId the cluster being written for
     * @param accountId the account doing the writing
     * @return the member row on the cluster's own station
     */
    public StationMember authorFor(int clusterId, int accountId) {
        int homeStationId = homeStationOf(clusterId);
        return memberRepository
                .findByStationAndAccount(homeStationId, accountId)
                .orElseGet(() -> {
                    StationMember created = memberRepository.create(homeStationId, accountId);
                    log.info("Gave account {} a byline on the station of cluster {}", accountId, clusterId);
                    return created;
                });
    }

    /**
     * The same byline as an identity, which is what the content services take.
     *
     * @param clusterId the cluster being written for
     * @param accountId the account doing the writing
     * @return the author identity
     */
    public MemberIdentity authorIdentity(int clusterId, int accountId) {
        StationMember author = authorFor(clusterId, accountId);
        return new MemberIdentity(stationRepository.resolveUid(author.stationId()), author.uid());
    }

    // -- Knowledge base --

    /**
     * The cluster's knowledge folders, which are the ones on its own station.
     *
     * @param clusterId the cluster
     * @return its folders
     */
    public List<KbFolder> findFolders(int clusterId) {
        return knowledgeBaseService.findAllFolders(homeStationOf(clusterId));
    }

    /**
     * Creates a knowledge folder and shares it with the whole cluster in the same breath.
     *
     * <p>Unlike news and events, a knowledge folder does not travel on a capability alone: it needs a share
     * row saying so. Since the cluster's station partners with its member stations and nothing else, "all
     * partners" is exactly "all cluster stations", and there is no per-folder choice worth offering. A
     * cluster's knowledge is the cluster's knowledge.
     *
     * @param clusterId   the cluster
     * @param parentId    the folder to create it in, or {@code null} for the top
     * @param name        what it is called
     * @param description a sentence about it
     * @param accountId   the account creating it
     * @return the folder
     */
    public KbFolder createFolder(int clusterId, Integer parentId, String name, String description, int accountId) {
        if (name == null || name.isBlank()) throw new BadRequestResponse("A folder needs a name");
        int homeStationId = homeStationOf(clusterId);
        // Both columns are NOT NULL, and "no description" is a thing a caller may legitimately mean
        KbFolder folder = knowledgeBaseService.createFolder(
                homeStationId,
                parentId,
                name.trim(),
                description != null ? description : "",
                authorFor(clusterId, accountId).id());
        federationRepository.createKbShare(homeStationId, null, folder.id(), ShareScope.ALL_PARTNERS);
        log.info("Cluster {} added knowledge folder {} and shared it with its stations", clusterId, folder.id());
        return folder;
    }

    /**
     * The files in one of the cluster's knowledge folders.
     *
     * @param clusterId the cluster
     * @param folderId  the folder, or {@code null} for the top
     * @return its files
     */
    public List<KbFile> findFiles(int clusterId, Integer folderId) {
        return knowledgeBaseService.findFiles(homeStationOf(clusterId), folderId);
    }

    /**
     * Writes an article in the cluster's knowledge base and shares it with the whole cluster.
     *
     * @param clusterId   the cluster
     * @param folderId    the folder to put it in, or {@code null} for the top
     * @param name        what it is called
     * @param description a sentence about it
     * @param content     the article, in markdown
     * @param accountId   the account writing it
     * @return the file
     */
    public KbFile createArticle(
            int clusterId, Integer folderId, String name, String description, String content, int accountId) {
        if (name == null || name.isBlank()) throw new BadRequestResponse("An article needs a name");
        int homeStationId = homeStationOf(clusterId);
        KbFile file = knowledgeBaseService.createMarkdownFile(
                homeStationId,
                folderId,
                name.trim(),
                description != null ? description : "",
                content != null ? content : "",
                authorFor(clusterId, accountId).id());
        federationRepository.createKbShare(homeStationId, file.id(), null, ShareScope.ALL_PARTNERS);
        log.info("Cluster {} added knowledge article {} and shared it with its stations", clusterId, file.id());
        return file;
    }

    /**
     * Removes one of the cluster's knowledge articles.
     *
     * @param clusterId the cluster
     * @param fileId    the article
     */
    public void deleteArticle(int clusterId, int fileId) {
        int homeStationId = homeStationOf(clusterId);
        KbFile file = knowledgeBaseService.findFile(fileId).orElseThrow(() -> new NotFoundResponse("No such article"));
        if (file.stationId() != homeStationId) throw new NotFoundResponse("No such article");
        knowledgeBaseService.deleteFile(fileId);
    }

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(() -> new NotFoundResponse("No such cluster"));
    }
}
