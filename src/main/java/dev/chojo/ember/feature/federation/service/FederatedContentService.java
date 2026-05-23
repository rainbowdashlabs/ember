/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.service.QuizService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to federated content from partner stations.
 * For same-instance federation, this directly calls the service layer.
 * For cross-instance (future), this will use HTTP.
 */
@Singleton
public class FederatedContentService {

    private final FederationRepository federationRepository;
    private final FederationService federationService;
    private final KnowledgeBaseService kbService;
    private final QuizService quizService;
    private final TestProtocolService protocolService;

    @Inject
    public FederatedContentService(
            FederationRepository federationRepository,
            FederationService federationService,
            KnowledgeBaseService kbService,
            QuizService quizService,
            TestProtocolService protocolService) {
        this.federationRepository = federationRepository;
        this.federationService = federationService;
        this.kbService = kbService;
        this.quizService = quizService;
        this.protocolService = protocolService;
    }

    /**
     * Browse shared KB files from all active federation partners.
     */
    public List<SharedKbItem> browseSharedKb(int stationId) {
        var result = new ArrayList<SharedKbItem>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            int remoteStationId = partner.stationId() == stationId ? partner.partnerStationId() : partner.stationId();
            if (!federationService.hasCapability(partner.id(), "KB_SHARE", "IMPORT")) continue;

            // Same-instance: directly query the partner's KB
            var shares = federationRepository.findKbShares(remoteStationId);
            for (var share : shares) {
                if (share.fileId() != null) {
                    kbService
                            .findFile(share.fileId())
                            .ifPresent(file -> result.add(new SharedKbItem(file, null, remoteStationId, partner.id())));
                } else if (share.folderId() != null) {
                    kbService.findFolder(share.folderId()).ifPresent(folder -> {
                        // Add the folder and all files in it
                        result.add(new SharedKbItem(null, folder, remoteStationId, partner.id()));
                        for (var file : kbService.findFiles(remoteStationId, share.folderId())) {
                            result.add(new SharedKbItem(file, null, remoteStationId, partner.id()));
                        }
                    });
                }
            }

            // Update metadata cache
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
        return result;
    }

    /**
     * Browse shared quiz catalogs from all active federation partners.
     */
    public List<SharedQuizItem> browseSharedQuiz(int stationId) {
        var result = new ArrayList<SharedQuizItem>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            int remoteStationId = partner.stationId() == stationId ? partner.partnerStationId() : partner.stationId();
            if (!federationService.hasCapability(partner.id(), "QUIZ_SHARE", "IMPORT")) continue;

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
        return result;
    }

    /**
     * Browse shared test protocols from all active federation partners.
     */
    public List<SharedProtocolItem> browseSharedProtocols(int stationId) {
        var result = new ArrayList<SharedProtocolItem>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            int remoteStationId = partner.stationId() == stationId ? partner.partnerStationId() : partner.stationId();
            if (!federationService.hasCapability(partner.id(), "PROTOCOL_SHARE", "IMPORT")) continue;

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
        return result;
    }

    /**
     * Copy a shared KB file to the local station.
     */
    public KbFile copyKbFile(int fileId, int targetStationId, int createdBy) {
        var source = kbService.findFile(fileId).orElseThrow();
        var content = kbService.getMarkdownContent(fileId).orElse("");
        return kbService.createMarkdownFile(
                targetStationId, null, source.name(), source.description(), content, createdBy);
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

    // -- Response types --

    public record SharedKbItem(KbFile file, KbFolder folder, int sourceStationId, int partnerId) {}

    public record SharedQuizItem(QuizCatalog catalog, int sourceStationId, int partnerId) {}

    public record SharedProtocolItem(TestProtocol protocol, int sourceStationId, int partnerId) {}
}
