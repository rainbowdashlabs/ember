/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;
import java.util.function.Function;

/**
 * Resolves single entities owned by a federation partner, hiding whether the owning station lives
 * on this instance or on another one. Callers provide the local lookup and either a remote fetcher
 * or the signed remote endpoint to read the entity from.
 */
@Singleton
public class FederationEntityResolver {
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final FederationHttpClient httpClient;

    @Inject
    public FederationEntityResolver(
            FederationRepository federationRepository,
            StationRepository stationRepository,
            FederationHttpClient httpClient) {
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.httpClient = httpClient;
    }

    /**
     * Looks up the partner a station is federated with and requires the partnership to be active.
     *
     * @param localStationId    the requesting station ID
     * @param partnerStationUid the partner station UUID
     * @return the active partner
     */
    public FederationPartner requireActivePartner(int localStationId, UUID partnerStationUid) {
        var partner = federationRepository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner"));
        if (partner.status() != FederationStatus.ACTIVE) {
            throw new BadRequestResponse("Partner is not active");
        }
        return partner;
    }

    /**
     * Resolves an entity from an active partner, dispatching to the local or the remote fetcher
     * depending on where the partner station lives.
     *
     * @param localStationId    the requesting station ID
     * @param partnerStationUid the partner station UUID
     * @param localFetcher      lookup for partners living on this instance
     * @param remoteFetcher     lookup for partners living on another instance
     * @param <T>               the entity type
     * @return the resolved entity
     */
    public <T> T resolve(
            int localStationId,
            UUID partnerStationUid,
            Function<FederationPartner, T> localFetcher,
            Function<FederationPartner, T> remoteFetcher) {
        var partner = requireActivePartner(localStationId, partnerStationUid);
        return partner.isRemote() ? remoteFetcher.apply(partner) : localFetcher.apply(partner);
    }

    /**
     * Resolves an entity from an active partner, reading it from the given signed remote endpoint
     * when the partner lives on another instance.
     *
     * @param localStationId    the requesting station ID
     * @param partnerStationUid the partner station UUID
     * @param remoteRequest     the declared federation endpoint call to read from
     * @param responseType      the expected remote response type
     * @param entityName        the entity name used in the failure message, for example {@code news}
     * @param localFetcher      lookup for partners living on this instance
     * @param <T>               the entity type
     * @return the resolved entity
     */
    public <T> T resolve(
            int localStationId,
            UUID partnerStationUid,
            FederationRequest remoteRequest,
            Class<T> responseType,
            String entityName,
            Function<FederationPartner, T> localFetcher) {
        return resolve(
                localStationId,
                partnerStationUid,
                localFetcher,
                partner -> fetchRemote(partner, localStationId, remoteRequest, responseType, entityName));
    }

    private <T> T fetchRemote(
            FederationPartner partner,
            int localStationId,
            FederationRequest remoteRequest,
            Class<T> responseType,
            String entityName) {
        var result = httpClient.get(
                partner.remoteHost(),
                remoteRequest,
                partner.partnerStationId(),
                localStationId,
                privateKey(localStationId),
                responseType);
        if (result == null) {
            throw new IllegalStateException("Failed to fetch " + entityName + " from remote partner");
        }
        return result;
    }

    private String privateKey(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(Station::federationPrivateKey)
                .orElse(null);
    }
}
