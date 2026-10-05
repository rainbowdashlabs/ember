/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.api.refusal.TestProtocolRefusal;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunMember;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import dev.chojo.ember.feature.protocol.repository.TestProtocolRepository;
import dev.chojo.ember.feature.protocol.route.RemoteTestProtocolRoutes;
import dev.chojo.ember.feature.protocol.route.RemoteTestProtocolRoutes.RemoteProtocolDetail;
import dev.chojo.ember.feature.protocol.route.RemoteTestProtocolRoutes.RemoteProtocolSummary;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test protocols, their runs, and the protocols federation partners share with a station.
 *
 * <p>The protocols this station shares are served to a partner through one set of serving
 * functions, reached over the {@code /remote} routes from another instance and through the local
 * transport from this one, so a partner sees the same protocols wherever it lives.
 */
@Singleton
public class TestProtocolService implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(TestProtocolService.class);

    private final TestProtocolRepository repository;
    private final FederationService federationService;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final FederationFanout fanout;
    private final FederationEntityResolver entityResolver;
    private final FederationTransport transport;

    @Inject
    public TestProtocolService(
            TestProtocolRepository repository,
            FederationService federationService,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            FederationFanout fanout,
            FederationEntityResolver entityResolver,
            FederationTransport transport) {
        this.repository = repository;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.fanout = fanout;
        this.entityResolver = entityResolver;
        this.transport = transport;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(RemoteTestProtocolRoutes.BROWSE_PROTOCOLS, (partner, params, body) -> serveProtocols(partner));
        endpoints.serve(
                RemoteTestProtocolRoutes.GET_PROTOCOL,
                (partner, params, body) -> serveProtocol(partner, params.integer("id")));
    }

    /**
     * The protocols this station shares with a partner.
     *
     * @param partner the partnership the request arrived on
     * @return a summary per shared protocol of this station
     */
    public List<RemoteProtocolSummary> serveProtocols(ServingPartner partner) {
        return federationRepository.findProtocolShares(partner.servingStationId()).stream()
                .flatMap(share -> Stream.ofNullable(share.protocolId()))
                .flatMap(protocolId -> findProtocol(protocolId).stream())
                .filter(protocol -> protocol.stationId() == partner.servingStationId())
                .map(protocol -> new RemoteProtocolSummary(
                        protocol.id(),
                        protocol.name(),
                        protocol.description(),
                        protocol.updatedAt().toString()))
                .toList();
    }

    /**
     * One protocol this station shares with a partner, with its sections and items.
     *
     * <p>A partner is paired with a station, not entitled to everything it holds, and protocol ids
     * are sequential: a protocol that is missing, belongs to another station or is not shared is
     * refused alike.
     *
     * @param partner    the partnership the request arrived on
     * @param protocolId the protocol asked for
     * @return the protocol
     */
    public RemoteProtocolDetail serveProtocol(ServingPartner partner, int protocolId) {
        var protocol = findProtocol(protocolId)
                .filter(found -> found.stationId() == partner.servingStationId())
                .filter(found -> isShared(partner, protocolId))
                .orElseThrow(TestProtocolRefusal.REMOTE_PROTOCOL_NOT_SHARED::raise);
        return new RemoteProtocolDetail(protocol, findSections(protocolId), findAllItemsByProtocol(protocolId));
    }

    private boolean isShared(ServingPartner partner, int protocolId) {
        return federationRepository.findProtocolShares(partner.servingStationId()).stream()
                .anyMatch(share -> Objects.equals(share.protocolId(), protocolId));
    }

    /**
     * The protocols of a station its partners may see.
     *
     * @param stationId the station
     * @return the ids of its shared protocols, each once
     */
    public List<Integer> findSharedProtocolIds(int stationId) {
        return federationRepository.findProtocolShares(stationId).stream()
                .flatMap(share -> Stream.ofNullable(share.protocolId()))
                .distinct()
                .toList();
    }

    /**
     * Shares a protocol with every partner of its station, or stops sharing it.
     *
     * <p>Asking for the state it is already in changes nothing, so a protocol is never shared twice.
     *
     * @param stationId  the station, already checked to own the protocol
     * @param protocolId the protocol
     * @param shared     whether partners may see it
     */
    public void setShared(int stationId, int protocolId, boolean shared) {
        Transactions.run(() -> {
            var existing = federationRepository.findProtocolShares(stationId).stream()
                    .filter(share -> Objects.equals(share.protocolId(), protocolId))
                    .toList();
            if (shared && existing.isEmpty()) {
                federationService.createProtocolShare(stationId, protocolId, ShareScope.ALL_PARTNERS);
            }
            if (!shared) {
                existing.forEach(share -> federationService.deleteProtocolShare(share.id(), stationId));
            }
        });
    }

    public List<TestProtocol> findProtocols(int stationId) {
        return repository.findProtocols(stationId);
    }

    public List<TestProtocol> searchProtocols(int stationId, String query) {
        return repository.searchProtocols(stationId, query);
    }

    public Optional<TestProtocol> findProtocol(int id) {
        return repository.findProtocolById(id);
    }

    public TestProtocol createProtocol(
            int stationId, String name, String description, @Nullable Integer passThreshold) {
        TestProtocol protocol = repository.createProtocol(stationId, name, description, passThreshold);
        log.info("Created test protocol {} (name='{}') in station {}", protocol.id(), name, stationId);
        return protocol;
    }

    public boolean updateProtocol(int id, String name, String description, @Nullable Integer passThreshold) {
        boolean updated = repository.updateProtocol(id, name, description, passThreshold);
        if (updated) log.info("Updated test protocol {} (name='{}')", id, name);
        else log.warn("Update of test protocol {} did not change any row", id);
        return updated;
    }

    public boolean deleteProtocol(int id, int stationId) {
        boolean deleted = repository.deleteProtocol(id, stationId);
        if (deleted) log.info("Deleted test protocol {}", id);
        else log.warn("Delete of test protocol {} did not change any row", id);
        return deleted;
    }

    public List<TestProtocolSection> findSections(int protocolId) {
        return repository.findSections(protocolId);
    }

    /**
     * The station owning the protocol a section belongs to, empty when there is no such section.
     */
    public Optional<Integer> findSectionStation(int sectionId) {
        return repository.findSectionStation(sectionId);
    }

    /**
     * The station owning the protocol an item belongs to, empty when there is no such item.
     */
    public Optional<Integer> findItemStation(int itemId) {
        return repository.findItemStation(itemId);
    }

    public TestProtocolSection createSection(
            int protocolId,
            @Nullable Integer parentId,
            String name,
            String description,
            @Nullable Integer maxPoints,
            @Nullable Integer passThreshold,
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

    /**
     * Changes a section.
     *
     * @param position where it now stands among its siblings, or {@code null} to leave it where it is
     * @return whether there was such a section
     */
    public boolean updateSection(
            int id,
            String name,
            String description,
            @Nullable Integer maxPoints,
            @Nullable Integer passThreshold,
            @Nullable Integer position) {
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

    /**
     * Changes a point.
     *
     * @param position where it now stands in its section, or {@code null} to leave it where it is
     * @return whether there was such a point
     */
    public boolean updateItem(int id, String label, String description, double points, @Nullable Integer position) {
        boolean updated = repository.updateItem(id, label, description, points, position);
        if (updated) log.info("Updated protocol item {} (label='{}', points={})", id, label, points);
        else log.warn("Update of protocol item {} did not change any row", id);
        return updated;
    }

    /**
     * Puts the sections of one level of a protocol into the given order: its top-level sections, or the
     * sections inside one section.
     *
     * <p>The order has to name exactly the sections of that level, each once. One that was added or
     * removed meanwhile, or one of another level, refuses the whole order rather than leaving the level
     * half sorted.
     *
     * @param protocolId the protocol
     * @param orderedIds the sections of one level, in their new order
     */
    public void reorderSections(int protocolId, List<Integer> orderedIds) {
        var sections = repository.findSections(protocolId);
        var named = sections.stream()
                .filter(section -> !orderedIds.isEmpty() && section.id() == orderedIds.getFirst())
                .findFirst()
                .orElseThrow(TestProtocolRefusal.PROTOCOL_ORDER_OUT_OF_DATE::raise);
        var siblings = sections.stream()
                .filter(section -> Objects.equals(section.parentId(), named.parentId()))
                .map(TestProtocolSection::id)
                .toList();
        requireWholeLevel(siblings, orderedIds);
        Transactions.run(() -> repository.reorderSections(protocolId, orderedIds));
        log.info("Reordered {} sections of test protocol {}", orderedIds.size(), protocolId);
    }

    /**
     * Puts the points of a section into the given order. The order has to name exactly the points of
     * the section, each once.
     *
     * @param sectionId  the section
     * @param orderedIds its points, in their new order
     */
    public void reorderItems(int sectionId, List<Integer> orderedIds) {
        var items = repository.findItems(sectionId).stream()
                .map(TestProtocolItem::id)
                .toList();
        requireWholeLevel(items, orderedIds);
        Transactions.run(() -> repository.reorderItems(sectionId, orderedIds));
        log.info("Reordered {} points of protocol section {}", orderedIds.size(), sectionId);
    }

    private static void requireWholeLevel(List<Integer> level, List<Integer> orderedIds) {
        if (orderedIds.size() != level.size() || !Set.copyOf(level).equals(Set.copyOf(orderedIds))) {
            throw TestProtocolRefusal.PROTOCOL_ORDER_OUT_OF_DATE.raise();
        }
    }

    public boolean deleteItem(int id) {
        boolean deleted = repository.deleteItem(id);
        if (deleted) log.info("Deleted protocol item {}", id);
        else log.warn("Delete of protocol item {} did not change any row", id);
        return deleted;
    }

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

    public boolean deleteRun(int id, int stationId) {
        boolean deleted = repository.deleteRun(id, stationId);
        if (deleted) log.info("Deleted protocol run {}", id);
        else log.warn("Delete of protocol run {} did not change any row", id);
        return deleted;
    }

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

    public List<SharedProtocolItem> browseSharedProtocols(int stationId) {
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(p -> federationService.hasCapability(p, CapabilityType.PROTOCOL_SHARE, Direction.IMPORT))
                .toList();
        return fanout.fanOut(partners, this::browsePartner).items();
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

    public RemoteProtocolDetail getFederatedProtocol(int localStationId, UUID partnerStationUid, int protocolId) {
        var partner = entityResolver.requireActivePartner(localStationId, partnerStationUid);
        return transport.get(partner, RemoteTestProtocolRoutes.GET_PROTOCOL.at(protocolId), RemoteProtocolDetail.class);
    }

    /**
     * Copies a protocol a partner shares into this station.
     *
     * <p>The protocol is read from the partner over federation, the same way it is shown, so only what
     * the partner actually shares can be copied, and a partner on another instance works the same as
     * one on this instance.
     *
     * @param localStationId    the station copying
     * @param partnerStationUid the partner station that shares the protocol
     * @param protocolId        the protocol's number at the partner
     * @return the new protocol of this station
     */
    public TestProtocol copyFederatedProtocol(int localStationId, UUID partnerStationUid, int protocolId) {
        var source = getFederatedProtocol(localStationId, partnerStationUid, protocolId);
        return Transactions.call(() -> copyInto(source, localStationId));
    }

    private TestProtocol copyInto(RemoteProtocolDetail source, int targetStationId) {
        var protocol = source.protocol();
        var copy = createProtocol(targetStationId, protocol.name(), protocol.description(), protocol.passThreshold());
        log.info(
                "Copying partner protocol {} into new protocol {} at station {}",
                protocol.id(),
                copy.id(),
                targetStationId);

        var sectionMap = new HashMap<Integer, Integer>();
        for (var section : parentsFirst(source.sections())) {
            var created = createSection(
                    copy.id(),
                    section.parentId() == null ? null : sectionMap.get(section.parentId()),
                    section.name(),
                    section.description(),
                    section.maxPoints(),
                    section.passThreshold(),
                    section.position());
            sectionMap.put(section.id(), created.id());
        }

        for (var item : source.items()) {
            Integer sectionId = sectionMap.get(item.sectionId());
            if (sectionId != null) {
                createItem(sectionId, item.label(), item.description(), item.points(), item.position());
            }
        }
        return copy;
    }

    /**
     * The sections of a protocol in an order where every parent comes before its children, at any
     * depth. A section whose parent is not among them is left out, as it could only be created at the
     * wrong place.
     */
    static List<TestProtocolSection> parentsFirst(List<TestProtocolSection> sections) {
        var byParent = sections.stream()
                .filter(section -> section.parentId() != null)
                .collect(Collectors.groupingBy(TestProtocolSection::parentId));
        var ordered = new ArrayList<TestProtocolSection>();
        var pending = new ArrayDeque<TestProtocolSection>();
        sections.stream().filter(section -> section.parentId() == null).forEach(pending::add);
        while (!pending.isEmpty()) {
            var section = pending.poll();
            ordered.add(section);
            pending.addAll(byParent.getOrDefault(section.id(), List.of()));
        }
        return ordered;
    }

    private List<SharedProtocolItem> browsePartner(FederationPartner partner) {
        int sourceStationId = resolvePartnerStationId(partner);
        return transport
                .getList(partner, RemoteTestProtocolRoutes.BROWSE_PROTOCOLS.at(), RemoteProtocolSummary.class)
                .stream()
                .map(protocol -> {
                    federationRepository.upsertMetadataCache(
                            partner.id(), ContentType.PROTOCOL, protocol.id(), protocol.name(), protocol.description());
                    return new SharedProtocolItem(
                            protocol.id(), protocol.name(), protocol.description(), sourceStationId, partner.id());
                })
                .toList();
    }

    private int resolvePartnerStationId(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
    }

    public record SharedProtocolItem(int id, String name, String description, int sourceStationId, int partnerId) {}

    /**
     * A protocol shared by a partner, carrying the owning partner station's display name. The
     * station UUID addresses the serving station on the federated read routes and is null when the
     * partnership behind it can no longer be resolved.
     */
    public record SharedProtocolView(
            int id,
            String name,
            String description,
            String stationName,
            @Nullable String stationUid) {}
}
