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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Proxies the board level structure of a federated board - its lanes, labels and fields - to the
 * owning station, either through the local database or over HTTP.
 */
@Singleton
public class FederatedBoardStructureProxy {
    private static final String DEFAULT_LABEL_COLOR = "#6b7280";
    private static final Logger log = LoggerFactory.getLogger(FederatedBoardStructureProxy.class);

    private final BoardService boardService;
    private final FederatedBoardRemoteGateway gateway;
    private final FederatedBoardLocator locator;

    @Inject
    public FederatedBoardStructureProxy(
            BoardService boardService, FederatedBoardRemoteGateway gateway, FederatedBoardLocator locator) {
        this.boardService = boardService;
        this.gateway = gateway;
        this.locator = locator;
    }

    /**
     * Returns the lanes of a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the lanes
     */
    public List<BoardLane> proxyGetLanes(int partnerId, String boardKey) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(partner, RemoteBoardRoutes.GET_LANES.at(boardKey), BoardLane.class);
        }
        return boardService.findLanes(locator.resolveBoardId(boardKey, partner));
    }

    /**
     * Returns the labels of a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the labels
     */
    public List<BoardLabel> proxyGetLabels(int partnerId, String boardKey) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(partner, RemoteBoardRoutes.GET_LABELS.at(boardKey), BoardLabel.class);
        }
        return boardService.findLabels(locator.resolveBoardId(boardKey, partner));
    }

    /**
     * Returns the label assignments of every ticket on a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the ticket to label mappings
     */
    public List<TicketLabelMapping> proxyGetAllTicketLabels(int partnerId, String boardKey) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(
                    partner, RemoteBoardRoutes.GET_ALL_TICKET_LABELS.at(boardKey), TicketLabelMapping.class);
        }
        return boardService.findAllTicketLabels(locator.resolveBoardId(boardKey, partner));
    }

    /**
     * Returns the custom fields of a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the fields
     */
    public List<BoardField> proxyGetFields(int partnerId, String boardKey) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(partner, RemoteBoardRoutes.GET_FIELDS.at(boardKey), BoardField.class);
        }
        return boardService.findFields(locator.resolveBoardId(boardKey, partner));
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
        var partner = locator.requirePartner(partnerId);
        log.info("Federated label creation on partner {} board {}", partnerId, boardKey);
        String effectiveColor = color != null ? color : DEFAULT_LABEL_COLOR;
        if (partner.isRemote()) {
            return gateway.post(
                    partner,
                    RemoteBoardRoutes.GET_LABELS.at(boardKey),
                    new CreateLabelBody(name, effectiveColor),
                    BoardLabel.class);
        }
        return boardService.createLabel(locator.resolveBoardId(boardKey, partner), name, effectiveColor);
    }

    record CreateLabelBody(String name, String color) {}
}
