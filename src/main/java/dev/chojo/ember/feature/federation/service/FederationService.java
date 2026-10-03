/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.FederationRequestAnswered;
import dev.chojo.ember.event.events.FederationRequestReceived;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ChangeType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationCapability;
import dev.chojo.ember.feature.federation.entity.FederationChangeLog;
import dev.chojo.ember.feature.federation.entity.FederationMetadataCache;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationShare;
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.RandomTokens;
import dev.chojo.ember.util.WebOrigins;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class FederationService {
    private static final Logger log = LoggerFactory.getLogger(FederationService.class);
    private static final String PAIRING_TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private final FederationRepository repository;
    private final StationRepository stationRepository;
    private final StationKeyStore stationKeys;
    private final DomainEventBus eventBus;
    private final String instanceHost;

    @Inject
    public FederationService(
            FederationRepository repository,
            StationRepository stationRepository,
            StationKeyStore stationKeys,
            DomainEventBus eventBus,
            Api apiConfig) {
        this.repository = repository;
        this.stationRepository = stationRepository;
        this.stationKeys = stationKeys;
        this.eventBus = eventBus;
        this.instanceHost = WebOrigins.hostAndPort(apiConfig.baseUrl());
    }

    /**
     * The public half of the key the station signs federation traffic with, generating the key pair
     * on first use. A station that already has a key keeps it, so its existing partners go on
     * verifying what it sends.
     *
     * @param stationId the station
     * @return the Base64 public key
     */
    public String ensureStationKey(int stationId) {
        return stationKeys.ensurePublicKey(stationId);
    }

    /**
     * Generates a discovery/pairing code: ember-BASE64(stationUid)-BASE64(host).
     * Stateless - entering this creates a PENDING request that the target station must accept.
     */
    public String generatePairingCode(UUID stationUid) {
        return pairingCode(stationUid, instanceHost);
    }

    /**
     * A pairing code for a station of another instance, which names that instance's address
     * instead of this one's. Entering it at a station sends that instance a request to federate.
     *
     * @param stationUid the station of the other instance
     * @param baseUrl    where the other instance is reached
     * @return the pairing code
     */
    public String generateRemotePairingCode(UUID stationUid, String baseUrl) {
        return pairingCode(stationUid, WebOrigins.hostAndPort(baseUrl));
    }

    private static String pairingCode(UUID stationUid, String host) {
        String encodedUid = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(stationUid.toString().getBytes(StandardCharsets.UTF_8));
        String encodedHost =
                Base64.getUrlEncoder().withoutPadding().encodeToString(host.getBytes(StandardCharsets.UTF_8));
        return "ember-" + encodedUid + "-" + encodedHost;
    }

    /**
     * Generates a station invite code: ember-BASE64(stationUid)-BASE64(host)-TOKEN.
     * The token proves the station consented - entering this auto-activates the federation.
     */
    public String generateStationInvite(int stationId, UUID stationUid) {
        String token = generateRandomToken();
        repository.createInviteToken(stationId, token);
        log.info("Generated federation station invite for station {}", stationId);
        return generatePairingCode(stationUid) + "-" + token;
    }

    /**
     * Parses any pairing/invite code. Returns the parts including an optional token.
     * - 2 segments: discovery code (PENDING request)
     * - 3 segments: station invite (auto-activate if token is valid)
     */
    public Optional<PairingCodeParts> parsePairingCode(String code) {
        if (!code.startsWith("ember-")) return Optional.empty();
        String rest = code.substring("ember-".length());
        String[] segments = rest.split("-", 3);
        if (segments.length < 2) return Optional.empty();
        try {
            String uid = new String(Base64.getUrlDecoder().decode(segments[0]), StandardCharsets.UTF_8);
            String host = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
            String token = segments.length == 3 ? segments[2] : null;
            return Optional.of(new PairingCodeParts(UUID.fromString(uid), host, token));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Validates and consumes a station invite token. Returns true if valid (station consented).
     */
    public boolean consumeInviteToken(int stationId, String token) {
        boolean consumed = repository.deleteInviteToken(stationId, token);
        if (consumed) {
            log.info("Consumed federation invite token for station {}", stationId);
        } else {
            log.warn("Federation invite token for station {} was invalid or already used", stationId);
        }
        return consumed;
    }

    /**
     * Why a pairing code was turned away. Each value names one situation the reader can act on,
     * because a single refusal covering all of them reads as "the code is gone" when the code is
     * fine.
     */
    public enum CodeRefusal {
        /** Not a pairing code at all: mistyped, truncated or from somewhere else entirely. */
        MALFORMED,
        /**
         * Made on a different instance and carrying no consent, so there is nothing to redeem
         * there. Only an invite code, which carries a token, reaches across instances.
         */
        OTHER_INSTANCE,
        /**
         * The address in the code is not one this instance will call: not public, not HTTPS, or
         * pointing into a network nobody outside it should be able to make this server visit.
         */
        HOST_REFUSED,
        /** The other instance did not answer at all. */
        REMOTE_UNREACHABLE,
        /** The other instance took too long to answer. */
        REMOTE_TIMEOUT,
        /** The other instance answered, and would not accept this station. */
        REMOTE_REFUSED,
        /** The other instance no longer has the station the code names. */
        REMOTE_STATION_GONE,
        /** The two instances run federation versions that cannot talk to each other. */
        CONTRACT_MISMATCH,
        /** Well formed, but this instance has no station with that identity. */
        UNKNOWN_STATION,
        /** The station that entered the code is the station the code was made for. */
        OWN_STATION,
        /** The two stations are already connected, or their connection is paused. */
        ALREADY_PARTNERED,
        /** A request to this station is already waiting for its answer. */
        REQUEST_PENDING,
        /** The code carried a token this station has no record of, or that was already used. */
        SPENT_TOKEN
    }

    /**
     * What entering a pairing code produced.
     *
     * <p>Nothing here expires: a token stands until it is redeemed, so a refusal always names a
     * situation and never the passing of time.
     */
    public sealed interface CodeOutcome {
        /** The code carried the other station's consent and the partnership now stands. */
        record Partnered(FederationPartner partner) implements CodeOutcome {}

        /** The code only named a station, so it is now waiting for that station to answer. */
        record Requested(FederationPartner partner) implements CodeOutcome {}

        /**
         * The code only named a station of another instance, so that instance was sent a request and
         * it now waits for its station to answer.
         */
        record RequestedRemotely(PairRequest request) implements CodeOutcome {}

        /** The code was turned away, for the named reason. */
        record Refused(CodeRefusal reason, @Nullable String detail) implements CodeOutcome {}
    }

    /**
     * Enters a pairing code naming a station on this instance, on behalf of the station that typed
     * it. A code made elsewhere is turned away here and belongs to
     * {@link FederationEnrollmentService}, which is the only caller that reaches another instance.
     *
     * <p>A code carrying a token is the issuing station's consent and settles the partnership at
     * once. A request either side had left open is dropped as part of that: asking to connect and
     * then being handed a code is one connection reached twice, and treating the open request as an
     * existing partnership used to make every code between those two stations unusable.
     */
    public CodeOutcome enterPairingCode(int enteringStationId, String code) {
        var parsed = parsePairingCode(code);
        if (parsed.isEmpty()) return new CodeOutcome.Refused(CodeRefusal.MALFORMED, null);
        var parts = parsed.get();
        if (!parts.host().equalsIgnoreCase(instanceHost)) {
            return new CodeOutcome.Refused(CodeRefusal.OTHER_INSTANCE, parts.host());
        }

        var target = stationRepository.findByUid(parts.stationUid());
        if (target.isEmpty()) return new CodeOutcome.Refused(CodeRefusal.UNKNOWN_STATION, null);
        int targetStationId = target.get().id();
        if (targetStationId == enteringStationId) {
            return new CodeOutcome.Refused(CodeRefusal.OWN_STATION, null);
        }

        var towardsTarget = repository.findPartners(enteringStationId).stream()
                .filter(partner ->
                        partner.partnerStationId().equals(target.get().uid()))
                .toList();
        if (towardsTarget.stream()
                .anyMatch(partner -> partner.status() != FederationPartner.FederationStatus.PENDING)) {
            return new CodeOutcome.Refused(CodeRefusal.ALREADY_PARTNERED, null);
        }

        if (!parts.isStationInvite()) {
            if (!towardsTarget.isEmpty()) return new CodeOutcome.Refused(CodeRefusal.REQUEST_PENDING, null);
            return new CodeOutcome.Requested(createPairRequest(enteringStationId, targetStationId));
        }

        if (!consumeInviteToken(targetStationId, parts.requireToken())) {
            return new CodeOutcome.Refused(CodeRefusal.SPENT_TOKEN, null);
        }
        repository.deletePendingRequest(enteringStationId, target.get().uid());
        repository.deletePendingRequest(targetStationId, resolveStationUid(enteringStationId));
        var keyPair = generateKeyPair();
        return new CodeOutcome.Partnered(
                acceptInvite(enteringStationId, targetStationId, encodePublicKey(keyPair), null, null));
    }

    public String getInstanceHost() {
        return instanceHost;
    }

    public KeyPair generateKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate keypair", e);
        }
    }

    public String encodePublicKey(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    public List<FederationPartner> findPartners(int stationId) {
        return repository.findPartners(stationId);
    }

    public Optional<FederationPartner> findPartner(int id) {
        return repository.findPartnerById(id);
    }

    /**
     * The partner row a station holds for another station, whatever state the partnership is in.
     *
     * @param stationId         the station holding the row
     * @param partnerStationUid the station it partners with
     * @return the row, or empty when the two are not partners
     */
    public Optional<FederationPartner> findPartnerByRemoteUid(int stationId, UUID partnerStationUid) {
        return repository.findPartnerByStationAndRemoteUid(stationId, partnerStationUid);
    }

    /**
     * The name a partner station is shown under, the local station's own name first, then the name
     * recorded with the partnership, and "Unknown" when neither is known.
     *
     * @param partner the partnership
     * @return the name to show
     */
    public String partnerName(FederationPartner partner) {
        return FederationDisplayNames.partnerName(stationRepository, partner, "Unknown");
    }

    /**
     * The name of the station that asked for a pairing, or "Unknown" when it is gone.
     *
     * @param request the pending pair request
     * @return the requesting station's name
     */
    public String requesterName(FederationPartner request) {
        return stationRepository
                .findById(request.stationId())
                .map(Station::name)
                .orElse("Unknown");
    }

    /**
     * A pending pair request, as long as it asks the given station.
     *
     * @param requestId the request
     * @param stationId the station that would answer it
     * @return the request, or empty when there is none or it asks another station
     */
    public Optional<FederationPartner> findRequestTo(int requestId, int stationId) {
        UUID stationUid =
                stationRepository.findById(stationId).map(Station::uid).orElse(null);
        return repository.findPartnerById(requestId).filter(request -> request.partnerStationId()
                .equals(stationUid));
    }

    /**
     * A station invite for the given station.
     *
     * @param stationId the inviting station
     * @return the invite code, or empty when the station does not exist
     */
    public Optional<String> generateStationInvite(int stationId) {
        return stationRepository.findById(stationId).map(station -> generateStationInvite(station.id(), station.uid()));
    }

    /**
     * Remembers where a partner wants to be told about changes.
     *
     * @param partnerId  the partnership
     * @param webhookUrl the partner's webhook address, already checked as one this instance calls
     */
    public void registerWebhook(int partnerId, String webhookUrl) {
        repository.setWebhookUrl(partnerId, webhookUrl);
    }

    /**
     * The changes a partner polls for, noting that it has polled.
     *
     * @param partner the polling partnership
     * @param since   the moment the partner last saw
     * @return the station's content changes since then
     */
    public List<FederationChangeLog> syncChanges(FederationPartner partner, Instant since) {
        var changes = repository.findChangesSince(partner.stationId(), since);
        repository.updateLastSyncAt(partner.id());
        return changes;
    }

    /**
     * Creates a pending pair request from the requesting station to the target station.
     * This shows up on the target station's federation page for approval.
     */
    public FederationPartner createPairRequest(int requestingStationId, int targetStationId) {
        UUID targetUid = resolveStationUid(targetStationId);
        var partner = repository.createPartner(requestingStationId, targetUid, null, null, null);
        log.info(
                "Created federation pair request {} from station {} to station {}",
                partner.id(),
                requestingStationId,
                targetStationId);
        eventBus.publish(new FederationRequestReceived(targetStationId, stationName(requestingStationId)));
        return partner;
    }

    /**
     * Accepts a pending pair request, establishing the full bidirectional federation.
     * Generates keypairs for both sides.
     */
    public FederationPartner acceptPairRequest(int partnerId) {
        var partner = repository.findPartnerById(partnerId).orElseThrow();
        if (partner.status() != FederationPartner.FederationStatus.PENDING) {
            throw new IllegalStateException("Partner is not in PENDING status");
        }

        int requestingStationId = partner.stationId();
        var target = stationRepository.findByUid(partner.partnerStationId()).orElseThrow();

        repository.deletePartner(partnerId);

        var keyPair = generateKeyPair();
        var established = acceptInvite(target.id(), requestingStationId, encodePublicKey(keyPair), null, null);
        eventBus.publish(new FederationRequestAnswered(requestingStationId, target.name(), true));
        return established;
    }

    /**
     * Declines a pending pair request and tells the asking station.
     */
    public void declinePairRequest(int partnerId) {
        var request = repository.findPartnerById(partnerId);
        repository.deletePartner(partnerId);
        log.info("Declined federation pair request {}", partnerId);
        request.ifPresent(declined -> eventBus.publish(new FederationRequestAnswered(
                declined.stationId(),
                stationRepository
                        .findByUid(declined.partnerStationId())
                        .map(Station::name)
                        .orElse("Unknown"),
                false)));
    }

    private String stationName(int stationId) {
        return stationRepository.findById(stationId).map(Station::name).orElse("Unknown");
    }

    /**
     * Finds pending pair requests targeting the given station.
     */
    public List<FederationPartner> findPendingRequests(int targetStationId) {
        UUID targetUid = resolveStationUid(targetStationId);
        return repository.findPendingRequestsForStation(targetUid);
    }

    /**
     * Accepts a federation invite on the same instance (or cross-instance).
     * Creates bidirectional partner records with optional remote host URLs, every capability
     * enabled in both directions.
     *
     * @param acceptingStationId   the station accepting the invite
     * @param initiatingStationId  the station that created the invite
     * @param initiatingPublicKey  the initiating station's public key
     * @param initiatingRemoteHost the initiating station's base URL (null if same instance)
     * @param acceptingRemoteHost  the accepting station's base URL (null if same instance)
     */
    public FederationPartner acceptInvite(
            int acceptingStationId,
            int initiatingStationId,
            String initiatingPublicKey,
            @Nullable String initiatingRemoteHost,
            @Nullable String acceptingRemoteHost) {
        String acceptingPublicKey = ensureStationKey(acceptingStationId);

        UUID acceptingUid = resolveStationUid(acceptingStationId);
        UUID initiatingUid = resolveStationUid(initiatingStationId);

        var partner = repository.createPartner(
                initiatingStationId, acceptingUid, null, initiatingPublicKey, acceptingRemoteHost);
        repository.activatePartner(partner.id(), acceptingPublicKey);

        var reverse = repository.createPartner(
                acceptingStationId, initiatingUid, null, acceptingPublicKey, initiatingRemoteHost);
        repository.activatePartner(reverse.id(), initiatingPublicKey);

        for (var cap : CapabilityType.values()) {
            for (var dir : Direction.values()) {
                repository.upsertCapability(partner.id(), cap, dir, true);
                repository.upsertCapability(reverse.id(), cap, dir, true);
            }
        }

        var established = repository.findPartnerById(partner.id()).orElseThrow();
        log.info(
                "Established federation partnership between station {} and station {} (partner records {} and {})",
                initiatingStationId,
                acceptingStationId,
                partner.id(),
                reverse.id());
        return established;
    }

    public boolean suspendPartner(int partnerId) {
        requirePausable(partnerId);
        boolean updated = repository.updatePartnerStatus(partnerId, FederationPartner.FederationStatus.SUSPENDED);
        if (updated) {
            log.info("Suspended federation partner {}", partnerId);
        } else {
            log.warn("Suspend for federation partner {} affected no row", partnerId);
        }
        return updated;
    }

    public boolean resumePartner(int partnerId) {
        boolean updated = repository.updatePartnerStatus(partnerId, FederationPartner.FederationStatus.ACTIVE);
        if (updated) {
            log.info("Resumed federation partner {}", partnerId);
        } else {
            log.warn("Resume for federation partner {} affected no row", partnerId);
        }
        return updated;
    }

    /**
     * Updates the remote host for all partner records pointing to the given station.
     * Called when a station announces it has moved to a new host.
     *
     * @param stationUid the UUID of the station that moved
     * @param newHost    the new base URL (null if the station moved to the same instance)
     */
    public void updateRemoteHost(UUID stationUid, String newHost) {
        repository.updateRemoteHostForPartnerStation(stationUid, newHost);
        log.info("Updated remote host for partners pointing at station {} to {}", stationUid, newHost);
    }

    /**
     * Ends a federation on both sides, deleting the partner station's reverse record as well.
     *
     * @param partnerId the partner record to end
     * @return {@code true} if the record was deleted
     */
    public boolean endFederation(int partnerId) {
        requireDeletable(partnerId);
        var partner = repository.findPartnerById(partnerId);
        if (partner.isPresent()) {
            var p = partner.get();
            var partnerStation = stationRepository.findByUid(p.partnerStationId());
            if (partnerStation.isPresent()) {
                UUID ourUid = resolveStationUid(p.stationId());
                var all = repository.findPartners(partnerStation.get().id());
                for (var rev : all) {
                    if (rev.partnerStationId().equals(ourUid)) {
                        repository.deletePartner(rev.id());
                    }
                }
            }
        }
        boolean deleted = repository.deletePartner(partnerId);
        if (deleted) {
            log.info("Ended federation partner {}", partnerId);
        } else {
            log.warn("End federation for partner {} affected no row", partnerId);
        }
        return deleted;
    }

    /**
     * Wires a station into its cluster's federation.
     *
     * <p>Two things at once, and they are governed differently. The home pair, in both directions between
     * the station and the cluster's own station, is how cluster content arrives, so it is made whatever the
     * cluster's settings say. The mesh pairs, between this station and every other member station, are made
     * only when the cluster asked for them, because whether stations under one roof see each other is a
     * choice the cluster gets to make.
     *
     * @param homeStationId  the cluster's own station
     * @param stationId      the station joining
     * @param siblingIds     the other member stations, for the mesh
     * @param autoFederate   whether the cluster wants its stations connected to each other
     */
    public void createClusterFederation(
            int homeStationId, int stationId, List<Integer> siblingIds, boolean autoFederate) {
        pairUp(homeStationId, stationId, true);
        if (!autoFederate) {
            log.info("Station {} is paired with its cluster home only, the mesh is switched off", stationId);
            return;
        }
        for (int siblingId : siblingIds) {
            if (siblingId == stationId) continue;
            pairUp(siblingId, stationId, false);
        }
    }

    /**
     * Fills in the mesh pairs that were never made while the cluster had them switched off.
     *
     * <p>Switching the setting back on does not reach into the past for pairs somebody paused or that were
     * made by hand: it only adds the ones that are missing.
     *
     * @param stationIds the cluster's member stations
     */
    public void backfillClusterMesh(List<Integer> stationIds) {
        for (int first : stationIds) {
            for (int second : stationIds) {
                if (first >= second) continue;
                pairUp(first, second, false);
            }
        }
    }

    /**
     * Takes a station out of its cluster's federation, in both directions.
     *
     * <p>Everything the cluster made goes, including the mesh pairs to its former siblings. What the station
     * arranged with anybody itself is untouched, inside the cluster or out: a pair two stations made is
     * theirs and survives the cluster that happened to introduce them.
     *
     * @param stationId the station being released
     */
    public void removeClusterFederation(int stationId) {
        for (FederationPartner partner : repository.findClusterManagedFor(stationId)) {
            repository.deletePartner(partner.id());
        }
        log.info("Removed the cluster-managed federation of station {}", stationId);
    }

    /**
     * Makes both directions of one pair and turns every capability on, in case either row is new.
     */
    private void pairUp(int firstStationId, int secondStationId, boolean clusterHome) {
        UUID firstUid = resolveStationUid(firstStationId);
        UUID secondUid = resolveStationUid(secondStationId);
        if (firstUid == null || secondUid == null) {
            log.warn(
                    "Cluster pairing of station {} and station {} skipped: one has no uid",
                    firstStationId,
                    secondStationId);
            return;
        }

        repository.createClusterPartner(firstStationId, secondUid, clusterHome).ifPresent(this::enableEveryCapability);
        repository.createClusterPartner(secondStationId, firstUid, clusterHome).ifPresent(this::enableEveryCapability);
        log.info("Paired station {} with station {} through their cluster", firstStationId, secondStationId);
    }

    /**
     * Every capability in both directions, written against the enum rather than a list.
     *
     * <p>Stations under one cluster have already agreed to share; asking them to tick seven boxes each would
     * be a formality with no decision behind it. A capability added later is enabled here for free.
     */
    public void enableEveryCapability(FederationPartner partner) {
        for (CapabilityType capability : CapabilityType.values()) {
            repository.upsertCapability(partner.id(), capability, Direction.EXPORT, true);
            repository.upsertCapability(partner.id(), capability, Direction.IMPORT, true);
        }
    }

    private void requirePausable(int partnerId) {
        repository.findPartnerById(partnerId).ifPresent(partner -> {
            if (!partner.pausableByStation()) {
                throw FederationRefusal.FEDERATION_CLUSTER_PARTNER_NOT_PAUSABLE.raise();
            }
        });
    }

    private void requireDeletable(int partnerId) {
        repository.findPartnerById(partnerId).ifPresent(partner -> {
            if (!partner.deletableByStation()) {
                throw FederationRefusal.FEDERATION_CLUSTER_PARTNER_NOT_DELETABLE.raise();
            }
        });
    }

    public List<FederationCapability> findCapabilities(int partnerId) {
        return repository.findCapabilities(partnerId);
    }

    public void setCapability(int partnerId, CapabilityType capability, Direction direction, boolean enabled) {
        repository.upsertCapability(partnerId, capability, direction, enabled);
        log.info("Set federation capability {} {} to {} for partner {}", capability, direction, enabled, partnerId);
    }

    /**
     * Whether a capability is effectively usable with a partner: the admin toggle is on and
     * the partner's last presented contract vector matches this build for the core surface
     * and the capability's feature surface. A feature that mismatches is paused without
     * touching the toggles, so it resumes on its own once both sides run the same contract.
     */
    public boolean hasCapability(FederationPartner partner, CapabilityType capability, Direction direction) {
        if (!contractCompatible(partner, capability)) return false;
        return repository.findCapabilities(partner.id()).stream()
                .anyMatch(
                        c -> c.capability().equals(capability) && c.direction().equals(direction) && c.enabled());
    }

    /**
     * Whether the given feature of a partner speaks this build's contract. Partners on the
     * same instance always do; a remote partner with no stored vector is incompatible until
     * the first successful version exchange fills it in.
     */
    public boolean contractCompatible(FederationPartner partner, CapabilityType capability) {
        if (!partner.isRemote()) return true;
        var remote = partner.federationContract();
        if (remote == null) return false;
        var local = FederationContractVersions.current();
        return local.core().equals(remote.core())
                && local.featureHash(capability).equals(remote.featureHash(capability));
    }

    public List<FederationShare> findKbShares(int stationId) {
        return repository.findKbShares(stationId);
    }

    /** The stations one knowledge share is aimed at, empty when it is for everybody. */
    public List<Integer> findKbShareTargets(int shareId) {
        return repository.findKbShareTargets(shareId);
    }

    public FederationShare createKbShare(
            int stationId, @Nullable Integer fileId, @Nullable Integer folderId, ShareScope shareScope) {
        return createKbShare(stationId, fileId, folderId, shareScope, List.of());
    }

    /**
     * Shares a knowledge entry, with everybody or with named stations.
     *
     * @param partnerIds the partnerships it is for, read only when the scope names stations
     */
    public FederationShare createKbShare(
            int stationId,
            @Nullable Integer fileId,
            @Nullable Integer folderId,
            ShareScope shareScope,
            List<Integer> partnerIds) {
        var share = repository.createKbShare(stationId, fileId, folderId, shareScope);
        if (shareScope == ShareScope.SPECIFIC) {
            repository.setKbShareTargets(share.id(), partnerIds);
        }
        log.info(
                "Created knowledge-base federation share {} for station {} (scope {}, {} targets)",
                share.id(),
                stationId,
                shareScope,
                partnerIds.size());
        return share;
    }

    public boolean deleteKbShare(int id, int stationId) {
        boolean deleted = repository.deleteKbShare(id, stationId);
        if (deleted) {
            log.info("Deleted knowledge-base federation share {} for station {}", id, stationId);
        } else {
            log.warn("Delete of knowledge-base federation share {} for station {} affected no row", id, stationId);
        }
        return deleted;
    }

    public List<FederationShare> findQuizShares(int stationId) {
        return repository.findQuizShares(stationId);
    }

    public FederationShare createQuizShare(int stationId, int catalogId, ShareScope shareScope) {
        var share = repository.createQuizShare(stationId, catalogId, shareScope);
        log.info("Created quiz federation share {} for station {} (scope {})", share.id(), stationId, shareScope);
        return share;
    }

    public boolean deleteQuizShare(int id, int stationId) {
        boolean deleted = repository.deleteQuizShare(id, stationId);
        if (deleted) {
            log.info("Deleted quiz federation share {} for station {}", id, stationId);
        } else {
            log.warn("Delete of quiz federation share {} for station {} affected no row", id, stationId);
        }
        return deleted;
    }

    public List<FederationShare> findProtocolShares(int stationId) {
        return repository.findProtocolShares(stationId);
    }

    public FederationShare createProtocolShare(int stationId, int protocolId, ShareScope shareScope) {
        var share = repository.createProtocolShare(stationId, protocolId, shareScope);
        log.info("Created protocol federation share {} for station {} (scope {})", share.id(), stationId, shareScope);
        return share;
    }

    public boolean deleteProtocolShare(int id, int stationId) {
        boolean deleted = repository.deleteProtocolShare(id, stationId);
        if (deleted) {
            log.info("Deleted protocol federation share {} for station {}", id, stationId);
        } else {
            log.warn("Delete of protocol federation share {} for station {} affected no row", id, stationId);
        }
        return deleted;
    }

    // TODO: wire into remote sync; no route calls this yet
    public List<FederationMetadataCache> getCachedMetadata(int partnerId, ContentType contentType) {
        return repository.findCachedMetadata(partnerId, contentType);
    }

    // TODO: wire into remote sync; no route calls this yet
    public void refreshMetadataCache(int partnerId, ContentType contentType, List<FederationMetadataCache> entries) {
        for (var entry : entries) {
            repository.upsertMetadataCache(
                    partnerId, contentType, entry.remoteId(), entry.title(), entry.description());
        }
    }

    /**
     * Logs a content change for federation sync polling.
     */
    public void logChange(int stationId, ContentType contentType, int contentId, ChangeType changeType) {
        repository.logChange(stationId, contentType, contentId, changeType);
    }

    /**
     * Returns content changes since the given timestamp for sync polling.
     */
    public List<FederationChangeLog> getChangesSince(int stationId, Instant since) {
        return repository.findChangesSince(stationId, since);
    }

    private String generateRandomToken() {
        return RandomTokens.code(PAIRING_TOKEN_ALPHABET, 12);
    }

    /**
     * Resolves an internal station ID to its UUID.
     */
    private UUID resolveStationUid(int stationId) {
        return stationRepository.requireUid(stationId);
    }

    public record PairingCodeParts(
            UUID stationUid, String host, @Nullable String token) {
        public boolean isStationInvite() {
            return token != null && !token.isBlank();
        }

        /**
         * The token of a station invite, which a plain pairing code does not carry.
         *
         * @return the token
         * @throws IllegalStateException for a plain pairing code
         */
        public String requireToken() {
            if (token == null || token.isBlank()) throw new IllegalStateException("A pairing code carries no token");
            return token;
        }
    }
}
