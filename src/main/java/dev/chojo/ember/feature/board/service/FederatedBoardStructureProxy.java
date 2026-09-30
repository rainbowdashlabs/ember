/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.feature.board.entity.BoardField;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardLane;
import dev.chojo.ember.feature.board.entity.TicketLabelMapping;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteCreateLabelRequest;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The board level structure of a federated board - its lanes, labels and fields: asks the owning
 * station for it, and answers partners asking for the structure of this station's shared boards.
 */
@Singleton
public class FederatedBoardStructureProxy implements FederationServer {
    private static final String DEFAULT_LABEL_COLOR = "#6b7280";
    private static final Logger log = LoggerFactory.getLogger(FederatedBoardStructureProxy.class);

    private final BoardService boardService;
    private final FederatedBoardLocator locator;
    private final FederationTransport transport;
    private final FederatedBoardGuards guards;

    @Inject
    public FederatedBoardStructureProxy(
            BoardService boardService,
            FederatedBoardLocator locator,
            FederationTransport transport,
            FederatedBoardGuards guards) {
        this.boardService = boardService;
        this.locator = locator;
        this.transport = transport;
        this.guards = guards;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.<RemoteCreateLabelRequest, BoardLabel>serve(
                RemoteBoardRoutes.CREATE_LABEL,
                (partner, params, body) -> boardService.createLabel(
                        guards.writableBoardId(partner, params),
                        body.name(),
                        body.color() != null ? body.color() : DEFAULT_LABEL_COLOR));
        endpoints.serve(
                RemoteBoardRoutes.GET_LANES,
                (partner, params, body) -> boardService.findLanes(guards.viewableBoardId(partner, params)));
        endpoints.serve(
                RemoteBoardRoutes.GET_LABELS,
                (partner, params, body) -> boardService.findLabels(guards.viewableBoardId(partner, params)));
        endpoints.serve(
                RemoteBoardRoutes.GET_ALL_TICKET_LABELS,
                (partner, params, body) -> boardService.findAllTicketLabels(guards.viewableBoardId(partner, params)));
        endpoints.serve(
                RemoteBoardRoutes.GET_FIELDS,
                (partner, params, body) -> boardService.findFields(guards.viewableBoardId(partner, params)));
    }

    /**
     * Returns the lanes of a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the lanes
     */
    public List<BoardLane> proxyGetLanes(int partnerId, String boardKey) {
        return transport.getList(
                locator.requirePartner(partnerId), RemoteBoardRoutes.GET_LANES.at(boardKey), BoardLane.class);
    }

    /**
     * Returns the labels of a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the labels
     */
    public List<BoardLabel> proxyGetLabels(int partnerId, String boardKey) {
        return transport.getList(
                locator.requirePartner(partnerId), RemoteBoardRoutes.GET_LABELS.at(boardKey), BoardLabel.class);
    }

    /**
     * Returns the label assignments of every ticket on a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the ticket to label mappings
     */
    public List<TicketLabelMapping> proxyGetAllTicketLabels(int partnerId, String boardKey) {
        return transport.getList(
                locator.requirePartner(partnerId),
                RemoteBoardRoutes.GET_ALL_TICKET_LABELS.at(boardKey),
                TicketLabelMapping.class);
    }

    /**
     * Returns the custom fields of a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the fields
     */
    public List<BoardField> proxyGetFields(int partnerId, String boardKey) {
        return transport.getList(
                locator.requirePartner(partnerId), RemoteBoardRoutes.GET_FIELDS.at(boardKey), BoardField.class);
    }

    /**
     * Creates a label on a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @param name      the label name
     * @param color     the label color, falling back to the default color
     * @return the created label
     */
    public BoardLabel proxyCreateLabel(int partnerId, String boardKey, String name, String color) {
        log.info("Federated label creation on partner {} board {}", partnerId, boardKey);
        return transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardRoutes.CREATE_LABEL.at(boardKey),
                new RemoteCreateLabelRequest(name, color != null ? color : DEFAULT_LABEL_COLOR),
                BoardLabel.class);
    }
}
