/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.service.AvatarAccessService;
import dev.chojo.ember.feature.account.service.AvatarService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Routes serving and managing account avatars: the caller's own avatar plus the account- and
 * member-keyed lookups other users' clients render.
 */
@Singleton
public class AvatarRoutes implements Routes {
    private static final Logger log = getLogger(AvatarRoutes.class);
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private final AvatarService avatarService;
    private final AvatarAccessService avatarAccessService;
    private final Api apiConfig;

    @Inject
    public AvatarRoutes(AvatarService avatarService, AvatarAccessService avatarAccessService, Api apiConfig) {
        this.avatarService = avatarService;
        this.avatarAccessService = avatarAccessService;
        this.apiConfig = apiConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/session/avatar", this::getAvatar, StationPermission.LOGIN);
        routes.post(prefix + "/session/avatar", this::uploadAvatar, StationPermission.LOGIN);
        routes.delete(prefix + "/session/avatar", this::deleteAvatar, StationPermission.LOGIN);
        routes.get(prefix + "/accounts/{accountUid}/avatar", this::getAvatarByAccount, StationPermission.LOGIN);
        routes.get(
                prefix + "/members/{stationUid}/{memberUid}/avatar", this::getAvatarByMember, StationPermission.LOGIN);
    }

    /**
     * Retrieves the avatar for the current session's account. Returns 404 if no avatar is stored.
     */
    private void getAvatar(Context ctx) {
        serveAvatar(ctx, avatarAccessService.ownAvatarUid(UserSession.from(ctx)));
    }

    /**
     * Retrieves the avatar for a specific account by its UUID path parameter. Returns
     * 404 when the caller has no relationship to the target account (no shared station
     * membership, no federation partnership, no admin role).
     */
    private void getAvatarByAccount(Context ctx) {
        UUID accountUid = pathUuid(ctx, "accountUid");
        serveAvatar(ctx, avatarAccessService.accountAvatarUid(UserSession.from(ctx), accountUid));
    }

    /**
     * Retrieves the avatar for a specific member by their UUID path parameter. Kept for
     * the transition window while the frontend migrates to the account-keyed endpoint;
     * resolves the underlying account UUID and falls through to the same disk lookup.
     */
    private void getAvatarByMember(Context ctx) {
        UUID stationUid = pathUuid(ctx, "stationUid");
        UUID memberUid = pathUuid(ctx, "memberUid");
        serveAvatar(ctx, avatarAccessService.memberAvatarUid(UserSession.from(ctx), stationUid, memberUid));
    }

    /**
     * Serves an avatar with appropriate content type and cache headers, given the account UUID
     * the avatar is stored under. An empty UUID means the caller may not see it — answered with
     * 404 so a missing target and a missing permission look the same.
     */
    private void serveAvatar(Context ctx, Optional<UUID> accountUid) {
        if (accountUid.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        avatarService
                .read(accountUid.get(), size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "private, max-age=300");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NO_CONTENT));
    }

    private void uploadAvatar(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var file = ctx.uploadedFile("avatar");
        if (file == null) {
            throw new BadRequestResponse("No file uploaded");
        }
        if (!ALLOWED_AVATAR_TYPES.contains(file.contentType())) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        UUID accountUid = requireOwnAvatarUid(session);
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            avatarService.store(accountUid, data, file.contentType(), apiConfig.maxImageSizeBytes());
            ctx.json(new MessageResponse("Avatar updated"));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        } catch (IOException e) {
            log.error("Failed to process image", e);
            throw new InternalServerErrorResponse("Failed to process image");
        }
    }

    private void deleteAvatar(Context ctx) {
        avatarService.delete(requireOwnAvatarUid(UserSession.from(ctx)));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Resolves the account UUID of the caller for a write operation, rejecting sessions without a
     * resolvable account instead of silently writing nothing.
     */
    private UUID requireOwnAvatarUid(UserSession session) {
        if (session.account() == null) {
            throw new BadRequestResponse("No account in session");
        }
        return avatarAccessService
                .ownAvatarUid(session)
                .orElseThrow(() -> new BadRequestResponse("Account UUID not found"));
    }
}
