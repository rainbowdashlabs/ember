/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import io.javalin.http.Context;

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
}
