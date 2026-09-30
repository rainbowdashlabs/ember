/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationWebhookService;
import io.javalin.http.HandlerType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The transport to a partner on another instance: a signed request as the asking station.
 *
 * <p>A station without a key cannot sign and is not sent anywhere. A single answer that does not
 * arrive, or arrives empty, is refused as {@link Refusal#FEDERATION_PARTNER_DID_NOT_ANSWER}; a list
 * that does not arrive is empty, so a fan-out loses only that partner's entries. Pushes go through
 * {@link FederationWebhookService}, which retries them in the background.
 */
@Singleton
public class HttpFederationTransport implements FederationTransport {
    private static final Logger log = LoggerFactory.getLogger(HttpFederationTransport.class);

    private final FederationHttpClient httpClient;
    private final FederationWebhookService webhooks;

    @Inject
    public HttpFederationTransport(FederationHttpClient httpClient, FederationWebhookService webhooks) {
        this.httpClient = httpClient;
        this.webhooks = webhooks;
    }

    @Override
    public <T> T get(FederationPartner partner, FederationRequest request, Class<T> type) {
        requireKey(partner);
        return answered(
                httpClient.get(partner.remoteHost(), request, partner.partnerStationId(), partner.stationId(), type));
    }

    @Override
    public <T> List<T> getList(FederationPartner partner, FederationRequest request, Class<T> elementType) {
        if (!canSign(partner)) return List.of();
        return httpClient.getList(
                partner.remoteHost(), request, partner.partnerStationId(), partner.stationId(), elementType);
    }

    @Override
    public <T> T send(FederationPartner partner, FederationRequest request, Object body, Class<T> type) {
        requireKey(partner);
        if (type == Void.class) {
            if (!delivered(partner, request, body)) throw Refusal.FEDERATION_PARTNER_DID_NOT_ANSWER.raise();
            return null;
        }
        var host = partner.remoteHost();
        var target = partner.partnerStationId();
        int station = partner.stationId();
        var method = request.endpoint().method();
        if (method == HandlerType.POST) return answered(httpClient.post(host, request, body, target, station, type));
        if (method == HandlerType.PUT) return answered(httpClient.put(host, request, body, target, station, type));
        throw new IllegalArgumentException(method + " cannot carry an answer: " + request.path());
    }

    @Override
    public <T> List<T> sendList(
            FederationPartner partner, FederationRequest request, Object body, Class<T> elementType) {
        if (!canSign(partner)) return List.of();
        return httpClient.postList(
                partner.remoteHost(), request, body, partner.partnerStationId(), partner.stationId(), elementType);
    }

    @Override
    public void notify(FederationPartner partner, FederationRequest webhook, Object body) {
        webhooks.notifyPartner(partner.id(), webhook, body);
    }

    private boolean delivered(FederationPartner partner, FederationRequest request, Object body) {
        var host = partner.remoteHost();
        var target = partner.partnerStationId();
        int station = partner.stationId();
        var method = request.endpoint().method();
        if (method == HandlerType.POST) return httpClient.post(host, request, body, target, station);
        if (method == HandlerType.PUT) return httpClient.put(host, request, body, target, station);
        if (method == HandlerType.DELETE) {
            return body == null
                    ? httpClient.delete(host, request, target, station)
                    : httpClient.delete(host, request, body, target, station);
        }
        throw new IllegalArgumentException(method + " sends no body");
    }

    private boolean canSign(FederationPartner partner) {
        if (httpClient.canSign(partner.stationId())) return true;
        log.warn("Station {} has no federation key, so partner {} is not asked", partner.stationId(), partner.id());
        return false;
    }

    private void requireKey(FederationPartner partner) {
        if (!canSign(partner)) throw Refusal.FEDERATION_PARTNER_DID_NOT_ANSWER.raise();
    }

    private static <T> T answered(T answer) {
        if (answer == null) throw Refusal.FEDERATION_PARTNER_DID_NOT_ANSWER.raise();
        return answer;
    }
}
