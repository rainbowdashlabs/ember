/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.service.QuizService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides access to federated content from partner stations.
 * For same-instance federation, this directly calls the service layer.
 * For cross-instance (future), this will use HTTP.
 */
@Singleton
public class FederatedContentService {
    private static final Logger log = LoggerFactory.getLogger(FederatedContentService.class);

    private final FederationRepository federationRepository;
    private final FederationService federationService;
    private final FederationHttpClient httpClient;
    private final KnowledgeBaseService kbService;
    private final QuizService quizService;
    private final TestProtocolService protocolService;
    private final Demo demo;

    @Inject
    public FederatedContentService(
            FederationRepository federationRepository,
            FederationService federationService,
            FederationHttpClient httpClient,
            KnowledgeBaseService kbService,
            QuizService quizService,
            TestProtocolService protocolService,
            Demo demo) {
        this.federationRepository = federationRepository;
        this.federationService = federationService;
        this.httpClient = httpClient;
        this.kbService = kbService;
        this.quizService = quizService;
        this.protocolService = protocolService;
        this.demo = demo;
    }

    /**
     * Browse shared KB files from all active federation partners.
     * Fetches from each partner in parallel.
     */
    public List<SharedKbItem> browseSharedKb(int stationId) {
        var futures = new ArrayList<CompletableFuture<List<SharedKbItem>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            int remoteStationId = partner.stationId() == stationId ? partner.partnerStationId() : partner.stationId();
            if (!federationService.hasCapability(partner.id(), "KB_SHARE", "IMPORT")) continue;

            futures.add(CompletableFuture.supplyAsync(() -> {
                var items = new ArrayList<SharedKbItem>();
                if (demo.federationForceHttp()) {
                    browseSharedKbViaHttp(stationId, partner, remoteStationId, items);
                } else {
                    browseSharedKbDirect(remoteStationId, partner, items);
                }
                return items;
            }));
        }
        return collectResults(futures);
    }

    private void browseSharedKbDirect(int remoteStationId, FederationPartner partner, List<SharedKbItem> result) {
        var shares = federationRepository.findKbShares(remoteStationId);
        for (var share : shares) {
            if (share.fileId() != null) {
                kbService
                        .findFile(share.fileId())
                        .ifPresent(file -> result.add(new SharedKbItem(file, null, remoteStationId, partner.id())));
            } else if (share.folderId() != null) {
                kbService.findFolder(share.folderId()).ifPresent(folder -> {
                    result.add(new SharedKbItem(null, folder, remoteStationId, partner.id()));
                    for (var file : kbService.findFiles(remoteStationId, share.folderId())) {
                        result.add(new SharedKbItem(file, null, remoteStationId, partner.id()));
                    }
                });
            }
        }
        for (var item : result) {
            if (item.file() != null) {
                federationRepository.upsertMetadataCache(
                        partner.id(),
                        "KB",
                        item.file().id(),
                        item.file().name(),
                        item.file().description());
            }
        }
    }

    private void browseSharedKbViaHttp(
            int localStationId, FederationPartner partner, int remoteStationId, List<SharedKbItem> result) {
        var files = httpClient.fetchSharedKbFiles(localStationId, partner.publicKey());
        for (var fileMap : files) {
            int fileId = ((Number) fileMap.get("id")).intValue();
            String name = (String) fileMap.getOrDefault("name", "");
            String description = (String) fileMap.getOrDefault("description", "");
            String fileType = (String) fileMap.getOrDefault("fileType", "MARKDOWN");
            var file = new KbFile(
                    fileId,
                    remoteStationId,
                    null,
                    name,
                    description,
                    KbFileType.valueOf(fileType),
                    null,
                    0,
                    null,
                    null,
                    null,
                    0,
                    0,
                    Instant.now(),
                    Instant.now(),
                    null,
                    null);
            result.add(new SharedKbItem(file, null, remoteStationId, partner.id()));
            federationRepository.upsertMetadataCache(partner.id(), "KB", fileId, name, description);
        }
    }

    /**
     * Browse shared quiz catalogs from all active federation partners.
     * Fetches from each partner in parallel.
     */
    public List<SharedQuizItem> browseSharedQuiz(int stationId) {
        var futures = new ArrayList<CompletableFuture<List<SharedQuizItem>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            int remoteStationId = partner.stationId() == stationId ? partner.partnerStationId() : partner.stationId();
            if (!federationService.hasCapability(partner.id(), "QUIZ_SHARE", "IMPORT")) continue;

            futures.add(CompletableFuture.supplyAsync(() -> {
                var items = new ArrayList<SharedQuizItem>();
                if (demo.federationForceHttp()) {
                    browseSharedQuizViaHttp(stationId, partner, remoteStationId, items);
                } else {
                    browseSharedQuizDirect(remoteStationId, partner, items);
                }
                return items;
            }));
        }
        return collectResults(futures);
    }

    private void browseSharedQuizDirect(int remoteStationId, FederationPartner partner, List<SharedQuizItem> result) {
        var shares = federationRepository.findQuizShares(remoteStationId);
        for (var share : shares) {
            if (share.catalogId() != null) {
                var catalog = quizService.findCatalog(share.catalogId());
                if (catalog.isPresent()) {
                    result.add(new SharedQuizItem(catalog.get(), remoteStationId, partner.id()));
                    federationRepository.upsertMetadataCache(
                            partner.id(),
                            "QUIZ",
                            catalog.get().id(),
                            catalog.get().name(),
                            catalog.get().description());
                }
            }
        }
    }

    private void browseSharedQuizViaHttp(
            int localStationId, FederationPartner partner, int remoteStationId, List<SharedQuizItem> result) {
        var catalogs = httpClient.fetchSharedQuizCatalogs(localStationId, partner.publicKey());
        for (var catMap : catalogs) {
            int id = ((Number) catMap.get("id")).intValue();
            String name = (String) catMap.getOrDefault("name", "");
            String description = (String) catMap.getOrDefault("description", "");
            // Fetch the actual catalog from the local DB to get a full object
            quizService.findCatalog(id).ifPresent(catalog -> {
                result.add(new SharedQuizItem(catalog, remoteStationId, partner.id()));
                federationRepository.upsertMetadataCache(partner.id(), "QUIZ", id, name, description);
            });
        }
    }

    /**
     * Browse shared test protocols from all active federation partners.
     * Fetches from each partner in parallel.
     */
    public List<SharedProtocolItem> browseSharedProtocols(int stationId) {
        var futures = new ArrayList<CompletableFuture<List<SharedProtocolItem>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            int remoteStationId = partner.stationId() == stationId ? partner.partnerStationId() : partner.stationId();
            if (!federationService.hasCapability(partner.id(), "PROTOCOL_SHARE", "IMPORT")) continue;

            futures.add(CompletableFuture.supplyAsync(() -> {
                var items = new ArrayList<SharedProtocolItem>();
                if (demo.federationForceHttp()) {
                    browseSharedProtocolsViaHttp(stationId, partner, remoteStationId, items);
                } else {
                    browseSharedProtocolsDirect(remoteStationId, partner, items);
                }
                return items;
            }));
        }
        return collectResults(futures);
    }

    private void browseSharedProtocolsDirect(
            int remoteStationId, FederationPartner partner, List<SharedProtocolItem> result) {
        var shares = federationRepository.findProtocolShares(remoteStationId);
        for (var share : shares) {
            if (share.protocolId() != null) {
                protocolService.findProtocol(share.protocolId()).ifPresent(proto -> {
                    result.add(new SharedProtocolItem(proto, remoteStationId, partner.id()));
                    federationRepository.upsertMetadataCache(
                            partner.id(), "PROTOCOL", proto.id(), proto.name(), proto.description());
                });
            }
        }
    }

    private void browseSharedProtocolsViaHttp(
            int localStationId, FederationPartner partner, int remoteStationId, List<SharedProtocolItem> result) {
        var protocols = httpClient.fetchSharedProtocols(localStationId, partner.publicKey());
        for (var protoMap : protocols) {
            int id = ((Number) protoMap.get("id")).intValue();
            String name = (String) protoMap.getOrDefault("name", "");
            String description = (String) protoMap.getOrDefault("description", "");
            protocolService.findProtocol(id).ifPresent(proto -> {
                result.add(new SharedProtocolItem(proto, remoteStationId, partner.id()));
                federationRepository.upsertMetadataCache(partner.id(), "PROTOCOL", id, name, description);
            });
        }
    }

    /**
     * Copy a shared KB file to the local station.
     * Keeps a reference to the original file and carries over the favourite status.
     */
    public KbFile copyKbFile(int fileId, int targetStationId, int createdBy) {
        var source = kbService.findFile(fileId).orElseThrow();
        String content;
        if (demo.federationForceHttp()) {
            content = fetchKbContentViaHttp(fileId, targetStationId, source.stationId());
        } else {
            content = kbService.getMarkdownContent(fileId).orElse("");
        }
        var copied = kbService.createMarkdownFile(
                targetStationId, null, source.name(), source.description(), content, createdBy);
        kbService.setSourceReference(copied.id(), source.id(), source.stationId());
        if (kbService.isFavourite(createdBy, fileId)) {
            kbService.addFavourite(createdBy, copied.id());
        }
        return kbService.findFile(copied.id()).orElseThrow();
    }

    private String fetchKbContentViaHttp(int fileId, int localStationId, int remoteStationId) {
        var partners = federationService.findPartners(localStationId);
        for (var partner : partners) {
            int partnerRemoteId =
                    partner.stationId() == localStationId ? partner.partnerStationId() : partner.stationId();
            if (partnerRemoteId == remoteStationId && partner.status() == FederationPartner.FederationStatus.ACTIVE) {
                return httpClient.fetchKbFileContent(fileId, localStationId, partner.publicKey());
            }
        }
        log.warn(
                "No active federation partner found for station {} -> {}, falling back to direct query",
                localStationId,
                remoteStationId);
        return kbService.getMarkdownContent(fileId).orElse("");
    }

    /**
     * Copy a shared quiz catalog to the local station.
     */
    public QuizCatalog copyQuizCatalog(int catalogId, int targetStationId) {
        var source = quizService.findCatalog(catalogId).orElseThrow();
        var newCatalog = quizService.createCatalog(
                targetStationId, source.name(), source.description(), source.trainingEnabled());

        // Copy categories
        var categories = quizService.findCategories(source.id());
        var categoryMap = new java.util.HashMap<Integer, Integer>(); // old ID -> new ID
        for (var cat : categories) {
            var newCat = quizService.createCategory(newCatalog.id(), cat.name(), cat.description(), cat.position());
            categoryMap.put(cat.id(), newCat.id());
        }

        // Copy questions
        var questions = quizService.findQuestions(source.id());
        for (var q : questions) {
            Integer newCatId = q.categoryId() != null ? categoryMap.get(q.categoryId()) : null;
            quizService.createQuestion(
                    newCatalog.id(),
                    newCatId,
                    q.questionType(),
                    q.title(),
                    q.description(),
                    q.imageUrl(),
                    q.points(),
                    q.autoPoints(),
                    q.configString(),
                    q.position());
        }
        return newCatalog;
    }

    /**
     * Copy a shared test protocol to the local station.
     */
    public TestProtocol copyProtocol(int protocolId, int targetStationId) {
        var source = protocolService.findProtocol(protocolId).orElseThrow();
        var newProto = protocolService.createProtocol(
                targetStationId, source.name(), source.description(), source.passThreshold());

        var sections = protocolService.findSections(source.id());
        var sectionMap = new java.util.HashMap<Integer, Integer>();

        // Copy top-level sections first
        for (var sec : sections) {
            if (sec.parentId() != null) continue;
            var newSec = protocolService.createSection(
                    newProto.id(),
                    null,
                    sec.name(),
                    sec.description(),
                    sec.maxPoints(),
                    sec.passThreshold(),
                    sec.position());
            sectionMap.put(sec.id(), newSec.id());
        }

        // Copy sub-sections
        for (var sec : sections) {
            if (sec.parentId() == null) continue;
            Integer newParentId = sectionMap.get(sec.parentId());
            var newSec = protocolService.createSection(
                    newProto.id(),
                    newParentId,
                    sec.name(),
                    sec.description(),
                    sec.maxPoints(),
                    sec.passThreshold(),
                    sec.position());
            sectionMap.put(sec.id(), newSec.id());
        }

        // Copy items
        var allItems = protocolService.findAllItemsByProtocol(source.id());
        for (var item : allItems) {
            Integer newSectionId = sectionMap.get(item.sectionId());
            if (newSectionId != null) {
                protocolService.createItem(
                        newSectionId, item.label(), item.description(), item.points(), item.position());
            }
        }

        return newProto;
    }

    private <T> List<T> collectResults(List<CompletableFuture<List<T>>> futures) {
        var allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allFuture.join();
        } catch (Exception e) {
            log.error("Error during parallel federation fetch", e);
        }
        var result = new ArrayList<T>();
        for (var future : futures) {
            try {
                result.addAll(future.get());
            } catch (Exception e) {
                log.error("Error collecting federation results", e);
            }
        }
        return result;
    }

    // -- Response types --

    public record SharedKbItem(KbFile file, KbFolder folder, int sourceStationId, int partnerId) {}

    public record SharedQuizItem(QuizCatalog catalog, int sourceStationId, int partnerId) {}

    public record SharedProtocolItem(TestProtocol protocol, int sourceStationId, int partnerId) {}
}
