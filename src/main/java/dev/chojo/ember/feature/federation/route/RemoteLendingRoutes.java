/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-server lending endpoints served to federation partners, through the serving functions
 * of {@code LendingService}. Requests carry an RSA-signed envelope instead of a user session; the
 * consumer side lives in {@link FederatedLendingRoutes} and {@link LendingRoutes}.
 *
 * <p>A request between two instances is written down on both of them and addressed by the identity
 * both copies carry, never by either instance's own row number.
 */
@Singleton
public class RemoteLendingRoutes implements Routes {

    public static final FederationEndpoint GET_AVAILABLE = FederationEndpoint.get(
            FederationSurface.INVENTORY_LEND, "/remote/lending/available", RemoteAvailability.class);
    public static final FederationEndpoint CREATE_REQUEST = FederationEndpoint.post(
            FederationSurface.INVENTORY_LEND,
            "/remote/lending/requests",
            RemoteLendingRequest.class,
            RemoteLendingAccepted.class);
    public static final FederationEndpoint CHANGE_STATUS = FederationEndpoint.post(
            FederationSurface.INVENTORY_LEND,
            "/remote/lending/requests/{requestUid}/status",
            RemoteLendingStatus.class,
            Void.class);
    public static final FederationEndpoint GET_MESSAGES = FederationEndpoint.getList(
            FederationSurface.INVENTORY_LEND, "/remote/lending/requests/{requestUid}/messages", LendingMessage.class);
    public static final FederationEndpoint MESSAGE_NOTICE = FederationEndpoint.post(
            FederationSurface.INVENTORY_LEND,
            "/remote/lending/requests/{requestUid}/notices",
            RemoteLendingNotice.class,
            Void.class);

    public static final List<FederationEndpoint> CONTRACT =
            List.of(GET_AVAILABLE, CREATE_REQUEST, CHANGE_STATUS, GET_MESSAGES, MESSAGE_NOTICE);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteLendingRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(GET_AVAILABLE)
                .serveCreated(CREATE_REQUEST)
                .serve(CHANGE_STATUS)
                .serve(GET_MESSAGES)
                .serve(MESSAGE_NOTICE));
    }

    /**
     * What a station offers the asking partner.
     *
     * @param entries        what is free, counted per inventory and kind of thing
     * @param offersAnything whether the station offers the partner anything at all, which tells
     *                       "nothing is shared with you" from "nothing is free just now"
     */
    public record RemoteAvailability(List<RemoteAvailableEntry> entries, boolean offersAnything) {}

    /**
     * One thing a station offers, counted.
     *
     * @param artId   the kind counted, or {@code null} where the row counts a whole inventory
     * @param artName what that kind is called, or {@code null}
     */
    public record RemoteAvailableEntry(
            int inventoryId, String inventoryName, Integer artId, String artName, int availableCount) {}

    /**
     * A request for gear, as the borrowing station sends it to the lending one.
     *
     * @param uid      the identity both copies of the request carry
     * @param occasion what the request is for, a copy of the appointment's name
     * @param lines    what is asked for, each line naming gear of the lending station
     */
    public record RemoteLendingRequest(
            UUID uid, LocalDate dateFrom, LocalDate dateTo, String occasion, List<RemoteLendingLine> lines) {}

    /**
     * One line of a request.
     *
     * @param inventoryId the inventory the line draws from, or {@code null}
     * @param itemId      the piece the line names, or {@code null}
     * @param artId       the kind of thing the line asks for, or {@code null}
     */
    public record RemoteLendingLine(Integer inventoryId, Integer itemId, Integer artId, int quantity) {}

    /**
     * The lending station's answer to a request it wrote down.
     *
     * @param labels what it calls each line, in line order, for the borrowing station to show
     */
    public record RemoteLendingAccepted(List<String> labels) {}

    /**
     * A request moved on by one of the two stations.
     *
     * @param status the state it moved to
     * @param reason why, where it was declined, or {@code null}
     */
    public record RemoteLendingStatus(LendingStatus status, String reason) {}

    /**
     * Word that a message was written on a request, so the other station's managers hear of it.
     *
     * @param senderName who wrote it
     */
    public record RemoteLendingNotice(String senderName) {}
}
