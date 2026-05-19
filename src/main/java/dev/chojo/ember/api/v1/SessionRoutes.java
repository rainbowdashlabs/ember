/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.entity.Account;
import dev.chojo.ember.entity.AccountSession;
import dev.chojo.ember.entity.MemberGroup;
import dev.chojo.ember.entity.StationMember;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.service.AuthService;
import dev.chojo.ember.service.GdprDeletionService;
import dev.chojo.ember.service.GdprExportService;
import dev.chojo.ember.service.MemberGroupService;
import dev.chojo.ember.service.ProfileFieldService;
import dev.chojo.ember.service.StationMemberService;
import dev.chojo.ember.service.StationService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

@Singleton
public class SessionRoutes implements Routes {
    private final StationService stationService;
    private final StationMemberService memberService;
    private final MemberGroupService groupService;
    private final AuthService authService;
    private final ProfileFieldService profileFieldService;
    private final AccountRepository accountRepository;
    private final GdprExportService gdprExportService;
    private final GdprDeletionService gdprDeletionService;
    private final Api apiConfig;

    @Inject
    public SessionRoutes(
            StationService stationService,
            StationMemberService memberService,
            MemberGroupService groupService,
            AuthService authService,
            ProfileFieldService profileFieldService,
            AccountRepository accountRepository,
            GdprExportService gdprExportService,
            GdprDeletionService gdprDeletionService,
            Api apiConfig) {
        this.stationService = stationService;
        this.memberService = memberService;
        this.groupService = groupService;
        this.authService = authService;
        this.accountRepository = accountRepository;
        this.profileFieldService = profileFieldService;
        this.gdprExportService = gdprExportService;
        this.gdprDeletionService = gdprDeletionService;
        this.apiConfig = apiConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/session", this::getSessionInfo, Roles.LOGIN);
        routes.get(prefix + "/session/stations", this::getStations, Roles.LOGIN);
        routes.get(prefix + "/session/active", this::getActiveSessions, Roles.LOGIN);
        routes.delete(prefix + "/session/active/{id}", this::invalidateSession, Roles.LOGIN);
        routes.post(prefix + "/session/invalidate-all", this::invalidateAll, Roles.LOGIN);
        routes.get(prefix + "/session/gdpr-export", this::gdprExport, Roles.LOGIN);
        routes.get(prefix + "/session/avatar", this::getAvatar, Roles.LOGIN);
        routes.post(prefix + "/session/avatar", this::uploadAvatar, Roles.LOGIN);
        routes.delete(prefix + "/session/avatar", this::deleteAvatar, Roles.LOGIN);
        routes.get(prefix + "/accounts/{accountId}/avatar", this::getAvatarByAccount);
        routes.delete(prefix + "/session/account", this::deleteAccount, Roles.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/session",
            methods = HttpMethod.GET,
            summary = "Get current session info",
            description = "Returns account info, roles for the current station, managed members, and groups.",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SessionInfo.class)))
    private void getSessionInfo(Context ctx) {
        UserSession session = UserSession.from(ctx);

        List<StationMember> managed = List.of();
        List<MemberGroup> groups = List.of();
        MemberInfo memberInfo = null;

        if (session.member() != null) {
            managed = memberService.findManaged(session.member().id());
            groups = groupService.findGroupsForMember(session.member().id());
            memberInfo = new MemberInfo(
                    session.member().id(),
                    session.member().stationId(),
                    session.member().accountId());
        }

        var roleNames = session.roles().stream().map(Enum::name).sorted().toList();
        boolean profileComplete = true;
        if (session.member() != null && session.stationId() != null) {
            profileComplete =
                    profileFieldService.isProfileComplete(session.member().id(), session.stationId(), roleNames);
        }

        var managedInfos = managed.stream()
                .map(m -> {
                    Account account = m.accountId() != null
                            ? accountRepository.findById(m.accountId()).orElse(null)
                            : null;
                    String name = account != null
                            ? (account.firstName() + " " + account.lastName()).trim()
                            : (m.displayName() != null ? m.displayName() : "");
                    String email = account != null ? account.email() : "";
                    return new ManagedMemberInfo(
                            m.id(), m.stationId(), m.accountId() != null ? m.accountId() : 0, name, email);
                })
                .toList();

        ctx.json(new SessionInfo(
                new AccountInfo(
                        session.account().id(),
                        session.account().email(),
                        session.account().firstName(),
                        session.account().lastName()),
                session.stationId(),
                memberInfo,
                roleNames,
                managedInfos,
                groups,
                profileComplete));
    }

    @OpenApi(
            path = "/api/v1/session/stations",
            methods = HttpMethod.GET,
            summary = "List stations the current user is a member of",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationMembership[].class)))
    private void getStations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        List<StationMember> memberships = memberService.findByAccount(session.accountId());
        List<StationMembership> result = memberships.stream()
                .map(m -> {
                    var station = stationService.findById(m.stationId()).orElse(null);
                    String stationName = station != null ? station.name() : null;
                    return new StationMembership(m.id(), m.stationId(), stationName);
                })
                .toList();
        ctx.json(result);
    }

    @OpenApi(
            path = "/api/v1/session/active",
            methods = HttpMethod.GET,
            summary = "List active sessions for the current account",
            description = "Returns all active sessions with user agent and last-used timestamp.",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ActiveSession[].class)))
    private void getActiveSessions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String authHeader = ctx.header("Authorization");
        String currentToken = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : "";
        List<AccountSession> sessions = authService.findSessionsByAccount(session.accountId());
        List<ActiveSession> result = sessions.stream()
                .map(s -> new ActiveSession(
                        s.id(),
                        s.userAgent(),
                        s.createdAt(),
                        s.lastUsedAt(),
                        s.expiresAt(),
                        s.token().equals(currentToken)))
                .toList();
        ctx.json(result);
    }

    @OpenApi(
            path = "/api/v1/session/active/{id}",
            methods = HttpMethod.DELETE,
            summary = "Invalidate a specific session",
            tags = {"Session"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void invalidateSession(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        accountRepository.deleteSessionById(id, session.accountId());
        ctx.status(io.javalin.http.HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/session/invalidate-all",
            methods = HttpMethod.POST,
            summary = "Invalidate all sessions for the current account",
            description = "Deletes all sessions including the current one. The user will need to log in again.",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void invalidateAll(Context ctx) {
        UserSession session = UserSession.from(ctx);
        authService.invalidateAllSessions(session.accountId());
        ctx.json(new MessageResponse("All sessions invalidated"));
    }

    // -- Response records --

    public record SessionInfo(
            AccountInfo account,
            Integer stationId,
            MemberInfo member,
            List<String> roles,
            List<ManagedMemberInfo> managedMembers,
            List<MemberGroup> groups,
            boolean profileComplete) {}

    public record ManagedMemberInfo(int id, int stationId, int accountId, String name, String email) {}

    public record AccountInfo(int id, String email, String firstName, String lastName) {}

    public record MemberInfo(int id, int stationId, int accountId) {}

    public record StationMembership(int memberId, int stationId, String stationName) {}

    public record ActiveSession(
            int id, String userAgent, Instant createdAt, Instant lastUsedAt, Instant expiresAt, boolean isCurrent) {}

    @OpenApi(
            path = "/api/v1/session/account",
            methods = HttpMethod.DELETE,
            summary = "Delete account and anonymize all data (GDPR/DSGVO)",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "204"))
    private void deleteAccount(Context ctx) {
        UserSession session = UserSession.from(ctx);
        gdprDeletionService.deleteAccount(session.accountId());
        ctx.status(io.javalin.http.HttpStatus.NO_CONTENT);
    }

    // -- Avatar --

    private static final java.util.Set<String> ALLOWED_AVATAR_TYPES =
            java.util.Set.of("image/png", "image/jpeg", "image/webp");

    private void getAvatar(Context ctx) {
        UserSession session = UserSession.from(ctx);
        serveAvatar(ctx, session.accountId());
    }

    private void getAvatarByAccount(Context ctx) {
        int accountId = ctx.pathParamAsClass("accountId", Integer.class).get();
        serveAvatar(ctx, accountId);
    }

    private void serveAvatar(Context ctx, int accountId) {
        accountRepository
                .findAvatar(accountId)
                .ifPresentOrElse(
                        avatar -> {
                            ctx.contentType(avatar.contentType());
                            ctx.header("Cache-Control", "public, max-age=3600");
                            ctx.result(avatar.data());
                        },
                        () -> ctx.status(io.javalin.http.HttpStatus.NOT_FOUND));
    }

    private void uploadAvatar(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var file = ctx.uploadedFile("avatar");
        if (file == null) {
            throw new io.javalin.http.BadRequestResponse("No file uploaded");
        }
        if (!ALLOWED_AVATAR_TYPES.contains(file.contentType())) {
            throw new io.javalin.http.BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        try {
            byte[] data = file.content().readAllBytes();
            if (data.length > apiConfig.maxAvatarSizeBytes()) {
                throw new io.javalin.http.BadRequestResponse("File too large");
            }
            accountRepository.updateAvatar(session.accountId(), data, file.contentType());
            ctx.json(new MessageResponse("Avatar updated"));
        } catch (java.io.IOException e) {
            throw new io.javalin.http.InternalServerErrorResponse("Failed to read file");
        }
    }

    private void deleteAvatar(Context ctx) {
        UserSession session = UserSession.from(ctx);
        accountRepository.deleteAvatar(session.accountId());
        ctx.status(io.javalin.http.HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/session/gdpr-export",
            methods = HttpMethod.GET,
            summary = "Export all personal data (GDPR/DSGVO)",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "200"))
    private void gdprExport(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var data = gdprExportService.exportAccountData(session.accountId());
        ctx.contentType("application/json");
        ctx.header("Content-Disposition", "attachment; filename=\"gdpr-export.json\"");
        ctx.json(data);
    }
}
