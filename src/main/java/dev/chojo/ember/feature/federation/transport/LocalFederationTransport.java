/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.api.PublicIdModule;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * The transport to a partner on this instance: the serving function called directly.
 *
 * <p>The partner's own row for the asking station is looked up first and has to be active, which
 * is what the signature check establishes for a partner on another instance. Refusals the serving
 * function raises pass through unchanged. There is no contract check, both sides being the same
 * build.
 *
 * <p>An answer whose type is the one the caller reads is handed over as it is. Where the caller
 * reads the answer into a type of its own, it is converted with a mapper that reads the way the
 * HTTP client does, so the caller sees what a partner on another instance would have sent,
 * station ids included.
 *
 * <p>The call runs on the caller's thread and inside its transaction scope; serving functions open
 * none of their own.
 */
@Singleton
public class LocalFederationTransport implements FederationTransport {
    private static final Logger log = LoggerFactory.getLogger(LocalFederationTransport.class);

    private final Provider<FederationEndpoints> endpoints;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final JsonMapper mapper = OutboundHttp.lenientMapper(PublicIdModule.forPartnerResponses());

    @Inject
    public LocalFederationTransport(
            Provider<FederationEndpoints> endpoints,
            FederationRepository federationRepository,
            StationRepository stationRepository) {
        this.endpoints = endpoints;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
    }

    @Override
    public <T> T get(FederationPartner partner, FederationRequest request, Class<T> type) {
        return answered(convert(call(partner, request, null), type));
    }

    @Override
    public <T> List<T> getList(FederationPartner partner, FederationRequest request, Class<T> elementType) {
        return convertList(call(partner, request, null), elementType);
    }

    @Override
    public <T> T send(FederationPartner partner, FederationRequest request, @Nullable Object body, Class<T> type) {
        return answered(convert(call(partner, request, body), type));
    }

    @Override
    public void deliver(FederationPartner partner, FederationRequest request, @Nullable Object body) {
        call(partner, request, body);
    }

    @Override
    public <T> List<T> sendList(
            FederationPartner partner, FederationRequest request, Object body, Class<T> elementType) {
        return convertList(call(partner, request, body), elementType);
    }

    @Override
    public void notify(FederationPartner partner, FederationRequest webhook, Object body) {
        if (partner.status() != FederationStatus.ACTIVE) return;
        try {
            call(partner, webhook, body);
        } catch (RuntimeException e) {
            log.warn("Webhook {} for partner {} was not taken in", webhook.path(), partner.id(), e);
        }
    }

    /**
     * The partnership as the partner station on this instance sees it.
     *
     * @param partner the asking station's partner row
     * @return the partner station's row for the asking station, required to be active
     */
    public ServingPartner servingSide(FederationPartner partner) {
        var askingUid = stationRepository.resolveUid(partner.stationId());
        return stationRepository
                .findHereByUid(partner.partnerStationId())
                .map(Station::id)
                .flatMap(serving -> federationRepository.findPartnerByStationAndRemoteUid(serving, askingUid))
                .filter(row -> row.status() == FederationStatus.ACTIVE)
                .map(row -> new ServingPartner(row, askingUid))
                .orElseThrow(FederationRefusal.FEDERATION_PARTNERSHIP_NOT_ACTIVE_THERE::raise);
    }

    private @Nullable Object call(FederationPartner partner, FederationRequest request, @Nullable Object body) {
        var endpoint = request.endpoint();
        FederationHandler<Object, Object> handler = endpoints.get().handlerFor(endpoint);
        Object payload = endpoint.requestType() == Void.class ? null : convert(body, endpoint.requestType());
        return handler.serve(servingSide(partner), PathParams.of(request), payload);
    }

    private <T> @Nullable T convert(@Nullable Object value, Class<T> type) {
        if (value == null || type == Void.class) return null;
        if (type.isInstance(value)) return type.cast(value);
        return mapper.convertValue(value, type);
    }

    private <T> List<T> convertList(@Nullable Object value, Class<T> elementType) {
        if (value == null) return List.of();
        return ((List<?>) value)
                .stream().map(element -> convert(element, elementType)).toList();
    }

    private static <T> T answered(@Nullable T answer) {
        if (answer == null) throw FederationRefusal.FEDERATION_PARTNER_DID_NOT_ANSWER.raise();
        return answer;
    }
}
