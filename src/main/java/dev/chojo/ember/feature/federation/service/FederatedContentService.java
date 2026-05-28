/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileSummary;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.service.QuizService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provides access to federated content from partner stations.
 * Automatically uses direct DB queries for local partners and HTTP for remote partners.
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
    private final StationRepository stationRepository;

    @Inject
    public FederatedContentService(
            FederationRepository federationRepository,
            FederationService federationService,
            FederationHttpClient httpClient,
            KnowledgeBaseService kbService,
            QuizService quizService,
            TestProtocolService protocolService,
            StationRepository stationRepository) {
        this.federationRepository = federationRepository;
        this.federationService = federationService;
        this.httpClient = httpClient;
        this.kbService = kbService;
        this.quizService = quizService;
        this.protocolService = protocolService;
        this.stationRepository = stationRepository;
    }

    private String getPrivateKey(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(Station::federationPrivateKey)
                .orElse(null);
    }

    // -- KB --

    public List<SharedKbItem> browseSharedKb(int stationId) {
        var futures = new ArrayList<CompletableFuture<List<SharedKbItem>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            if (!federationService.hasCapability(partner.id(), CapabilityType.KB_SHARE, Direction.IMPORT)) continue;
            int remoteStationId = resolvePartnerStationId(partner);

            futures.add(CompletableFuture.supplyAsync(() -> {
                var items = new ArrayList<SharedKbItem>();
                if (partner.isRemote()) {
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
                kbService.findFile(share.fileId()).ifPresent(file -> {
                    var summary = KbFileSummary.of(file);
                    result.add(new SharedKbItem(summary, remoteStationId, partner.id()));
                    federationRepository.upsertMetadataCache(
                            partner.id(), ContentType.KB, file.id(), file.name(), file.description());
                });
            } else if (share.folderId() != null) {
                for (var file : kbService.findFiles(remoteStationId, share.folderId())) {
                    var summary = KbFileSummary.of(file);
                    result.add(new SharedKbItem(summary, remoteStationId, partner.id()));
                    federationRepository.upsertMetadataCache(
                            partner.id(), ContentType.KB, file.id(), file.name(), file.description());
                }
            }
        }
    }

    private void browseSharedKbViaHttp(
            int localStationId, FederationPartner partner, int remoteStationId, List<SharedKbItem> result) {
        var files = httpClient.fetchSharedKbFiles(partner.remoteHost(), localStationId, getPrivateKey(localStationId));
        for (var remoteFile : files) {
            var summary = new KbFileSummary(
                    remoteFile.id(),
                    remoteStationId,
                    null,
                    remoteFile.name(),
                    remoteFile.description(),
                    KbFileType.valueOf(remoteFile.fileType() != null ? remoteFile.fileType() : "MARKDOWN"),
                    Instant.now(),
                    false);
            result.add(new SharedKbItem(summary, remoteStationId, partner.id()));
            federationRepository.upsertMetadataCache(
                    partner.id(), ContentType.KB, remoteFile.id(), remoteFile.name(), remoteFile.description());
        }
    }

    // -- Quiz --

    public List<SharedQuizItem> browseSharedQuiz(int stationId) {
        var futures = new ArrayList<CompletableFuture<List<SharedQuizItem>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            if (!federationService.hasCapability(partner.id(), CapabilityType.QUIZ_SHARE, Direction.IMPORT)) continue;
            int remoteStationId = resolvePartnerStationId(partner);

            futures.add(CompletableFuture.supplyAsync(() -> {
                var items = new ArrayList<SharedQuizItem>();
                if (partner.isRemote()) {
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
                quizService.findCatalog(share.catalogId()).ifPresent(catalog -> {
                    result.add(new SharedQuizItem(
                            catalog.id(), catalog.name(), catalog.description(), remoteStationId, partner.id()));
                    federationRepository.upsertMetadataCache(
                            partner.id(), ContentType.QUIZ, catalog.id(), catalog.name(), catalog.description());
                });
            }
        }
    }

    private void browseSharedQuizViaHttp(
            int localStationId, FederationPartner partner, int remoteStationId, List<SharedQuizItem> result) {
        var catalogs =
                httpClient.fetchSharedQuizCatalogs(partner.remoteHost(), localStationId, getPrivateKey(localStationId));
        for (var remoteCatalog : catalogs) {
            result.add(new SharedQuizItem(
                    remoteCatalog.id(),
                    remoteCatalog.name(),
                    remoteCatalog.description(),
                    remoteStationId,
                    partner.id()));
            federationRepository.upsertMetadataCache(
                    partner.id(),
                    ContentType.QUIZ,
                    remoteCatalog.id(),
                    remoteCatalog.name(),
                    remoteCatalog.description());
        }
    }

    // -- Protocols --

    public List<SharedProtocolItem> browseSharedProtocols(int stationId) {
        var futures = new ArrayList<CompletableFuture<List<SharedProtocolItem>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            if (!federationService.hasCapability(partner.id(), CapabilityType.PROTOCOL_SHARE, Direction.IMPORT))
                continue;
            int remoteStationId = resolvePartnerStationId(partner);

            futures.add(CompletableFuture.supplyAsync(() -> {
                var items = new ArrayList<SharedProtocolItem>();
                if (partner.isRemote()) {
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
                    result.add(new SharedProtocolItem(
                            proto.id(), proto.name(), proto.description(), remoteStationId, partner.id()));
                    federationRepository.upsertMetadataCache(
                            partner.id(), ContentType.PROTOCOL, proto.id(), proto.name(), proto.description());
                });
            }
        }
    }

    private void browseSharedProtocolsViaHttp(
            int localStationId, FederationPartner partner, int remoteStationId, List<SharedProtocolItem> result) {
        var protocols =
                httpClient.fetchSharedProtocols(partner.remoteHost(), localStationId, getPrivateKey(localStationId));
        for (var remoteProto : protocols) {
            result.add(new SharedProtocolItem(
                    remoteProto.id(), remoteProto.name(), remoteProto.description(), remoteStationId, partner.id()));
            federationRepository.upsertMetadataCache(
                    partner.id(),
                    ContentType.PROTOCOL,
                    remoteProto.id(),
                    remoteProto.name(),
                    remoteProto.description());
        }
    }

    // -- KB Search --

    public List<FederatedSearchResult> searchFederatedKb(int stationId, String query) {
        var futures = new ArrayList<CompletableFuture<List<FederatedSearchResult>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            if (!federationService.hasCapability(partner.id(), CapabilityType.KB_SHARE, Direction.IMPORT)) continue;
            int remoteStationId = resolvePartnerStationId(partner);
            String stationName = stationRepository
                    .findByUid(partner.partnerStationId())
                    .map(Station::name)
                    .orElse("?");
            String stationUid = partner.partnerStationId().toString();

            futures.add(CompletableFuture.supplyAsync(() -> {
                if (partner.isRemote()) {
                    return searchKbViaHttp(stationId, partner, remoteStationId, stationName, stationUid, query);
                } else {
                    return searchKbDirect(remoteStationId, stationName, stationUid, query);
                }
            }));
        }
        return collectResults(futures);
    }

    private List<FederatedSearchResult> searchKbDirect(
            int remoteStationId, String stationName, String stationUid, String query) {
        return kbService.searchWithSnippets(remoteStationId, query).stream()
                .map(r -> new FederatedSearchResult(KbFileSummary.of(r.file()), r.snippet(), stationName, stationUid))
                .toList();
    }

    private List<FederatedSearchResult> searchKbViaHttp(
            int localStationId,
            FederationPartner partner,
            int remoteStationId,
            String stationName,
            String stationUid,
            String query) {
        String privateKey = getPrivateKey(localStationId);
        if (privateKey == null) return List.of();
        var results = httpClient.searchKb(partner.remoteHost(), localStationId, privateKey, query);
        return results.stream()
                .map(r -> new FederatedSearchResult(
                        new KbFileSummary(
                                r.id(), remoteStationId, null, r.name(), r.description(), null, Instant.now(), false),
                        r.snippet(),
                        stationName,
                        stationUid))
                .toList();
    }

    // -- Single-entity fetch (local or remote, transparent to caller) --

    /**
     * Fetches a single KB file from a federated partner, transparently handling local/remote.
     */
    public KbFile getFederatedKbFile(int localStationId, UUID partnerStationUid, int fileId) {
        var partner = resolveActivePartner(localStationId, partnerStationUid);
        if (partner.isRemote()) {
            String json = httpClient.signedGetJson(
                    partner.remoteHost(), "/remote/kb/files/" + fileId, localStationId, getPrivateKey(localStationId));
            if (json == null) throw new IllegalStateException("Failed to fetch file from remote partner");
            try {
                return httpClient.getMapper().readValue(json, KbFile.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse remote KB file response", e);
            }
        }
        var file = kbService.findFile(fileId).orElseThrow();
        int partnerStationId = resolvePartnerStationId(partner);
        if (file.stationId() != partnerStationId) {
            throw new IllegalArgumentException("File does not belong to this partner");
        }
        return file;
    }

    /**
     * Fetches KB file content from a federated partner, transparently handling local/remote.
     */
    public String getFederatedKbFileContent(int localStationId, UUID partnerStationUid, int fileId) {
        var partner = resolveActivePartner(localStationId, partnerStationUid);
        if (partner.isRemote()) {
            return httpClient.fetchKbFileContent(
                    partner.remoteHost(), fileId, localStationId, getPrivateKey(localStationId));
        }
        var file = kbService.findFile(fileId).orElseThrow();
        int partnerStationId = resolvePartnerStationId(partner);
        if (file.stationId() != partnerStationId) {
            throw new IllegalArgumentException("File does not belong to this partner");
        }
        return kbService.getMarkdownContent(fileId).orElse("");
    }

    /**
     * Fetches a single quiz catalog with categories and questions from a federated partner.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFederatedQuizCatalog(int localStationId, UUID partnerStationUid, int catalogId) {
        var partner = resolveActivePartner(localStationId, partnerStationUid);
        if (partner.isRemote()) {
            String json = httpClient.signedGetJson(
                    partner.remoteHost(),
                    "/remote/quiz/catalogs/" + catalogId,
                    localStationId,
                    getPrivateKey(localStationId));
            if (json == null) throw new IllegalStateException("Failed to fetch catalog from remote partner");
            try {
                return httpClient.getMapper().readValue(json, Map.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse remote catalog response", e);
            }
        }
        var catalog = quizService.findCatalog(catalogId).orElseThrow();
        int partnerStationId = resolvePartnerStationId(partner);
        if (catalog.stationId() != partnerStationId) {
            throw new IllegalArgumentException("Catalog does not belong to this partner");
        }
        var categories = quizService.findCategories(catalog.stationId());
        var questions = quizService.findQuestions(catalog.id());
        return Map.of("catalog", catalog, "categories", categories, "questions", questions);
    }

    /**
     * Fetches a single test protocol with sections and items from a federated partner.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFederatedProtocol(int localStationId, UUID partnerStationUid, int protocolId) {
        var partner = resolveActivePartner(localStationId, partnerStationUid);
        if (partner.isRemote()) {
            String json = httpClient.signedGetJson(
                    partner.remoteHost(),
                    "/remote/protocols/" + protocolId,
                    localStationId,
                    getPrivateKey(localStationId));
            if (json == null) throw new IllegalStateException("Failed to fetch protocol from remote partner");
            try {
                return httpClient.getMapper().readValue(json, Map.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse remote protocol response", e);
            }
        }
        var protocol = protocolService.findProtocol(protocolId).orElseThrow();
        int partnerStationId = resolvePartnerStationId(partner);
        if (protocol.stationId() != partnerStationId) {
            throw new IllegalArgumentException("Protocol does not belong to this partner");
        }
        var sections = protocolService.findSections(protocolId);
        var items = protocolService.findAllItemsByProtocol(protocolId);
        return Map.of("protocol", protocol, "sections", sections, "items", items);
    }

    private FederationPartner resolveActivePartner(int localStationId, UUID partnerStationUid) {
        var partner = federationRepository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner"));
        if (partner.status() != FederationPartner.FederationStatus.ACTIVE) {
            throw new IllegalArgumentException("Partner is not active");
        }
        return partner;
    }

    // -- Copy operations --

    public KbFile copyKbFile(int fileId, int targetStationId, int createdBy) {
        var source = kbService.findFile(fileId).orElseThrow();
        String content;
        var partner = findPartnerForStation(targetStationId, source.stationId());
        if (partner != null && partner.isRemote()) {
            content = httpClient.fetchKbFileContent(
                    partner.remoteHost(), fileId, targetStationId, getPrivateKey(targetStationId));
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

    public QuizCatalog copyQuizCatalog(int catalogId, int targetStationId) {
        var source = quizService.findCatalog(catalogId).orElseThrow();
        var newCatalog = quizService.createCatalog(
                targetStationId, source.name(), source.description(), source.trainingEnabled());

        var categories = quizService.findCategories(source.id());
        var categoryMap = new HashMap<Integer, Integer>();
        for (var cat : categories) {
            var newCat = quizService.createCategory(newCatalog.id(), cat.name(), cat.description(), cat.position());
            categoryMap.put(cat.id(), newCat.id());
        }

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
                    q.config() != null ? q.config() : new QuestionConfig.Unknown(),
                    q.position());
        }
        return newCatalog;
    }

    public TestProtocol copyProtocol(int protocolId, int targetStationId) {
        var source = protocolService.findProtocol(protocolId).orElseThrow();
        var newProto = protocolService.createProtocol(
                targetStationId, source.name(), source.description(), source.passThreshold());

        var sections = protocolService.findSections(source.id());
        var sectionMap = new HashMap<Integer, Integer>();

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

    // -- Helpers --

    private FederationPartner findPartnerForStation(int localStationId, int remoteStationId) {
        var partners = federationService.findPartners(localStationId);
        for (var partner : partners) {
            int partnerRemoteId = resolvePartnerStationId(partner);
            if (partnerRemoteId == remoteStationId && partner.status() == FederationPartner.FederationStatus.ACTIVE) {
                return partner;
            }
        }
        return null;
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

    /**
     * Resolves the partner station UUID to its internal int ID.
     */
    private int resolvePartnerStationId(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
    }

    public record SharedKbItem(KbFileSummary file, int sourceStationId, int partnerId) {}

    public record SharedQuizItem(int id, String name, String description, int sourceStationId, int partnerId) {}

    public record SharedProtocolItem(int id, String name, String description, int sourceStationId, int partnerId) {}

    public record FederatedSearchResult(KbFileSummary file, String snippet, String stationName, String stationUid) {}
}
