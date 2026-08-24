/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.board.entity.BoardTicketAttachment;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * File attachments of a local board ticket: listing, upload, download and deletion.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class BoardTicketAttachmentRoutes implements Routes {

    private final BoardTicketService ticketService;
    private final BoardRouteGuards guards;
    private final Api apiConfig;

    @Inject
    public BoardTicketAttachmentRoutes(BoardTicketService ticketService, BoardRouteGuards guards, Api apiConfig) {
        this.ticketService = ticketService;
        this.guards = guards;
        this.apiConfig = apiConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String p = prefix + "/boards/{boardKey}/tickets/{ticketNumber}/attachments";
        routes.get(p, this::getAttachments, StationPermission.BOARD_USE);
        routes.post(p, this::uploadAttachment, StationPermission.BOARD_USE);
        routes.get(p + "/{attachmentId}/download", this::downloadAttachment, StationPermission.BOARD_USE);
        routes.delete(p + "/{attachmentId}", this::deleteAttachment, StationPermission.BOARD_USE);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/attachments",
            methods = HttpMethod.GET,
            summary = "List attachments on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketAttachment[].class)))
    private void getAttachments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(ticketService.findAttachments(guards.viewableTicketId(ctx, session)));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/attachments",
            methods = HttpMethod.POST,
            summary = "Upload an attachment to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardTicketAttachment.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void uploadAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("No file uploaded");
        if (file.size() > apiConfig.maxUploadSizeBytes()) throw new BadRequestResponse("File too large");
        try (var content = file.content()) {
            var att = ticketService.uploadAttachment(
                    session.stationId(),
                    ticketId,
                    file.filename(),
                    file.contentType(),
                    content.readAllBytes(),
                    guards.actor(session));
            ctx.status(HttpStatus.CREATED).json(att);
        } catch (IOException e) {
            throw new InternalServerErrorResponse("Failed to read uploaded file");
        }
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/attachments/{attachmentId}/download",
            methods = HttpMethod.GET,
            summary = "Download an attachment",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void downloadAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var att = requireAttachmentOfTicket(ctx, guards.viewableTicketId(ctx, session));
        var path = ticketService.getAttachmentPath(session.stationId(), att);
        if (!Files.exists(path)) throw new NotFoundResponse();
        ctx.contentType(SafeInlineMime.safeContentType(att.contentType()));
        ctx.header(
                "Content-Disposition",
                SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, att.originalName()));
        try {
            ctx.result(Files.newInputStream(path));
        } catch (IOException e) {
            throw new InternalServerErrorResponse("Failed to read file");
        }
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/attachments/{attachmentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete an attachment",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var att = requireAttachmentOfTicket(ctx, guards.editableTicketId(ctx, session));
        if (ticketService.deleteAttachment(session.stationId(), att.id())) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    /**
     * Loads the attachment named in the path and asserts it hangs on the ticket the path names.
     *
     * <p>The board and the ticket are resolved against the caller's station, so tying the
     * attachment to that ticket is what scopes it. Reading the file happened to fail for an
     * attachment of another station because the path is built from the caller's station, but that
     * is a property of the storage layout rather than an access decision, and deleting the row
     * never consulted the layout at all.
     */
    private BoardTicketAttachment requireAttachmentOfTicket(Context ctx, int ticketId) {
        var att = ticketService.findAttachmentById(pathInt(ctx, "attachmentId")).orElseThrow(NotFoundResponse::new);
        if (att.ticketId() != ticketId) throw new NotFoundResponse();
        return att;
    }
}
