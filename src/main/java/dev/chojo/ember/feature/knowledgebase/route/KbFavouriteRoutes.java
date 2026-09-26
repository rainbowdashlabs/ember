/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.knowledgebase.entity.KbFavourite;
import dev.chojo.ember.feature.knowledgebase.entity.KbFavouriteTarget;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbFavouriteService;
import dev.chojo.ember.feature.members.entity.StationMember;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.feature.knowledgebase.route.KbRouteAccess.accessOf;
import static dev.chojo.ember.feature.knowledgebase.route.KbRouteAccess.readerUserType;

/**
 * The reader's own favourites in the wiki: listing them, marking something, and taking a mark off.
 *
 * <p>A favourite belongs to a member, so a session with station rights but no member row of its own
 * has none and can mark nothing.
 */
@Singleton
public class KbFavouriteRoutes implements Routes {
    private final KbFavouriteService favourites;
    private final KbAccessService accessService;

    @Inject
    public KbFavouriteRoutes(KbFavouriteService favourites, KbAccessService accessService) {
        this.favourites = favourites;
        this.accessService = accessService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/kb/favourites", this::list, StationPermission.USER);
        routes.post(prefix + "/kb/favourites", this::mark, StationPermission.USER);
        routes.delete(prefix + "/kb/favourites/{id}", this::unmark, StationPermission.USER);
    }

    private static StationMember requireMember(UserSession session) {
        var member = session.member();
        if (member == null) throw Refusal.KB_FAVOURITES_NEED_A_MEMBER.raise();
        return member;
    }

    private void list(Context ctx) {
        requireMember(UserSession.from(ctx));
        ctx.json(favourites.list(accessOf(ctx, accessService)));
    }

    private void mark(Context ctx) {
        var session = UserSession.from(ctx);
        var member = requireMember(session);
        var request = ctx.bodyAsClass(MarkFavouriteRequest.class);
        if (request.target() == null) throw Refusal.KB_FAVOURITE_NEEDS_A_TARGET.raise();
        KbFavourite marked;
        if (request.target().isPartner()) {
            if (request.partnerStationUid() == null) throw Refusal.KB_FAVOURITE_NEEDS_A_PARTNER.raise();
            marked = favourites.markPartner(
                    session.stationId(),
                    member.id(),
                    readerUserType(session),
                    request.target(),
                    request.partnerStationUid(),
                    request.entryId());
        } else {
            marked = favourites.markLocal(
                    session.stationId(), accessOf(ctx, accessService), request.target(), request.entryId());
        }
        ctx.status(HttpStatus.CREATED).json(marked);
    }

    private void unmark(Context ctx) {
        var member = requireMember(UserSession.from(ctx));
        if (!favourites.unmark(member.id(), pathInt(ctx, "id"))) throw Refusal.KB_FAVOURITE_NOT_HERE.raise();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * What to mark: an entry of this station by its id, or a partner's by the partner and the id it
     * has there.
     *
     * @param partnerStationUid the partner, for {@code PARTNER_FILE} and {@code PARTNER_FOLDER} only
     */
    public record MarkFavouriteRequest(KbFavouriteTarget target, int entryId, UUID partnerStationUid) {}
}
