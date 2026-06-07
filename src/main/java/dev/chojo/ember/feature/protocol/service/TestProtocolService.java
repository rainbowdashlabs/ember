/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunMember;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import dev.chojo.ember.feature.protocol.repository.TestProtocolRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Singleton
public class TestProtocolService {
    private static final Logger log = LoggerFactory.getLogger(TestProtocolService.class);

    private final TestProtocolRepository repository;
    private final FederationService federationService;
    private final FederationRepository federationRepository;
    private final FederationHttpClient federationHttpClient;
    private final StationRepository stationRepository;

    @Inject
    public TestProtocolService(
            TestProtocolRepository repository,
            FederationService federationService,
            FederationRepository federationRepository,
            FederationHttpClient federationHttpClient,
            StationRepository stationRepository) {
        this.repository = repository;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.federationHttpClient = federationHttpClient;
        this.stationRepository = stationRepository;
    }

    // -- Protocols --

    public List<TestProtocol> findProtocols(int stationId) {
        return repository.findProtocols(stationId);
    }

    public List<TestProtocol> searchProtocols(int stationId, String query) {
        return repository.searchProtocols(stationId, query);
    }

    public Optional<TestProtocol> findProtocol(int id) {
        return repository.findProtocolById(id);
    }

    public TestProtocol createProtocol(int stationId, String name, String description, Integer passThreshold) {
        return repository.createProtocol(stationId, name, description, passThreshold);
    }

    public boolean updateProtocol(int id, String name, String description, Integer passThreshold) {
        return repository.updateProtocol(id, name, description, passThreshold);
    }

    public boolean deleteProtocol(int id) {
        return repository.deleteProtocol(id);
    }

    // -- Sections --

    public List<TestProtocolSection> findSections(int protocolId) {
        return repository.findSections(protocolId);
    }

    public TestProtocolSection createSection(
            int protocolId,
            Integer parentId,
            String name,
            String description,
            Integer maxPoints,
            Integer passThreshold,
            int position) {
        return repository.createSection(protocolId, parentId, name, description, maxPoints, passThreshold, position);
    }

    public boolean updateSection(
            int id, String name, String description, Integer maxPoints, Integer passThreshold, int position) {
        return repository.updateSection(id, name, description, maxPoints, passThreshold, position);
    }

    public boolean deleteSection(int id) {
        return repository.deleteSection(id);
    }

    // -- Items --

    public List<TestProtocolItem> findItems(int sectionId) {
        return repository.findItems(sectionId);
    }

    public List<TestProtocolItem> findAllItemsByProtocol(int protocolId) {
        return repository.findAllItemsByProtocol(protocolId);
    }

    public TestProtocolItem createItem(int sectionId, String label, String description, double points, int position) {
        return repository.createItem(sectionId, label, description, points, position);
    }

    public boolean updateItem(int id, String label, String description, double points, int position) {
        return repository.updateItem(id, label, description, points, position);
    }

    public boolean deleteItem(int id) {
        return repository.deleteItem(id);
    }

    // -- Runs --

    public List<TestProtocolRun> findRuns(int stationId) {
        return repository.findRuns(stationId);
    }

    public Optional<TestProtocolRun> findRun(int id) {
        return repository.findRunById(id);
    }

    public TestProtocolRun createRun(int protocolId, int stationId, String name, LocalDate testDate, int createdBy) {
        return repository.createRun(protocolId, stationId, name, testDate, createdBy);
    }

    public boolean updateRun(int id, String name, LocalDate testDate) {
        return repository.updateRun(id, name, testDate);
    }

    public boolean closeRun(int id) {
        return repository.closeRun(id);
    }

    public boolean deleteRun(int id) {
        return repository.deleteRun(id);
    }

    // -- Run Members --

    public List<TestProtocolRunMember> findRunMembers(int runId) {
        return repository.findRunMembers(runId);
    }

    public Optional<TestProtocolRunMember> findRunMember(int runId, int memberId) {
        return repository.findRunMember(runId, memberId);
    }

    public TestProtocolRunMember addRunMember(int runId, int memberId) {
        return repository.addRunMember(runId, memberId);
    }

    public void addRunMembers(int runId, List<Integer> memberIds) {
        for (int memberId : memberIds) {
            repository.addRunMember(runId, memberId);
        }
    }

    public boolean lockMember(int runId, int memberId, int lockedBy) {
        var rm = repository.findRunMember(runId, memberId);
        return rm.filter(testProtocolRunMember -> repository.lockMember(testProtocolRunMember.id(), lockedBy))
                .isPresent();
    }

    public boolean unlockMember(int runId, int memberId) {
        var rm = repository.findRunMember(runId, memberId);
        return rm.filter(testProtocolRunMember -> repository.unlockMember(testProtocolRunMember.id()))
                .isPresent();
    }

    public void saveChecks(int runId, int memberId, Map<Integer, Boolean> checks, int checkedBy, int protocolId) {
        var rm = repository.findRunMember(runId, memberId);
        if (rm.isEmpty()) return;
        int runMemberId = rm.get().id();
        for (var entry : checks.entrySet()) {
            repository.upsertCheck(runMemberId, entry.getKey(), entry.getValue(), checkedBy);
        }
        // Recalculate and update score
        var allChecks = repository.findChecks(runMemberId);
        var allItems = repository.findAllItemsByProtocol(protocolId);
        var itemPoints = allItems.stream().collect(Collectors.toMap(TestProtocolItem::id, TestProtocolItem::points));
        double score = 0;
        for (var c : allChecks) {
            if (c.checked() && itemPoints.containsKey(c.itemId())) {
                score += itemPoints.get(c.itemId());
            }
        }
        repository.updateScore(runMemberId, score);
    }

    // -- Section Done --

    public List<Integer> findDoneSections(int runId, int memberId) {
        var rm = repository.findRunMember(runId, memberId);
        if (rm.isEmpty()) return List.of();
        return repository.findDoneSections(rm.get().id());
    }

    public void toggleSectionDone(int runId, int memberId, int sectionId, int doneBy) {
        var rm = repository.findRunMember(runId, memberId);
        if (rm.isEmpty()) return;
        int runMemberId = rm.get().id();
        var done = repository.findDoneSections(runMemberId);
        if (done.contains(sectionId)) {
            repository.unmarkSectionDone(runMemberId, sectionId);
        } else {
            repository.markSectionDone(runMemberId, sectionId, doneBy);
        }
    }

    public int countDoneSections(int runMemberId) {
        return repository.countDoneSections(runMemberId);
    }

    public List<TestProtocolRunCheck> findChecks(int runId, int memberId) {
        var rm = repository.findRunMember(runId, memberId);
        if (rm.isEmpty()) return List.of();
        return repository.findChecks(rm.get().id());
    }

    public boolean completeMember(int runId, int memberId, int protocolId) {
        var rm = repository.findRunMember(runId, memberId);
        if (rm.isEmpty()) return false;
        int runMemberId = rm.get().id();

        // Calculate total score from checked items
        var checks = repository.findChecks(runMemberId);
        var allItems = repository.findAllItemsByProtocol(protocolId);
        var itemPoints = allItems.stream().collect(Collectors.toMap(TestProtocolItem::id, TestProtocolItem::points));

        double totalScore = 0;
        for (var check : checks) {
            if (check.checked() && itemPoints.containsKey(check.itemId())) {
                totalScore += itemPoints.get(check.itemId());
            }
        }

        return repository.completeMember(runMemberId, totalScore);
    }

    // -- Federated protocols --

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
                findProtocol(share.protocolId()).ifPresent(proto -> {
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
        var protocols = fetchSharedProtocols(partner.remoteHost(), localStationId, getPrivateKey(localStationId));
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

    @SuppressWarnings("unchecked")
    public Map<String, Object> getFederatedProtocol(int localStationId, UUID partnerStationUid, int protocolId) {
        var partner = resolveActivePartner(localStationId, partnerStationUid);
        if (partner.isRemote()) {
            var result = federationHttpClient.get(
                    partner.remoteHost(),
                    "/remote/protocols/" + protocolId,
                    localStationId,
                    getPrivateKey(localStationId),
                    Map.class);
            if (result == null) throw new IllegalStateException("Failed to fetch protocol from remote partner");
            return result;
        }
        var protocol = findProtocol(protocolId).orElseThrow();
        int partnerStationId = resolvePartnerStationId(partner);
        if (protocol.stationId() != partnerStationId) {
            throw new IllegalArgumentException("Protocol does not belong to this partner");
        }
        var sections = findSections(protocolId);
        var items = findAllItemsByProtocol(protocolId);
        return Map.of("protocol", protocol, "sections", sections, "items", items);
    }

    public TestProtocol copyProtocol(int protocolId, int targetStationId) {
        var source = findProtocol(protocolId).orElseThrow();
        var newProto = createProtocol(targetStationId, source.name(), source.description(), source.passThreshold());

        var sections = findSections(source.id());
        var sectionMap = new HashMap<Integer, Integer>();

        for (var sec : sections) {
            if (sec.parentId() != null) continue;
            var newSec = createSection(
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
            var newSec = createSection(
                    newProto.id(),
                    newParentId,
                    sec.name(),
                    sec.description(),
                    sec.maxPoints(),
                    sec.passThreshold(),
                    sec.position());
            sectionMap.put(sec.id(), newSec.id());
        }

        var allItems = findAllItemsByProtocol(source.id());
        for (var item : allItems) {
            Integer newSectionId = sectionMap.get(item.sectionId());
            if (newSectionId != null) {
                createItem(newSectionId, item.label(), item.description(), item.points(), item.position());
            }
        }

        return newProto;
    }

    // -- Federation helpers --

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

    private FederationPartner resolveActivePartner(int localStationId, UUID partnerStationUid) {
        var partner = federationRepository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner"));
        if (partner.status() != FederationPartner.FederationStatus.ACTIVE) {
            throw new IllegalArgumentException("Partner is not active");
        }
        return partner;
    }

    private int resolvePartnerStationId(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
    }

    private String getPrivateKey(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(Station::federationPrivateKey)
                .orElse(null);
    }

    public record SharedProtocolItem(int id, String name, String description, int sourceStationId, int partnerId) {}

    // -- Federation HTTP convenience methods --

    public List<RemoteProtocol> fetchSharedProtocols(
            String remoteHost, int localStationId, String localPrivateKeyBase64) {
        return federationHttpClient.getList(
                remoteHost, "/remote/protocols", localStationId, localPrivateKeyBase64, RemoteProtocol.class);
    }

    public record RemoteProtocol(int id, String name, String description) {}
}
