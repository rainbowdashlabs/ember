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
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunMember;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import dev.chojo.ember.feature.protocol.repository.TestProtocolRepository;
import dev.chojo.ember.feature.protocol.route.RemoteTestProtocolRoutes;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
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
import java.util.stream.Collectors;

@Singleton
public class TestProtocolService {
    private static final Logger log = LoggerFactory.getLogger(TestProtocolService.class);

    private final TestProtocolRepository repository;
    private final FederationService federationService;
    private final FederationRepository federationRepository;
    private final FederationHttpClient federationHttpClient;
    private final StationRepository stationRepository;
    private final FederationFanout fanout;
    private final FederationEntityResolver entityResolver;

    @Inject
    public TestProtocolService(
            TestProtocolRepository repository,
            FederationService federationService,
            FederationRepository federationRepository,
            FederationHttpClient federationHttpClient,
            StationRepository stationRepository,
            FederationFanout fanout,
            FederationEntityResolver entityResolver) {
        this.repository = repository;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.federationHttpClient = federationHttpClient;
        this.stationRepository = stationRepository;
        this.fanout = fanout;
        this.entityResolver = entityResolver;
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
        TestProtocol protocol = repository.createProtocol(stationId, name, description, passThreshold);
        log.info("Created test protocol {} (name='{}') in station {}", protocol.id(), name, stationId);
        return protocol;
    }

    public boolean updateProtocol(int id, String name, String description, Integer passThreshold) {
        boolean updated = repository.updateProtocol(id, name, description, passThreshold);
        if (updated) log.info("Updated test protocol {} (name='{}')", id, name);
        else log.warn("Update of test protocol {} did not change any row", id);
        return updated;
    }

    public boolean deleteProtocol(int id) {
        boolean deleted = repository.deleteProtocol(id);
        if (deleted) log.info("Deleted test protocol {}", id);
        else log.warn("Delete of test protocol {} did not change any row", id);
        return deleted;
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
        TestProtocolSection section =
                repository.createSection(protocolId, parentId, name, description, maxPoints, passThreshold, position);
        log.info(
                "Created section {} (name='{}', parentId={}) in test protocol {}",
                section.id(),
                name,
                parentId,
                protocolId);
        return section;
    }

    public boolean updateSection(
            int id, String name, String description, Integer maxPoints, Integer passThreshold, int position) {
        boolean updated = repository.updateSection(id, name, description, maxPoints, passThreshold, position);
        if (updated) log.info("Updated section {} (name='{}')", id, name);
        else log.warn("Update of section {} did not change any row", id);
        return updated;
    }

    public boolean deleteSection(int id) {
        boolean deleted = repository.deleteSection(id);
        if (deleted) log.info("Deleted section {}", id);
        else log.warn("Delete of section {} did not change any row", id);
        return deleted;
    }

    // -- Items --

    public List<TestProtocolItem> findItems(int sectionId) {
        return repository.findItems(sectionId);
    }

    public List<TestProtocolItem> findAllItemsByProtocol(int protocolId) {
        return repository.findAllItemsByProtocol(protocolId);
    }

    public TestProtocolItem createItem(int sectionId, String label, String description, double points, int position) {
        TestProtocolItem item = repository.createItem(sectionId, label, description, points, position);
        log.info("Created protocol item {} (label='{}', points={}) in section {}", item.id(), label, points, sectionId);
        return item;
    }

    public boolean updateItem(int id, String label, String description, double points, int position) {
        boolean updated = repository.updateItem(id, label, description, points, position);
        if (updated) log.info("Updated protocol item {} (label='{}', points={})", id, label, points);
        else log.warn("Update of protocol item {} did not change any row", id);
        return updated;
    }

    public boolean deleteItem(int id) {
        boolean deleted = repository.deleteItem(id);
        if (deleted) log.info("Deleted protocol item {}", id);
        else log.warn("Delete of protocol item {} did not change any row", id);
        return deleted;
    }

    // -- Runs --

    public List<TestProtocolRun> findRuns(int stationId) {
        return repository.findRuns(stationId);
    }

    public Optional<TestProtocolRun> findRun(int id) {
        return repository.findRunById(id);
    }

    public TestProtocolRun createRun(int protocolId, int stationId, String name, LocalDate testDate, int createdBy) {
        TestProtocolRun run = repository.createRun(protocolId, stationId, name, testDate, createdBy);
        log.info(
                "Created protocol run {} (name='{}') for protocol {} in station {} by member {}",
                run.id(),
                name,
                protocolId,
                stationId,
                createdBy);
        return run;
    }

    public boolean updateRun(int id, String name, LocalDate testDate) {
        boolean updated = repository.updateRun(id, name, testDate);
        if (updated) log.info("Updated protocol run {} (name='{}')", id, name);
        else log.warn("Update of protocol run {} did not change any row", id);
        return updated;
    }

    public boolean closeRun(int id) {
        boolean closed = repository.closeRun(id);
        if (closed) log.info("Closed protocol run {}", id);
        else log.warn("Close of protocol run {} did not change any row", id);
        return closed;
    }

    public boolean deleteRun(int id) {
        boolean deleted = repository.deleteRun(id);
        if (deleted) log.info("Deleted protocol run {}", id);
        else log.warn("Delete of protocol run {} did not change any row", id);
        return deleted;
    }

    // -- Run Members --

    public List<TestProtocolRunMember> findRunMembers(int runId) {
        return repository.findRunMembers(runId);
    }

    public Optional<TestProtocolRunMember> findRunMember(int runId, int memberId) {
        return repository.findRunMember(runId, memberId);
    }

    public TestProtocolRunMember addRunMember(int runId, int memberId) {
        TestProtocolRunMember runMember = repository.addRunMember(runId, memberId);
        log.info("Added member {} to protocol run {}", memberId, runId);
        return runMember;
    }

    public void addRunMembers(int runId, List<Integer> memberIds) {
        for (int memberId : memberIds) {
            repository.addRunMember(runId, memberId);
        }
        log.info("Added {} members to protocol run {}", memberIds.size(), runId);
    }

    public boolean lockMember(int runId, int memberId, int lockedBy) {
        var rm = repository.findRunMember(runId, memberId);
        boolean locked = rm.filter(testProtocolRunMember -> repository.lockMember(testProtocolRunMember.id(), lockedBy))
                .isPresent();
        if (locked) log.info("Locked member {} on protocol run {} by member {}", memberId, runId, lockedBy);
        else log.warn("Lock of member {} on protocol run {} did not change any row", memberId, runId);
        return locked;
    }

    public boolean unlockMember(int runId, int memberId) {
        var rm = repository.findRunMember(runId, memberId);
        boolean unlocked = rm.filter(testProtocolRunMember -> repository.unlockMember(testProtocolRunMember.id()))
                .isPresent();
        if (unlocked) log.info("Unlocked member {} on protocol run {}", memberId, runId);
        else log.warn("Unlock of member {} on protocol run {} did not change any row", memberId, runId);
        return unlocked;
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
        log.info(
                "Saved {} checks for member {} on protocol run {} by member {} (score={})",
                checks.size(),
                memberId,
                runId,
                checkedBy,
                score);
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
            log.info("Unmarked section {} done for member {} on protocol run {}", sectionId, memberId, runId);
        } else {
            repository.markSectionDone(runMemberId, sectionId, doneBy);
            log.info(
                    "Marked section {} done for member {} on protocol run {} by member {}",
                    sectionId,
                    memberId,
                    runId,
                    doneBy);
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

        boolean completed = repository.completeMember(runMemberId, totalScore);
        if (completed) {
            log.info(
                    "Completed member {} on protocol run {} (protocol {}, score={})",
                    memberId,
                    runId,
                    protocolId,
                    totalScore);
        } else {
            log.warn("Completion of member {} on protocol run {} did not change any row", memberId, runId);
        }
        return completed;
    }

    // -- Federated protocols --

    public List<SharedProtocolItem> browseSharedProtocols(int stationId) {
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(p -> federationService.hasCapability(p, CapabilityType.PROTOCOL_SHARE, Direction.IMPORT))
                .toList();
        return fanout.fanOut(
                partners,
                partner -> browseSharedProtocolsDirect(resolvePartnerStationId(partner), partner),
                partner -> browseSharedProtocolsViaHttp(stationId, partner, resolvePartnerStationId(partner)));
    }

    /**
     * Lists the protocols federation partners share with the given station, with the owning
     * partner station's display name already resolved.
     *
     * @param stationId the station browsing shared protocols
     * @return the shared protocols with a display name per entry
     */
    public List<SharedProtocolView> browseSharedProtocolViews(int stationId) {
        return browseSharedProtocols(stationId).stream()
                .map(item -> {
                    var partner = federationRepository
                            .findPartnerById(item.partnerId())
                            .orElse(null);
                    return new SharedProtocolView(
                            item.id(),
                            item.name(),
                            item.description(),
                            FederationDisplayNames.partnerName(stationRepository, partner, "Unknown"),
                            partner != null ? partner.partnerStationId().toString() : null);
                })
                .toList();
    }

    public FederatedProtocolDetail getFederatedProtocol(int localStationId, UUID partnerStationUid, int protocolId) {
        return entityResolver.resolve(
                localStationId,
                partnerStationUid,
                RemoteTestProtocolRoutes.GET_PROTOCOL.at(protocolId),
                FederatedProtocolDetail.class,
                "protocol",
                partner -> {
                    var protocol = findProtocol(protocolId).orElseThrow();
                    if (protocol.stationId() != resolvePartnerStationId(partner)) {
                        throw new BadRequestResponse("Protocol does not belong to this partner");
                    }
                    var sections = findSections(protocolId);
                    var items = findAllItemsByProtocol(protocolId);
                    return new FederatedProtocolDetail(protocol, sections, items);
                });
    }

    public TestProtocol copyProtocol(int protocolId, int targetStationId) {
        var source = findProtocol(protocolId).orElseThrow();
        var newProto = createProtocol(targetStationId, source.name(), source.description(), source.passThreshold());
        log.info(
                "Copying test protocol {} into new protocol {} at station {}",
                protocolId,
                newProto.id(),
                targetStationId);

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

    public List<RemoteProtocol> fetchSharedProtocols(
            String remoteHost, UUID partnerStationUid, int localStationId, String localPrivateKeyBase64) {
        return federationHttpClient.getList(
                remoteHost,
                RemoteTestProtocolRoutes.BROWSE_PROTOCOLS.at(),
                partnerStationUid,
                localStationId,
                localPrivateKeyBase64,
                RemoteProtocol.class);
    }

    private List<SharedProtocolItem> browseSharedProtocolsDirect(int remoteStationId, FederationPartner partner) {
        var result = new ArrayList<SharedProtocolItem>();
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
        return result;
    }

    private List<SharedProtocolItem> browseSharedProtocolsViaHttp(
            int localStationId, FederationPartner partner, int remoteStationId) {
        var result = new ArrayList<SharedProtocolItem>();
        var protocols = fetchSharedProtocols(
                partner.remoteHost(), partner.partnerStationId(), localStationId, getPrivateKey(localStationId));
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
        return result;
    }

    // -- Federation helpers --

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

    public record FederatedProtocolDetail(
            TestProtocol protocol, List<TestProtocolSection> sections, List<TestProtocolItem> items) {}

    // -- Federation HTTP convenience methods --

    public record SharedProtocolItem(int id, String name, String description, int sourceStationId, int partnerId) {}

    /**
     * A shared protocol as shown to users, carrying the owning partner station's display name.
     */
    /**
     * A protocol shared by a partner. The station UUID addresses the serving station on the
     * federated read routes and is null when the partnership behind it can no longer be resolved.
     */
    public record SharedProtocolView(int id, String name, String description, String stationName, String stationUid) {}

    public record RemoteProtocol(int id, String name, String description) {}
}
