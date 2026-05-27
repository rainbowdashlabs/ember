/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;

@Singleton
public class BoardTicketRoutes implements Routes {
    private final BoardTicketService ticketService;
    private final BoardService boardService;

    @Inject
    public BoardTicketRoutes(BoardTicketService ticketService, BoardService boardService) {
        this.ticketService = ticketService;
        this.boardService = boardService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String p = prefix + "/boards/{boardId}/tickets";
        routes.get(p, this::listTickets, Roles.USER);
        routes.get(p + "/search", this::searchTickets, Roles.USER);
        routes.post(p, this::createTicket, Roles.USER);
        routes.get(p + "/{ticketId}", this::getTicket, Roles.USER);
        routes.put(p + "/{ticketId}", this::updateTicket, Roles.USER);
        routes.delete(p + "/{ticketId}", this::deleteTicket, Roles.USER);
        routes.put(p + "/{ticketId}/move", this::moveTicket, Roles.USER);
        routes.put(p + "/{ticketId}/assign", this::assignTicket, Roles.USER);
        routes.put(p + "/{ticketId}/reorder", this::reorderTickets, Roles.USER);

        // Links
        routes.get(p + "/{ticketId}/links", this::getLinks, Roles.USER);
        routes.post(p + "/{ticketId}/links", this::createLink, Roles.USER);
        routes.delete(p + "/{ticketId}/links/{linkedId}", this::deleteLink, Roles.USER);

        // Checklist
        routes.get(p + "/{ticketId}/checklist", this::getChecklist, Roles.USER);
        routes.post(p + "/{ticketId}/checklist", this::addChecklistItem, Roles.USER);
        routes.put(p + "/{ticketId}/checklist/{itemId}", this::updateChecklistItem, Roles.USER);
        routes.delete(p + "/{ticketId}/checklist/{itemId}", this::deleteChecklistItem, Roles.USER);
        routes.put(p + "/{ticketId}/checklist/reorder", this::reorderChecklist, Roles.USER);

        // Activity & Comments
        routes.get(p + "/{ticketId}/transitions", this::getTransitions, Roles.USER);
        routes.get(p + "/{ticketId}/comments", this::getComments, Roles.USER);
        routes.post(p + "/{ticketId}/comments", this::createComment, Roles.USER);
        routes.put(p + "/{ticketId}/comments/{commentId}", this::updateComment, Roles.USER);
        routes.delete(p + "/{ticketId}/comments/{commentId}", this::deleteComment, Roles.USER);

        // Weblinks
        routes.get(p + "/{ticketId}/weblinks", this::getWeblinks, Roles.USER);
        routes.post(p + "/{ticketId}/weblinks", this::addWeblink, Roles.USER);
        routes.delete(p + "/{ticketId}/weblinks/{weblinkId}", this::deleteWeblink, Roles.USER);

        // Watchers
        routes.get(p + "/{ticketId}/watchers", this::getWatchers, Roles.USER);
        routes.post(p + "/{ticketId}/watch", this::watchTicket, Roles.USER);
        routes.delete(p + "/{ticketId}/watch", this::unwatchTicket, Roles.USER);

        // Field values
        routes.get(p + "/{ticketId}/fields", this::getFieldValues, Roles.USER);
        routes.put(p + "/{ticketId}/fields/{fieldId}", this::setFieldValue, Roles.USER);
        routes.delete(p + "/{ticketId}/fields/{fieldId}", this::deleteFieldValue, Roles.USER);

        // Attachments
        routes.get(p + "/{ticketId}/attachments", this::getAttachments, Roles.USER);
        routes.post(p + "/{ticketId}/attachments", this::uploadAttachment, Roles.USER);
        routes.get(p + "/{ticketId}/attachments/{attachmentId}/download", this::downloadAttachment, Roles.USER);
        routes.delete(p + "/{ticketId}/attachments/{attachmentId}", this::deleteAttachment, Roles.USER);

        // Labels on tickets
        routes.get(p + "/{ticketId}/labels", this::getTicketLabels, Roles.USER);
        routes.post(p + "/{ticketId}/labels/{labelId}", this::addTicketLabel, Roles.USER);
        routes.delete(p + "/{ticketId}/labels/{labelId}", this::removeTicketLabel, Roles.USER);

        // KB Links
        routes.get(p + "/{ticketId}/kb-links", this::getKbLinks, Roles.USER);
        routes.post(p + "/{ticketId}/kb-links/{kbFileId}", this::addKbLink, Roles.USER);
        routes.delete(p + "/{ticketId}/kb-links/{linkId}", this::removeKbLink, Roles.USER);

        // History
        routes.get(p + "/{ticketId}/history", this::getHistory, Roles.USER);

        // Activity
        routes.get(p + "/{ticketId}/activity", this::getActivity, Roles.USER);
    }

    private void requireEditAccess(int boardId, UserSession session) {
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        if (!boardService.canEdit(boardId, session.member().id()))
            throw new ForbiddenResponse("No edit access to this board");
    }

    private void requireViewAccess(int boardId, UserSession session) {
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        if (!boardService.canView(boardId, session.member().id()))
            throw new ForbiddenResponse("No access to this board");
    }

    // -- Tickets --

    private void searchTickets(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        String q = ctx.queryParam("q");
        if (q == null || q.isBlank()) {
            ctx.json(ticketService.findByBoard(boardId));
        } else {
            ctx.json(ticketService.search(boardId, q));
        }
    }

    private void listTickets(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        ctx.json(ticketService.findByBoard(boardId));
    }

    private void createTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        var req = ctx.bodyAsClass(CreateTicketRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        var ticket = ticketService.createTicket(
                boardId,
                req.laneId(),
                req.title(),
                req.description(),
                req.assignedMemberId(),
                req.priority() != null ? req.priority() : TicketPriority.MEDIUM,
                req.dueDate(),
                session.member().id());
        ctx.status(HttpStatus.CREATED).json(ticket);
    }

    private void getTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void updateTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(UpdateTicketRequest.class);
        ticketService.updateTicket(
                ticketId,
                req.title(),
                req.description(),
                req.assignedMemberId(),
                req.priority(),
                req.dueDate(),
                session.member().id());
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void deleteTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        if (ticketService.deleteTicket(ticketId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private void moveTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(MoveTicketRequest.class);
        var ticket = ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
        ticketService.moveTicket(
                ticketId,
                ticket.laneId(),
                req.toLaneId(),
                req.position(),
                session.member().id());
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void reorderTickets(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        var req = ctx.bodyAsClass(ReorderRequest.class);
        ticketService.reorderTickets(req.laneId(), req.orderedIds());
        ctx.status(HttpStatus.OK);
    }

    private void assignTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(AssignRequest.class);
        ticketService.assignTicket(
                ticketId, req.assignedMemberId(), session.member().id());
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    // -- Links --

    private void getLinks(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findLinks(ticketId));
    }

    private void createLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(LinkRequest.class);
        ticketService.linkTickets(ticketId, req.linkedTicketId(), req.linkType());
        ctx.status(HttpStatus.CREATED).json(ticketService.findLinks(ticketId));
    }

    private void deleteLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int linkedId = ctx.pathParamAsClass("linkedId", Integer.class).get();
        ticketService.unlinkTickets(ticketId, linkedId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Checklist --

    private void getChecklist(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findChecklistItems(ticketId));
    }

    private void addChecklistItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(ChecklistItemRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        ctx.status(HttpStatus.CREATED)
                .json(ticketService.addChecklistItem(
                        ticketId, req.title(), session.member().id()));
    }

    private void updateChecklistItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        var req = ctx.bodyAsClass(ChecklistItemRequest.class);
        ticketService.updateChecklistItem(
                itemId,
                ticketId,
                req.title(),
                req.checked() != null && req.checked(),
                session.member().id());
        ctx.status(HttpStatus.OK);
    }

    private void deleteChecklistItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        ticketService.deleteChecklistItem(itemId, ticketId, session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void reorderChecklist(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(ReorderChecklistRequest.class);
        ticketService.reorderChecklistItems(ticketId, req.orderedIds());
        ctx.status(HttpStatus.OK);
    }

    // -- Activity & Comments --

    private void getTransitions(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findTransitions(ticketId));
    }

    private void getComments(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findComments(ticketId));
    }

    private void createComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(CommentRequest.class);
        if (req.content() == null || req.content().isBlank()) throw new BadRequestResponse("content is required");
        ctx.status(HttpStatus.CREATED)
                .json(ticketService.createComment(
                        ticketId, req.parentId(), session.member().id(), req.content()));
    }

    private void updateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(CommentRequest.class);
        ticketService.updateComment(commentId, req.content());
        ctx.status(HttpStatus.OK);
    }

    private void deleteComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        ticketService.deleteComment(commentId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Weblinks --

    private void getWeblinks(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findWeblinks(ticketId));
    }

    private void addWeblink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(WeblinkRequest.class);
        if (req.url() == null || req.url().isBlank()) throw new BadRequestResponse("url is required");
        ctx.status(HttpStatus.CREATED)
                .json(ticketService.addWeblink(ticketId, req.url(), req.title() != null ? req.title() : ""));
    }

    private void deleteWeblink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int weblinkId = ctx.pathParamAsClass("weblinkId", Integer.class).get();
        ticketService.deleteWeblink(weblinkId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Attachments --

    private void getAttachments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findAttachments(ticketId));
    }

    private void uploadAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("No file uploaded");
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            var att = ticketService.uploadAttachment(
                    ticketId,
                    file.filename(),
                    file.contentType(),
                    data,
                    session.member().id());
            ctx.status(HttpStatus.CREATED).json(att);
        } catch (java.io.IOException e) {
            throw new io.javalin.http.InternalServerErrorResponse("Failed to read uploaded file");
        }
    }

    private void downloadAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int attachmentId = ctx.pathParamAsClass("attachmentId", Integer.class).get();
        var att = ticketService.findAttachmentById(attachmentId).orElseThrow(NotFoundResponse::new);
        var path = ticketService.getAttachmentPath(att);
        if (!java.nio.file.Files.exists(path)) throw new NotFoundResponse();
        ctx.contentType(att.contentType());
        ctx.header("Content-Disposition", "attachment; filename=\"" + att.originalName() + "\"");
        try {
            ctx.result(java.nio.file.Files.newInputStream(path));
        } catch (java.io.IOException e) {
            throw new io.javalin.http.InternalServerErrorResponse("Failed to read file");
        }
    }

    private void deleteAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int attachmentId = ctx.pathParamAsClass("attachmentId", Integer.class).get();
        if (ticketService.deleteAttachment(attachmentId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Watchers --

    private void getWatchers(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findWatchers(ticketId));
    }

    private void watchTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ticketService.watchTicket(ticketId, session.member().id());
        ctx.status(HttpStatus.CREATED);
    }

    private void unwatchTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ticketService.unwatchTicket(ticketId, session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Field values --

    private void getFieldValues(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findFieldValues(ticketId));
    }

    private void setFieldValue(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        var req = ctx.bodyAsClass(FieldValueRequest.class);
        ticketService.setFieldValue(ticketId, fieldId, req.value());
        ticketService.logHistory(
                ticketId, "FIELD_CHANGED", "Feld #" + fieldId, session.member().id());
        ctx.status(HttpStatus.OK);
    }

    private void deleteFieldValue(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        ticketService.deleteFieldValue(ticketId, fieldId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Ticket Labels --

    private void getTicketLabels(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(boardService.findLabelsForTicket(ticketId));
    }

    private void addTicketLabel(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        boardService.addLabelToTicket(ticketId, labelId);
        var label = boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .orElse(null);
        ticketService.logHistory(
                ticketId,
                "LABEL_ADDED",
                label != null ? label.name() : "?",
                session.member().id());
        ctx.status(HttpStatus.CREATED).json(boardService.findLabelsForTicket(ticketId));
    }

    private void removeTicketLabel(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        var label = boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .orElse(null);
        boardService.removeLabelFromTicket(ticketId, labelId);
        ticketService.logHistory(
                ticketId,
                "LABEL_REMOVED",
                label != null ? label.name() : "?",
                session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- KB Links --

    private void getKbLinks(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findKbLinks(ticketId));
    }

    private void addKbLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int kbFileId = ctx.pathParamAsClass("kbFileId", Integer.class).get();
        var link = ticketService.addKbLink(ticketId, kbFileId);
        if (link != null) ctx.status(HttpStatus.CREATED).json(link);
        else ctx.status(HttpStatus.OK).json(ticketService.findKbLinks(ticketId));
    }

    private void removeKbLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int linkId = ctx.pathParamAsClass("linkId", Integer.class).get();
        ticketService.removeKbLink(linkId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- History --

    private void getHistory(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findHistory(ticketId));
    }

    // -- Activity --

    private void getActivity(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findActivity(ticketId));
    }

    // -- Request records --

    public record CreateTicketRequest(
            int laneId,
            String title,
            String description,
            Integer assignedMemberId,
            TicketPriority priority,
            LocalDate dueDate) {}

    public record UpdateTicketRequest(
            String title, String description, Integer assignedMemberId, TicketPriority priority, LocalDate dueDate) {}

    public record MoveTicketRequest(int toLaneId, int position) {}

    public record ReorderRequest(int laneId, List<Integer> orderedIds) {}

    public record LinkRequest(int linkedTicketId, LinkType linkType) {}

    public record ChecklistItemRequest(String title, Boolean checked) {}

    public record ReorderChecklistRequest(List<Integer> orderedIds) {}

    public record AssignRequest(Integer assignedMemberId) {}

    public record FieldValueRequest(Object value) {}

    public record CommentRequest(Integer parentId, String content) {}

    public record WeblinkRequest(String url, String title) {}
}
