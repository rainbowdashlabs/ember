/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.discovery.service.DiscoverySigningService;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestAnswer;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestMessage;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestStatusQuery;
import dev.chojo.ember.util.Json;
import dev.chojo.ember.util.RandomTokens;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * Signs and checks the messages two instances exchange about a request to federate.
 *
 * <p>Every message is signed twice over the same payload. The station signature proves the station
 * holds the key it names; the instance signature ties the message to an instance this one knows
 * from discovery, which is the only thing that can vouch for a station before any partnership
 * exists. The payload is the message's fields as a JSON array, so no value can run into the next.
 */
@Singleton
public class PairRequestSignatures {
    private static final Logger log = LoggerFactory.getLogger(PairRequestSignatures.class);

    private final StationSigner signer;
    private final FederationSigningService federationSigning;
    private final DiscoverySigningService discoverySigning;

    @Inject
    public PairRequestSignatures(
            StationSigner signer,
            FederationSigningService federationSigning,
            DiscoverySigningService discoverySigning) {
        this.signer = signer;
        this.federationSigning = federationSigning;
        this.discoverySigning = discoverySigning;
    }

    /**
     * A request from a station here to a station of another instance, signed by both.
     *
     * @param stationId        the asking station
     * @param stationUid       its identity
     * @param stationName      its name
     * @param publicKey        its federation key
     * @param baseUrl          where this instance is reached
     * @param targetStationUid the station asked
     * @param contract         the contract this instance speaks
     * @return the signed request
     */
    public PairRequestMessage request(
            int stationId,
            UUID stationUid,
            String stationName,
            String publicKey,
            String baseUrl,
            UUID targetStationUid,
            FederationContract contract) {
        var unsigned = new PairRequestMessage(
                stationUid,
                stationName,
                publicKey,
                baseUrl,
                discoverySigning.publicKeyBase64(),
                targetStationUid,
                contract,
                Instant.now(),
                RandomTokens.urlSafe(24),
                "",
                "");
        String payload = payloadOf(unsigned);
        return new PairRequestMessage(
                unsigned.requesterStationUid(),
                unsigned.requesterStationName(),
                unsigned.requesterPublicKey(),
                unsigned.requesterBaseUrl(),
                unsigned.requesterInstanceKey(),
                unsigned.targetStationUid(),
                unsigned.contract(),
                unsigned.issuedAt(),
                unsigned.nonce(),
                signer.signEnrollment(stationId, payload),
                discoverySigning.sign(payload));
    }

    /**
     * A question from a station here about the request it sent, signed by both.
     *
     * @param stationId        the asking station
     * @param stationUid       its identity
     * @param targetStationUid the station it asked
     * @return the signed question
     */
    public PairRequestStatusQuery query(int stationId, UUID stationUid, UUID targetStationUid) {
        var unsigned = new PairRequestStatusQuery(
                stationUid, targetStationUid, Instant.now(), RandomTokens.urlSafe(24), "", "");
        String payload = payloadOf(unsigned);
        return new PairRequestStatusQuery(
                unsigned.requesterStationUid(),
                unsigned.targetStationUid(),
                unsigned.issuedAt(),
                unsigned.nonce(),
                signer.signEnrollment(stationId, payload),
                discoverySigning.sign(payload));
    }

    /**
     * The answer of a station here to a request from another instance. Only an acceptance carries
     * the station's key and its signature; a decline or a pending answer is signed by the instance.
     *
     * @param stationId           the asked station
     * @param requesterStationUid the asking station
     * @param stationUid          the asked station's identity
     * @param status              where the request stands
     * @param stationName         the asked station's name
     * @param baseUrl             where this instance is reached
     * @param contract            the contract this instance speaks
     * @return the signed answer
     */
    public PairRequestAnswer answer(
            int stationId,
            UUID requesterStationUid,
            UUID stationUid,
            PairRequestStatus status,
            String stationName,
            String baseUrl,
            FederationContract contract) {
        boolean accepted = status == PairRequestStatus.ACCEPTED;
        var unsigned = new PairRequestAnswer(
                requesterStationUid,
                stationUid,
                status,
                stationName,
                baseUrl,
                accepted ? signer.ensurePublicKey(stationId) : null,
                accepted ? contract : null,
                Instant.now(),
                RandomTokens.urlSafe(24),
                null,
                "");
        String payload = payloadOf(unsigned);
        return new PairRequestAnswer(
                unsigned.requesterStationUid(),
                unsigned.targetStationUid(),
                unsigned.status(),
                unsigned.stationName(),
                unsigned.baseUrl(),
                unsigned.publicKey(),
                unsigned.contract(),
                unsigned.issuedAt(),
                unsigned.nonce(),
                accepted ? signer.signEnrollment(stationId, payload) : null,
                discoverySigning.sign(payload));
    }

    /**
     * Whether the instance signature of a request fits the instance key it names.
     *
     * @param message the request
     * @return true when it does
     */
    public boolean instanceSignatureHolds(PairRequestMessage message) {
        return instanceHolds(payloadOf(message), message.instanceSignature(), message.requesterInstanceKey());
    }

    /**
     * Whether the station signature of a request fits the station key it carries.
     *
     * @param message the request
     * @return true when it does
     */
    public boolean stationSignatureHolds(PairRequestMessage message) {
        return stationHolds(payloadOf(message), message.stationSignature(), message.requesterPublicKey());
    }

    /**
     * Whether both signatures of a question fit the keys its request was received with.
     *
     * @param query       the question
     * @param stationKey  the asking station's federation key, as its request carried it
     * @param instanceKey the asking instance's discovery key, as its request carried it
     * @return true when both do
     */
    public boolean holds(PairRequestStatusQuery query, String stationKey, String instanceKey) {
        String payload = payloadOf(query);
        return instanceHolds(payload, query.instanceSignature(), instanceKey)
                && stationHolds(payload, query.stationSignature(), stationKey);
    }

    /**
     * Whether an answer is signed by the instance it was expected from, and, for an acceptance, by
     * the station key it hands over.
     *
     * @param answer      the answer
     * @param instanceKey the discovery key of the instance the request was sent to
     * @return true when every signature it must carry fits
     */
    public boolean holds(PairRequestAnswer answer, String instanceKey) {
        String payload = payloadOf(answer);
        if (!instanceHolds(payload, answer.instanceSignature(), instanceKey)) return false;
        if (answer.status() != PairRequestStatus.ACCEPTED) return true;
        String publicKey = answer.publicKey();
        String signature = answer.stationSignature();
        return publicKey != null && signature != null && stationHolds(payload, signature, publicKey);
    }

    static String payloadOf(PairRequestMessage message) {
        return payload(
                "pair-request",
                message.requesterStationUid(),
                message.requesterStationName(),
                message.requesterPublicKey(),
                message.requesterBaseUrl(),
                message.requesterInstanceKey(),
                message.targetStationUid(),
                message.contract() == null ? null : message.contract().core(),
                message.issuedAt(),
                message.nonce());
    }

    static String payloadOf(PairRequestStatusQuery query) {
        return payload(
                "pair-request-status",
                query.requesterStationUid(),
                query.targetStationUid(),
                query.issuedAt(),
                query.nonce());
    }

    static String payloadOf(PairRequestAnswer answer) {
        var contract = answer.contract();
        return payload(
                "pair-request-answer",
                answer.requesterStationUid(),
                answer.targetStationUid(),
                answer.status(),
                answer.stationName(),
                answer.baseUrl(),
                answer.publicKey(),
                contract == null ? null : contract.core(),
                answer.issuedAt(),
                answer.nonce());
    }

    private static String payload(@Nullable Object... parts) {
        return Json.MAPPER.writeValueAsString(Arrays.stream(parts)
                .map(part -> part == null ? "" : part.toString())
                .toList());
    }

    private boolean instanceHolds(String payload, @Nullable String signature, @Nullable String instanceKey) {
        if (signature == null || instanceKey == null) return false;
        try {
            return discoverySigning.verify(payload, signature, instanceKey);
        } catch (RuntimeException e) {
            log.warn("A request to federate carried an instance signature that could not be read");
            return false;
        }
    }

    private boolean stationHolds(String payload, @Nullable String signature, @Nullable String publicKey) {
        if (signature == null || publicKey == null) return false;
        try {
            return federationSigning.verifyEnrollmentPayload(
                    payload, signature, federationSigning.decodePublicKey(publicKey));
        } catch (RuntimeException e) {
            log.warn("A request to federate carried a station key that could not be read");
            return false;
        }
    }
}
