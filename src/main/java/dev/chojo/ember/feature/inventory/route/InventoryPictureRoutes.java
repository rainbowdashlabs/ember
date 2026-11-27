/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.inventory.service.InventoryPictureService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The photos of inventories and of their kinds, and the photo a piece is shown with.
 *
 * <p>Anybody who may read the inventory sees its photos; changing them needs the right to edit it.
 * A photo is sent as the multipart field {@code image}, in PNG, JPEG or WebP, and is weighed against
 * the instance's limit for pictures. It is answered with the key it is now kept under, which the
 * pages put into the address they ask for it with, so a browser never shows a cached older photo.
 */
@Singleton
public class InventoryPictureRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(InventoryPictureRoutes.class);
    private static final Set<String> TAKEN_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final String INVENTORY_PICTURE = "/inventories/{inventoryId}/picture";
    private static final String ART_PICTURE = "/inventories/{inventoryId}/arts/{artId}/picture";
    private static final String ITEM_PICTURE = "/inventory-items/{itemId}/picture";

    private final InventoryPictureService pictureService;
    private final Api apiConfig;

    @Inject
    public InventoryPictureRoutes(InventoryPictureService pictureService, Api apiConfig) {
        this.pictureService = pictureService;
        this.apiConfig = apiConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + INVENTORY_PICTURE, this::inventoryPicture, StationPermission.INVENTORY_READ);
        routes.put(prefix + INVENTORY_PICTURE, this::storeInventoryPicture, StationPermission.INVENTORY_EDIT);
        routes.delete(prefix + INVENTORY_PICTURE, this::removeInventoryPicture, StationPermission.INVENTORY_EDIT);
        routes.get(prefix + ART_PICTURE, this::artPicture, StationPermission.INVENTORY_READ);
        routes.put(prefix + ART_PICTURE, this::storeArtPicture, StationPermission.INVENTORY_EDIT);
        routes.delete(prefix + ART_PICTURE, this::removeArtPicture, StationPermission.INVENTORY_EDIT);
        routes.get(prefix + ITEM_PICTURE, this::itemPicture, StationPermission.INVENTORY_READ);
    }

    @OpenApi(
            path = "/api/v1" + INVENTORY_PICTURE,
            methods = HttpMethod.GET,
            summary = "The photo of an inventory",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "size", type = Integer.class, description = "Side in pixels"),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void inventoryPicture(Context ctx) {
        send(ctx, pictureService.readForInventory(stationId(ctx), pathInt(ctx, "inventoryId"), size(ctx)));
    }

    @OpenApi(
            path = "/api/v1" + INVENTORY_PICTURE,
            methods = HttpMethod.PUT,
            summary = "Give an inventory a new photo",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(type = "multipart/form-data")),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PictureResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void storeInventoryPicture(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        byte[] data = picture(ctx);
        ctx.json(new PictureResponse(stored(() ->
                pictureService.storeForInventory(stationId(ctx), inventoryId, data, apiConfig.maxImageSizeBytes()))));
    }

    @OpenApi(
            path = "/api/v1" + INVENTORY_PICTURE,
            methods = HttpMethod.DELETE,
            summary = "Take an inventory's photo away",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void removeInventoryPicture(Context ctx) {
        pictureService.removeFromInventory(stationId(ctx), pathInt(ctx, "inventoryId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1" + ART_PICTURE,
            methods = HttpMethod.GET,
            summary = "The photo of a kind",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "artId", type = Integer.class, required = true)
            },
            queryParams = @OpenApiParam(name = "size", type = Integer.class, description = "Side in pixels"),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void artPicture(Context ctx) {
        send(
                ctx,
                pictureService.readForArt(
                        stationId(ctx), pathInt(ctx, "inventoryId"), pathInt(ctx, "artId"), size(ctx)));
    }

    @OpenApi(
            path = "/api/v1" + ART_PICTURE,
            methods = HttpMethod.PUT,
            summary = "Give a kind a new photo, shown on its pieces before the inventory's",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "artId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(type = "multipart/form-data")),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PictureResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void storeArtPicture(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        int artId = pathInt(ctx, "artId");
        byte[] data = picture(ctx);
        ctx.json(new PictureResponse(stored(() ->
                pictureService.storeForArt(stationId(ctx), inventoryId, artId, data, apiConfig.maxImageSizeBytes()))));
    }

    @OpenApi(
            path = "/api/v1" + ART_PICTURE,
            methods = HttpMethod.DELETE,
            summary = "Take a kind's photo away",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "artId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void removeArtPicture(Context ctx) {
        pictureService.removeFromArt(stationId(ctx), pathInt(ctx, "inventoryId"), pathInt(ctx, "artId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1" + ITEM_PICTURE,
            methods = HttpMethod.GET,
            summary = "The photo a piece is shown with: its kind's, otherwise its inventory's",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "itemId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "size", type = Integer.class, description = "Side in pixels"),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void itemPicture(Context ctx) {
        send(ctx, pictureService.readForItem(stationId(ctx), pathInt(ctx, "itemId"), size(ctx)));
    }

    private static int stationId(Context ctx) {
        return StationSession.from(ctx).stationId();
    }

    private static int size(Context ctx) {
        return ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
    }

    private static void send(Context ctx, MediaContent picture) {
        ctx.contentType(picture.contentType());
        ctx.header("Cache-Control", "private, max-age=86400");
        ctx.result(picture.data());
    }

    private static byte[] picture(Context ctx) {
        UploadedFile file = ctx.uploadedFile("image");
        if (file == null) throw InventoryRefusal.INVENTORY_PICTURE_FILE_MISSING.raise();
        String contentType = file.contentType();
        if (contentType == null || !TAKEN_TYPES.contains(contentType)) {
            throw InventoryRefusal.INVENTORY_PICTURE_KIND_NOT_TAKEN.raise();
        }
        try (var content = file.content()) {
            return content.readAllBytes();
        } catch (IOException e) {
            log.warn("Could not read an uploaded inventory photo", e);
            throw InventoryRefusal.INVENTORY_PICTURE_NOT_TAKEN.raise();
        }
    }

    private static String stored(PictureWrite write) {
        try {
            return write.run();
        } catch (IllegalArgumentException e) {
            log.warn("An inventory photo was not one that could be kept", e);
            throw InventoryRefusal.INVENTORY_PICTURE_NOT_TAKEN.raise();
        } catch (IOException e) {
            log.error("Could not write an inventory photo", e);
            throw InventoryRefusal.INVENTORY_PICTURE_NOT_PROCESSED.raise();
        }
    }

    /** A write of a photo that answers with the key it is kept under. */
    @FunctionalInterface
    private interface PictureWrite {
        String run() throws IOException;
    }

    /**
     * @param pictureKey the key the photo is now kept under, which changes with every new photo
     */
    public record PictureResponse(String pictureKey) {}
}
