/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.board.service.FederatedBoardProxyService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Unauthenticated remote endpoints for federated board access.
 * All requests are verified via RSA signature. The owning station enforces
 * the share mode (READ_ONLY / FULL) per partner on every request.
 */
@Singleton
public class FederatedBoardRemoteRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(FederatedBoardRemoteRoutes.class);

    private final FederatedBoardService federatedBoardService;
    private final FederatedBoardProxyService proxyService;
    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final FederationSigningService signingService;
    private final FederationRepository federationRepository;

    @Inject
    public FederatedBoardRemoteRoutes(
            FederatedBoardService federatedBoardService,
            FederatedBoardProxyService proxyService,
            BoardService boardService,
            BoardTicketService ticketService,
            FederationSigningService signingService,
            FederationRepository federationRepository) {
        this.federatedBoardService = federatedBoardService;
        this.proxyService = proxyService;
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.signingService = signingService;
        this.federationRepository = federationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String p = prefix + "/federation/remote/boards";

        // Read endpoints (both modes)
        routes.get(p, this::listSharedBoards);
        routes.get(p + "/{boardId}", this::getBoard);
        routes.get(p + "/{boardId}/lanes", this::getLanes);
        routes.get(p + "/{boardId}/labels", this::getLabels);
        routes.get(p + "/{boardId}/fields", this::getFields);
        routes.get(p + "/{boardId}/tickets", this::listTickets);
        routes.get(p + "/{boardId}/tickets/search", this::searchTickets);
        routes.get(p + "/{boardId}/tickets/{ticketId}", this::getTicket);
        routes.get(p + "/{boardId}/tickets/{ticketId}/comments", this::getComments);
        routes.get(p + "/{boardId}/tickets/{ticketId}/checklist", this::getChecklist);
        routes.get(p + "/{boardId}/tickets/{ticketId}/links", this::getLinks);
        routes.get(p + "/{boardId}/tickets/{ticketId}/labels", this::getTicketLabels);
        routes.get(p + "/{boardId}/tickets/{ticketId}/transitions", this::getTransitions);
        routes.get(p + "/{boardId}/tickets/{ticketId}/history", this::getHistory);
        routes.get(p + "/{boardId}/tickets/{ticketId}/attachments", this::getAttachments);
        routes.get(p + "/{boardId}/tickets/{ticketId}/watchers", this::getWatchers);
        routes.get(p + "/{boardId}/access", this::getAccess);

        // Write endpoints (FULL mode only)
        routes.post(p + "/{boardId}/tickets", this::createTicket);
        routes.put(p + "/{boardId}/tickets/{ticketId}", this::updateTicket);
        routes.delete(p + "/{boardId}/tickets/{ticketId}", this::deleteTicket);
        routes.put(p + "/{boardId}/tickets/{ticketId}/move", this::moveTicket);
        routes.put(p + "/{boardId}/tickets/{ticketId}/reorder", this::reorderTickets);
        routes.post(p + "/{boardId}/tickets/{ticketId}/comments", this::addComment);
        routes.put(p + "/{boardId}/tickets/{ticketId}/comments/{commentId}", this::editComment);
        routes.delete(p + "/{boardId}/tickets/{ticketId}/comments/{commentId}", this::deleteComment);
        routes.post(p + "/{boardId}/tickets/{ticketId}/checklist", this::addChecklistItem);
        routes.put(p + "/{boardId}/tickets/{ticketId}/checklist/{itemId}", this::updateChecklistItem);
        routes.delete(p + "/{boardId}/tickets/{ticketId}/checklist/{itemId}", this::deleteChecklistItem);
        routes.post(p + "/{boardId}/tickets/{ticketId}/labels/{labelId}", this::addTicketLabel);
        routes.delete(p + "/{boardId}/tickets/{ticketId}/labels/{labelId}", this::removeTicketLabel);
        routes.post(p + "/{boardId}/labels", this::createLabel);
        routes.post(p + "/{boardId}/tickets/{ticketId}/watch", this::watchTicket);
        routes.delete(p + "/{boardId}/tickets/{ticketId}/watch", this::unwatchTicket);

        // Webhook receivers (called by owning station to notify this partner)
        routes.post(p + "/webhook/ticket-changed", this::onTicketChanged);
        routes.post(p + "/webhook/mention", this::onMention);
        routes.post(p + "/webhook/assignment", this::onAssignment);
        routes.post(p + "/webhook/unassignment", this::onUnassignment);
        routes.post(p + "/webhook/board-renamed", this::onBoardRenamed);
        routes.post(p + "/webhook/board-unshared", this::onBoardUnshared);
        routes.post(p + "/webhook/share-mode-changed", this::onShareModeChanged);
    }

    // -- Signature verification --

    private FederationPartner verifySignature(Context ctx) {
        String stationIdHeader = ctx.header("X-Federation-Station-Id");
        String signature = ctx.header("X-Federation-Signature");
        String timestampHeader = ctx.header("X-Federation-Timestamp");
        if (stationIdHeader == null || signature == null || timestampHeader == null) {
            throw new ForbiddenResponse("Missing federation signature headers");
        }
        int remoteStationId;
        try {
            remoteStationId = Integer.parseInt(stationIdHeader);
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("Invalid X-Federation-Station-Id");
        }
        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampHeader);
        } catch (Exception e) {
            throw new BadRequestResponse("Invalid X-Federation-Timestamp");
        }
        var partner = federationRepository
                .findPartnerByRemoteStationId(remoteStationId)
                .orElseThrow(() -> new ForbiddenResponse("Unknown federation partner"));
        if (partner.status() != FederationPartner.FederationStatus.ACTIVE) {
            throw new ForbiddenResponse("Federation partnership is not active");
        }
        if (partner.partnerPublicKey() == null || partner.partnerPublicKey().isBlank()) {
            throw new ForbiddenResponse("Partner public key not configured");
        }
        var publicKey = signingService.decodePublicKey(partner.partnerPublicKey());
        String body = ctx.body().isEmpty() ? ctx.queryString() != null ? ctx.queryString() : "" : ctx.body();
        boolean valid = signingService.verify(body, signature, publicKey, timestamp);
        if (!valid) {
            throw new ForbiddenResponse("Invalid federation signature");
        }
        return partner;
    }

    private void requireView(int boardId, FederationPartner partner) {
        if (!federatedBoardService.canFederatedView(boardId, partner.id())) {
            throw new ForbiddenResponse("Board not shared with this partner");
        }
    }

    private void requireWrite(int boardId, FederationPartner partner) {
        if (!federatedBoardService.canFederatedWrite(boardId, partner.id())) {
            throw new ForbiddenResponse("Write access requires FULL share mode");
        }
    }

    // -- Read endpoints --

    private void listSharedBoards(Context ctx) {
        var partner = verifySignature(ctx);
        var boardIds = federatedBoardService.findSharedBoardIds(partner.id());
        var boards = boardIds.stream()
                .map(id -> boardService.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(board -> {
                    var mode = federatedBoardService
                            .getShareMode(board.id(), partner.id())
                            .orElse(BoardShareMode.READ_ONLY);
                    return Map.of(
                            "id", board.id(),
                            "name", board.name(),
                            "description", board.description() != null ? board.description() : "",
                            "shortKey", board.shortKey(),
                            "shareMode", mode.name());
                })
                .toList();
        ctx.json(boards);
    }

    private void getBoard(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        var mode = federatedBoardService.getShareMode(boardId, partner.id()).orElse(BoardShareMode.READ_ONLY);
        ctx.json(Map.of("board", board, "shareMode", mode.name()));
    }

    private void getLanes(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        ctx.json(boardService.findLanes(boardId));
    }

    private void getLabels(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        ctx.json(boardService.findLabels(boardId));
    }

    private void getFields(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        ctx.json(boardService.findFields(boardId));
    }

    private void listTickets(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        ctx.json(ticketService.findByBoard(boardId));
    }

    private void searchTickets(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        String q = ctx.queryParam("q");
        if (q == null || q.isBlank()) {
            ctx.json(ticketService.findByBoard(boardId));
        } else {
            ctx.json(ticketService.search(boardId, q));
        }
    }

    private void getTicket(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    private void getComments(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findComments(ticketId));
    }

    private void getChecklist(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findChecklistItems(ticketId));
    }

    private void getLinks(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findLinks(ticketId));
    }

    private void getTicketLabels(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(boardService.findLabelsForTicket(ticketId));
    }

    private void getTransitions(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findTransitions(ticketId));
    }

    private void getHistory(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findHistory(ticketId));
    }

    private void getAttachments(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findAttachments(ticketId));
    }

    private void getWatchers(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var localWatchers = ticketService.findWatchers(ticketId);
        var federatedWatchers = federatedBoardService.findFederatedWatchers(ticketId);
        ctx.json(Map.of("local", localWatchers, "federated", federatedWatchers));
    }

    private void getAccess(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(boardId, partner);
        var mode = federatedBoardService.getShareMode(boardId, partner.id()).orElse(BoardShareMode.READ_ONLY);
        var editRoles = federatedBoardService.findFederatedEditRoles(boardId);
        ctx.json(Map.of("shareMode", mode.name(), "editRoleIds", editRoles));
    }

    // -- Write endpoints (FULL mode only) --

    private void createTicket(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        var req = ctx.bodyAsClass(CreateTicketRequest.class);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        // Use the first lane if none specified
        int laneId = req.laneId() != null
                ? req.laneId()
                : boardService.findLanes(boardId).getFirst().id();
        var ticket = ticketService.createTicket(
                boardId,
                laneId,
                req.title(),
                req.description(),
                null,
                req.priority() != null ? TicketPriority.valueOf(req.priority()) : TicketPriority.MEDIUM,
                req.dueDate() != null ? LocalDate.parse(req.dueDate()) : null,
                0); // createdBy 0 for federated — tracked via federated_creator table
        federatedBoardService.setFederatedCreator(ticket.id(), partner.id(), req.remoteMemberId());
        ctx.json(ticket);
    }

    private void updateTicket(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(UpdateTicketRequest.class);
        ticketService.updateTicket(
                ticketId,
                req.title(),
                req.description(),
                req.assignedMemberId(),
                req.priority() != null ? TicketPriority.valueOf(req.priority()) : null,
                req.dueDate() != null ? LocalDate.parse(req.dueDate()) : null,
                0);
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    private void deleteTicket(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ticketService.deleteTicket(ticketId);
        ctx.status(204);
    }

    private void moveTicket(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(MoveTicketRequest.class);
        var ticket = ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
        ticketService.moveTicket(ticketId, ticket.laneId(), req.toLaneId(), req.position(), 0);
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    private void reorderTickets(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(ReorderRequest.class);
        ticketService.reorderTickets(req.laneId(), req.orderedIds());
        ctx.status(204);
    }

    private void addComment(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(CommentRequest.class);
        var comment = ticketService.createComment(ticketId, req.parentId(), 0, req.content());
        federatedBoardService.setFederatedCommentAuthor(comment.id(), partner.id(), req.remoteMemberId());
        ctx.json(comment);
    }

    private void editComment(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(EditCommentRequest.class);
        ticketService.updateComment(commentId, req.content());
        ctx.status(204);
    }

    private void deleteComment(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        ticketService.deleteComment(commentId);
        ctx.status(204);
    }

    private void addChecklistItem(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(ChecklistItemRequest.class);
        ctx.json(ticketService.addChecklistItem(ticketId, req.title(), 0));
    }

    private void updateChecklistItem(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        var req = ctx.bodyAsClass(UpdateChecklistItemRequest.class);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ticketService.updateChecklistItem(itemId, ticketId, req.title(), req.checked(), 0);
        ctx.status(204);
    }

    private void deleteChecklistItem(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        ticketService.deleteChecklistItem(itemId, ticketId, 0);
        ctx.status(204);
    }

    private void addTicketLabel(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        boardService.addLabelToTicket(ticketId, labelId);
        ctx.json(boardService.findLabelsForTicket(ticketId));
    }

    private void removeTicketLabel(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        boardService.removeLabelFromTicket(ticketId, labelId);
        ctx.status(204);
    }

    private void createLabel(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        var req = ctx.bodyAsClass(CreateLabelRequest.class);
        ctx.json(boardService.createLabel(boardId, req.name(), req.color() != null ? req.color() : "#6b7280"));
    }

    private void watchTicket(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(WatchRequest.class);
        federatedBoardService.addFederatedWatcher(ticketId, partner.id(), req.remoteMemberId());
        ctx.status(204);
    }

    private void unwatchTicket(Context ctx) {
        var partner = verifySignature(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(boardId, partner);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(WatchRequest.class);
        federatedBoardService.removeFederatedWatcher(ticketId, partner.id(), req.remoteMemberId());
        ctx.status(204);
    }

    // -- Webhook receivers (partner station receives these from owning station) --

    private void onTicketChanged(Context ctx) {
        verifySignature(ctx);
        // Payload contains remoteMemberIds to notify — handled by the partner's notification system
        log.info("Received board ticket-changed webhook");
        ctx.status(204);
    }

    private void onMention(Context ctx) {
        verifySignature(ctx);
        log.info("Received board mention webhook");
        ctx.status(204);
    }

    private void onAssignment(Context ctx) {
        verifySignature(ctx);
        log.info("Received board assignment webhook");
        ctx.status(204);
    }

    private void onUnassignment(Context ctx) {
        verifySignature(ctx);
        log.info("Received board unassignment webhook");
        ctx.status(204);
    }

    private void onBoardRenamed(Context ctx) {
        var partner = verifySignature(ctx);
        var req = ctx.bodyAsClass(BoardRenamedWebhook.class);
        proxyService.onBoardRenamed(partner.id(), req.boardId(), req.newName(), req.newShortKey());
        ctx.status(204);
    }

    private void onBoardUnshared(Context ctx) {
        var partner = verifySignature(ctx);
        var req = ctx.bodyAsClass(BoardUnsharedWebhook.class);
        proxyService.onBoardUnshared(partner.id(), req.boardId());
        ctx.status(204);
    }

    private void onShareModeChanged(Context ctx) {
        var partner = verifySignature(ctx);
        var req = ctx.bodyAsClass(ShareModeChangedWebhook.class);
        proxyService.onShareModeChanged(partner.id(), req.boardId(), BoardShareMode.valueOf(req.shareMode()));
        ctx.status(204);
    }

    // -- Request records --

    record CreateTicketRequest(
            String remoteMemberId, Integer laneId, String title, String description, String priority, String dueDate) {}

    record UpdateTicketRequest(
            String title, String description, Integer assignedMemberId, String priority, String dueDate) {}

    record MoveTicketRequest(int toLaneId, int position) {}

    record ReorderRequest(int laneId, List<Integer> orderedIds) {}

    record CommentRequest(String remoteMemberId, Integer parentId, String content) {}

    record EditCommentRequest(String content) {}

    record ChecklistItemRequest(String title) {}

    record UpdateChecklistItemRequest(String title, boolean checked) {}

    record CreateLabelRequest(String name, String color) {}

    record WatchRequest(String remoteMemberId) {}

    record BoardRenamedWebhook(int boardId, String newName, String newShortKey) {}

    record BoardUnsharedWebhook(int boardId) {}

    record ShareModeChangedWebhook(int boardId, String shareMode) {}
}
