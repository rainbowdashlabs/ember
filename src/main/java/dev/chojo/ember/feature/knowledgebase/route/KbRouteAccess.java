/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * Station-ownership guards shared by the knowledge-base route classes. Every guard answers
 * {@code 404} when the entity is absent or owned by another station, so an id from one station
 * cannot expose its content, metadata, comments, or existence to another.
 */
final class KbRouteAccess {

    private KbRouteAccess() {}

    /**
     * Loads a knowledge-base file and asserts it belongs to the caller's station.
     */
    static KbFile requireOwnedFile(Context ctx, KnowledgeBaseService service, int fileId) {
        return requireOwnedOrNotFound(ctx, fileId, service::findFile, KbFile::stationId);
    }

    /**
     * Loads a knowledge-base folder and asserts it belongs to the caller's station.
     */
    static KbFolder requireOwnedFolder(Context ctx, KnowledgeBaseService service, int folderId) {
        return requireOwnedOrNotFound(ctx, folderId, service::findFolder, KbFolder::stationId);
    }

    /**
     * Reads the caller's access context, carrying the station-wide knowledge rights so the grant
     * resolution knows what they may do where the tree says nothing.
     *
     * <p>A session with station rights but no member row of its own matches no audience, so it
     * carries no memberships rather than failing the request.
     */
    static MemberAccess accessOf(Context ctx, KbAccessService accessService) {
        var session = UserSession.from(ctx);
        boolean canEdit = session.hasPermission(StationPermission.KNOWLEDGE_EDIT);
        boolean canManage = session.hasPermission(StationPermission.KNOWLEDGE_MANAGER);
        var member = session.member();
        if (member == null) {
            return new MemberAccess(0, null, List.of(), List.of(), canEdit, canManage);
        }
        return accessService.memberAccess(member.id(), member.userType(), canEdit, canManage);
    }

    /**
     * Asserts the caller holds at least {@code required} on a folder or file.
     *
     * <p>Answers {@code 404} rather than {@code 403}: the station already made that choice for
     * cross-station access, and the reasoning carries - a {@code 403} confirms the item exists.
     *
     * @param folderId the folder, or {@code null} when guarding a file
     * @param fileId   the file, or {@code null} when guarding a folder
     */
    static void requireLevel(
            Context ctx, KbAccessService accessService, Integer folderId, Integer fileId, KbAccessLevel required) {
        var level = accessService.effectiveLevel(accessOf(ctx, accessService), folderId, fileId);
        if (!level.covers(required)) throw new NotFoundResponse();
    }
}
