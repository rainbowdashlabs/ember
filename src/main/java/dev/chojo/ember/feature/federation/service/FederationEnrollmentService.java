/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes.HandshakeRequest;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes.HandshakeResponse;
import dev.chojo.ember.feature.federation.service.FederationService.CodeOutcome;
import dev.chojo.ember.feature.federation.service.FederationService.CodeRefusal;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.UUID;

/**
 * Connecting two stations by invite code, whichever instances they live on.
 *
 * <p>Every code arrives here first. One naming a station on this instance is handed straight to
 * {@link FederationService}, which settles it in the database and is the model this class follows
 * over the wire: the same order of steps, the same end state, only with an HTTP call where the
 * local path has a second row.
 *
 * <p>Three things make the remote path different from copying the local one across the network.
 * The token lives only on the instance that issued it, so it is redeemed there and nowhere else.
 * The address in a code was typed by somebody this instance has never met, so it is checked against
 * {@link RemoteUrlValidator} before anything is fetched from it. And the far side is a stranger
 * until it has signed something, so its half of the exchange is verified before a row is written.
 *
 * <p>A code names an address and never a scheme, so the instance entering it decides. HTTPS, except
 * where {@code federation.allowPrivateHosts} is set: a deployment that waives the public-HTTPS rule
 * for the addresses it calls is one federating inside a private network, where nothing terminates
 * TLS and insisting on it would only fail the connection the flag was set to allow.
 */
@Singleton
public class FederationEnrollmentService {
    private static final Logger log = LoggerFactory.getLogger(FederationEnrollmentService.class);

    private final FederationService federationService;
    private final FederationRepository repository;
    private final StationRepository stationRepository;
    private final FederationHttpClient httpClient;
    private final FederationSigningService signingService;
    private final StationSigner signer;
    private final RemoteUrlValidator urlValidator;
    private final OutgoingPairRequestService outgoingRequests;
    private final String localBaseUrl;
    private final String remoteScheme;

    @Inject
    public FederationEnrollmentService(
            FederationService federationService,
            FederationRepository repository,
            StationRepository stationRepository,
            FederationHttpClient httpClient,
            FederationSigningService signingService,
            StationSigner signer,
            RemoteUrlValidator urlValidator,
            OutgoingPairRequestService outgoingRequests,
            Api apiConfig,
            Federation federationConfig) {
        this.federationService = federationService;
        this.repository = repository;
        this.stationRepository = stationRepository;
        this.httpClient = httpClient;
        this.signingService = signingService;
        this.signer = signer;
        this.urlValidator = urlValidator;
        this.outgoingRequests = outgoingRequests;
        this.localBaseUrl = apiConfig.baseUrl();
        this.remoteScheme = federationConfig.allowPrivateHosts() ? "http://" : "https://";
    }

    /**
     * The payload both sides sign the handshake over. It binds the two stations, the contract, the
     * public key, the token and the address the caller wants to be reached at, so none of them can
     * be swapped for another while the signature still fits.
     */
    static String enrollmentPayload(HandshakeRequest request) {
        return String.join(
                ":",
                String.valueOf(request.stationUid()),
                String.valueOf(request.targetStationUid()),
                request.contract() == null ? "" : request.contract().core(),
                request.publicKey(),
                request.token(),
                request.baseUrl());
    }

    /**
     * Enters a pairing or invite code on behalf of the station that typed it.
     *
     * @param enteringStationId the station entering the code
     * @param code              the code as typed
     */
    public CodeOutcome enterCode(int enteringStationId, String code) {
        var parsed = federationService.parsePairingCode(code);
        if (parsed.isEmpty()) return new CodeOutcome.Refused(CodeRefusal.MALFORMED, null);
        var parts = parsed.get();
        if (parts.host().equalsIgnoreCase(federationService.getInstanceHost())) {
            return federationService.enterPairingCode(enteringStationId, code);
        }
        return joinRemoteInstance(enteringStationId, parts);
    }

    /**
     * Answers a handshake from a station on another instance.
     *
     * <p>Nothing is trusted until the signature holds, and the partnership is written only after the
     * token has been redeemed. Redeeming is a conditional delete and its row count is the decision:
     * two people entering the same code at the same moment both reach the delete, exactly one of
     * them removes a row, and only that one gets a partnership.
     */
    public Handshake acceptHandshake(HandshakeRequest request) {
        if (!isComplete(request)) {
            return new Handshake.Rejected(HandshakeRejection.INVALID_REQUEST);
        }
        if (!signatureHolds(request)) {
            log.warn(
                    "Federation handshake from station {} carried a signature that does not fit", request.stationUid());
            return new Handshake.Rejected(HandshakeRejection.BAD_SIGNATURE);
        }
        if (!FederationContractVersions.current()
                .core()
                .equals(request.contract().core())) {
            log.warn(
                    "Federation handshake from station {} speaks core contract {}, this instance speaks {}",
                    request.stationUid(),
                    request.contract().core(),
                    FederationContractVersions.current().core());
            return new Handshake.Rejected(HandshakeRejection.CONTRACT_MISMATCH);
        }
        if (!urlValidator.isAllowed(request.baseUrl())) {
            log.warn(
                    "Federation handshake from station {} named an address this instance will not call",
                    request.stationUid());
            return new Handshake.Rejected(HandshakeRejection.HOST_REFUSED);
        }

        if (request.stationUid().equals(request.targetStationUid())) {
            log.warn("A handshake asks station {} to federate with itself", request.stationUid());
            return new Handshake.Rejected(HandshakeRejection.INVALID_REQUEST);
        }

        var target = stationRepository.findHereByUid(request.targetStationUid());
        if (target.isEmpty()) {
            return new Handshake.Rejected(HandshakeRejection.UNKNOWN_STATION);
        }
        var station = target.get();
        if (!federationService.consumeInviteToken(station.id(), request.token())) {
            return new Handshake.Rejected(HandshakeRejection.SPENT_TOKEN);
        }

        String publicKey = signer.ensurePublicKey(station.id());
        establish(
                station.id(),
                request.stationUid(),
                publicKey,
                request.publicKey(),
                request.baseUrl(),
                request.stationName(),
                request.contract());
        log.info(
                "Station {} is now federated with station {} on {}",
                station.id(),
                request.stationUid(),
                request.baseUrl());
        return new Handshake.Accepted(new HandshakeResponse(
                station.uid(), station.name(), localBaseUrl, FederationContractVersions.current(), publicKey));
    }

    /**
     * Calls the instance that issued the code, lets it redeem the token, and writes this side of the
     * partnership from what it answers. A plain pairing code carries no token to redeem, so it sends
     * that instance a request to federate instead, which its station answers when it chooses.
     */
    private CodeOutcome joinRemoteInstance(int enteringStationId, FederationService.PairingCodeParts parts) {
        if (!parts.isStationInvite()) {
            return new CodeOutcome.RequestedRemotely(
                    outgoingRequests.sendToCode(enteringStationId, parts.stationUid(), parts.host()));
        }
        String remoteBaseUrl = remoteScheme + parts.host();
        if (!urlValidator.isAllowed(remoteBaseUrl)) {
            return new CodeOutcome.Refused(CodeRefusal.HOST_REFUSED, parts.host());
        }

        var station = stationRepository.findById(enteringStationId).orElseThrow();
        if (station.uid().equals(parts.stationUid())) {
            return new CodeOutcome.Refused(CodeRefusal.OWN_STATION, null);
        }
        if (repository.findPartners(enteringStationId).stream()
                .anyMatch(partner -> partner.partnerStationId().equals(parts.stationUid())
                        && partner.status() != FederationPartner.FederationStatus.PENDING)) {
            return new CodeOutcome.Refused(CodeRefusal.ALREADY_PARTNERED, null);
        }

        String publicKey = signer.ensurePublicKey(enteringStationId);
        var attempt = httpClient.handshake(remoteBaseUrl, signedRequest(station, parts, publicKey));
        if (attempt.status() != FederationHttpClient.HandshakeStatus.ESTABLISHED) {
            return new CodeOutcome.Refused(refusalFor(attempt.status()), parts.host());
        }
        var answer = attempt.response();
        if (answer == null || answer.publicKey() == null || !parts.stationUid().equals(answer.stationUid())) {
            return new CodeOutcome.Refused(CodeRefusal.REMOTE_REFUSED, parts.host());
        }

        repository.deletePendingRequest(enteringStationId, parts.stationUid());
        var partner = establish(
                enteringStationId,
                parts.stationUid(),
                publicKey,
                answer.publicKey(),
                agreedRemoteHost(remoteBaseUrl, answer.baseUrl()),
                answer.stationName(),
                answer.contract());
        log.info("Station {} joined station {} on {}", enteringStationId, parts.stationUid(), remoteBaseUrl);
        return new CodeOutcome.Partnered(partner);
    }

    private HandshakeRequest signedRequest(
            Station station, FederationService.PairingCodeParts parts, String publicKey) {
        var unsigned = new HandshakeRequest(
                station.uid(),
                parts.stationUid(),
                station.name(),
                localBaseUrl,
                FederationContractVersions.current(),
                publicKey,
                parts.requireToken(),
                "");
        String signature = signer.signEnrollment(station.id(), enrollmentPayload(unsigned));
        return new HandshakeRequest(
                unsigned.stationUid(),
                unsigned.targetStationUid(),
                unsigned.stationName(),
                unsigned.baseUrl(),
                unsigned.contract(),
                unsigned.publicKey(),
                unsigned.token(),
                signature);
    }

    /**
     * Where the far side will be called from now on.
     *
     * <p>It may name a fuller address than the code carried, a port for instance, and that is worth
     * taking. It may not name a different host: an answer that redirects the connection somewhere
     * else is an answer to a code somebody else wrote, so the address from the code stands.
     */
    private String agreedRemoteHost(String fromCode, String announced) {
        if (announced == null || announced.isBlank() || !urlValidator.isAllowed(announced)) return fromCode;
        String announcedHost;
        String codeHost;
        try {
            announcedHost = URI.create(announced).getHost();
            codeHost = URI.create(fromCode).getHost();
        } catch (IllegalArgumentException e) {
            return fromCode;
        }
        if (announcedHost == null || codeHost == null) return fromCode;
        return codeHost.equalsIgnoreCase(announcedHost) ? announced : fromCode;
    }

    private FederationPartner establish(
            int stationId,
            UUID partnerStationUid,
            String publicKey,
            String partnerPublicKey,
            String remoteHost,
            String partnerStationName,
            FederationContract contract) {
        var partner = repository.createRemotePartner(
                stationId, partnerStationUid, publicKey, partnerPublicKey, remoteHost, partnerStationName, contract);
        federationService.enableEveryCapability(partner);
        return partner;
    }

    private boolean isComplete(HandshakeRequest request) {
        return request.stationUid() != null
                && request.targetStationUid() != null
                && request.contract() != null
                && request.contract().core() != null
                && isFilled(request.publicKey())
                && isFilled(request.token())
                && isFilled(request.signature())
                && isFilled(request.baseUrl());
    }

    private boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }

    private boolean signatureHolds(HandshakeRequest request) {
        return signingService.enrollmentSignatureHolds(
                enrollmentPayload(request), request.signature(), request.publicKey());
    }

    /** The refusal a reader is shown for each way a handshake can fail. */
    static CodeRefusal refusalFor(FederationHttpClient.HandshakeStatus status) {
        return switch (status) {
            case ESTABLISHED -> throw new IllegalStateException("A handshake that worked is not a refusal");
            case HOST_REFUSED -> CodeRefusal.HOST_REFUSED;
            case UNREACHABLE -> CodeRefusal.REMOTE_UNREACHABLE;
            case TIMEOUT -> CodeRefusal.REMOTE_TIMEOUT;
            case REFUSED -> CodeRefusal.REMOTE_REFUSED;
            case STATION_GONE -> CodeRefusal.REMOTE_STATION_GONE;
            case TOKEN_SPENT -> CodeRefusal.SPENT_TOKEN;
            case CONTRACT_MISMATCH -> CodeRefusal.CONTRACT_MISMATCH;
        };
    }

    /** What answering a handshake produced. */
    public sealed interface Handshake {
        /** The token was redeemed and this instance's half of the partnership stands. */
        record Accepted(HandshakeResponse response) implements Handshake {}

        /** Nothing was written, for the named reason. */
        record Rejected(HandshakeRejection reason) implements Handshake {}
    }

    /** Why a handshake was turned away. */
    public enum HandshakeRejection {
        /** Fields the exchange cannot do without were missing. */
        INVALID_REQUEST,
        /** The signature does not fit the payload and the key that came with it. */
        BAD_SIGNATURE,
        /** The two instances run federation versions that cannot talk to each other. */
        CONTRACT_MISMATCH,
        /** The caller named an address this instance is not willing to call back. */
        HOST_REFUSED,
        /** No station here answers to the identity the code named. */
        UNKNOWN_STATION,
        /** The token is not on record here, or somebody has already redeemed it. */
        SPENT_TOKEN
    }
}
