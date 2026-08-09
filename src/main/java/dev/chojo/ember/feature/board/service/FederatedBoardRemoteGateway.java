/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Signed HTTP transport for board requests against a federation partner living on another instance.
 * Resolves the calling station's federation key for every call and turns transport failures and
 * empty bodies into a {@link NotFoundResponse}.
 */
@Singleton
public class FederatedBoardRemoteGateway {
    private final FederationHttpClient httpClient;
    private final StationRepository stationRepository;

    @Inject
    public FederatedBoardRemoteGateway(FederationHttpClient httpClient, StationRepository stationRepository) {
        this.httpClient = httpClient;
        this.stationRepository = stationRepository;
    }

    /**
     * Fetches a single object from the partner.
     *
     * @param partner the partner to call
     * @param path    the remote path
     * @param type    the expected response type
     * @param <T>     the response type
     * @return the parsed response
     */
    public <T> T get(FederationPartner partner, String path, Class<T> type) {
        try {
            var result = httpClient.get(
                    partner.remoteHost(),
                    path,
                    partner.partnerStationId(),
                    partner.stationId(),
                    privateKey(partner),
                    type);
            if (result == null) throw new NotFoundResponse("Empty response from remote partner");
            return result;
        } catch (NotFoundResponse e) {
            throw e;
        } catch (Exception e) {
            throw new NotFoundResponse("Failed to fetch from remote partner: " + e.getMessage());
        }
    }

    /**
     * Fetches a list of objects from the partner.
     *
     * @param partner     the partner to call
     * @param path        the remote path
     * @param elementType the expected element type
     * @param <T>         the element type
     * @return the parsed elements
     */
    public <T> List<T> getList(FederationPartner partner, String path, Class<T> elementType) {
        return httpClient.getList(
                partner.remoteHost(),
                path,
                partner.partnerStationId(),
                partner.stationId(),
                privateKey(partner),
                elementType);
    }

    /**
     * Posts a body to the partner and parses the response.
     *
     * @param partner the partner to call
     * @param path    the remote path
     * @param body    the request body
     * @param type    the expected response type
     * @param <T>     the response type
     * @return the parsed response
     */
    public <T> T post(FederationPartner partner, String path, Object body, Class<T> type) {
        try {
            var result = httpClient.post(
                    partner.remoteHost(),
                    path,
                    body,
                    partner.partnerStationId(),
                    partner.stationId(),
                    privateKey(partner),
                    type);
            if (result == null) throw new NotFoundResponse("Empty response from remote partner");
            return result;
        } catch (NotFoundResponse e) {
            throw e;
        } catch (Exception e) {
            throw new NotFoundResponse("Failed to post to remote partner: " + e.getMessage());
        }
    }

    /**
     * Posts a body to the partner and parses a list response.
     *
     * @param partner     the partner to call
     * @param path        the remote path
     * @param body        the request body
     * @param elementType the expected element type
     * @param <T>         the element type
     * @return the parsed elements
     */
    public <T> List<T> postList(FederationPartner partner, String path, Object body, Class<T> elementType) {
        return httpClient.postList(
                partner.remoteHost(),
                path,
                body,
                partner.partnerStationId(),
                partner.stationId(),
                privateKey(partner),
                elementType);
    }

    /**
     * Posts a body to the partner without reading a response.
     *
     * @param partner the partner to call
     * @param path    the remote path
     * @param body    the request body
     */
    public void post(FederationPartner partner, String path, Object body) {
        httpClient.post(
                partner.remoteHost(), path, body, partner.partnerStationId(), partner.stationId(), privateKey(partner));
    }

    /**
     * Sends an update to the partner and parses the response.
     *
     * @param partner the partner to call
     * @param path    the remote path
     * @param body    the request body
     * @param type    the expected response type
     * @param <T>     the response type
     * @return the parsed response
     */
    public <T> T put(FederationPartner partner, String path, Object body, Class<T> type) {
        try {
            var result = httpClient.put(
                    partner.remoteHost(),
                    path,
                    body,
                    partner.partnerStationId(),
                    partner.stationId(),
                    privateKey(partner),
                    type);
            if (result == null) throw new NotFoundResponse("Empty response from remote partner");
            return result;
        } catch (NotFoundResponse e) {
            throw e;
        } catch (Exception e) {
            throw new NotFoundResponse("Failed to update on remote partner: " + e.getMessage());
        }
    }

    /**
     * Sends an update to the partner without reading a response.
     *
     * @param partner the partner to call
     * @param path    the remote path
     * @param body    the request body
     */
    public void put(FederationPartner partner, String path, Object body) {
        httpClient.put(
                partner.remoteHost(), path, body, partner.partnerStationId(), partner.stationId(), privateKey(partner));
    }

    /**
     * Deletes a resource on the partner.
     *
     * @param partner the partner to call
     * @param path    the remote path
     */
    public void delete(FederationPartner partner, String path) {
        httpClient.delete(
                partner.remoteHost(), path, partner.partnerStationId(), partner.stationId(), privateKey(partner));
    }

    /**
     * Deletes a resource on the partner, sending a body along with the request.
     *
     * @param partner the partner to call
     * @param path    the remote path
     * @param body    the request body
     */
    public void delete(FederationPartner partner, String path, Object body) {
        httpClient.delete(
                partner.remoteHost(), path, body, partner.partnerStationId(), partner.stationId(), privateKey(partner));
    }

    private String privateKey(FederationPartner partner) {
        return stationRepository
                .findById(partner.stationId())
                .map(Station::federationPrivateKey)
                .orElse(null);
    }
}
