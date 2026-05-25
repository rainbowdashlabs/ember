/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.legal.service.GdprDeletionService;
import dev.chojo.ember.feature.legal.service.GdprExportService;
import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserSettingsRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Routes for session management including session info, active session listing, session invalidation,
 * avatar management, GDPR data export, and account deletion.
 */
@Singleton
public class SessionRoutes implements Routes {
    private static final Logger log = getLogger(SessionRoutes.class);
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private final StationService stationService;
    private final StationMemberService memberService;
    private final MemberGroupService groupService;
    private final AuthService authService;
    private final ProfileFieldService profileFieldService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final GdprExportService gdprExportService;
    private final GdprDeletionService gdprDeletionService;
    private final Api apiConfig;
    private final ImageService imageService;
    private final UserSettingsRepository userSettingsRepository;
    private final UserTagRepository userTagRepository;

    @Inject
    public SessionRoutes(
            StationService stationService,
            StationMemberService memberService,
            MemberGroupService groupService,
            AuthService authService,
            ProfileFieldService profileFieldService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            GdprExportService gdprExportService,
            GdprDeletionService gdprDeletionService,
            Api apiConfig,
            ImageService imageService,
            UserSettingsRepository userSettingsRepository,
            UserTagRepository userTagRepository) {
        this.stationService = stationService;
        this.memberService = memberService;
        this.groupService = groupService;
        this.authService = authService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.profileFieldService = profileFieldService;
        this.gdprExportService = gdprExportService;
        this.gdprDeletionService = gdprDeletionService;
        this.apiConfig = apiConfig;
        this.imageService = imageService;
        this.userSettingsRepository = userSettingsRepository;
        this.userTagRepository = userTagRepository;
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
        routes.get(prefix + "/members/{memberId}/avatar", this::getAvatarByMember, Roles.LOGIN);
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
        List<UserTag> tags = List.of();
        List<Integer> roleIds = List.of();
        List<Integer> groupIds = List.of();
        List<Integer> tagIds = List.of();
        MemberInfo memberInfo = null;

        if (session.member() != null) {
            managed = memberService.findManaged(session.member().id());
            groups = groupService.findGroupsForMember(session.member().id());
            tags = userTagRepository.findTagsForMember(session.member().id());
            roleIds = stationMemberRepository.findRoles(session.member().id()).stream()
                    .map(Role::id)
                    .toList();
            groupIds = groups.stream().map(MemberGroup::id).toList();
            tagIds = tags.stream().map(UserTag::id).toList();
            memberInfo = new MemberInfo(
                    session.member().id(),
                    session.stationUid() != null ? session.stationUid().toString() : null,
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
                    var managedStation = stationService.findById(m.stationId()).orElse(null);
                    String managedStationUid =
                            managedStation != null ? managedStation.uid().toString() : null;
                    return new ManagedMemberInfo(
                            m.id(), managedStationUid, m.accountId() != null ? m.accountId() : 0, name, email);
                })
                .toList();

        var disabledModules = session.stationId() != null
                ? stationService.findDisabledModules(session.stationId())
                : Set.<StationModule>of();

        // Build theme info
        ThemeInfo themeInfo = null;
        if (session.member() != null && session.stationId() != null) {
            var station = stationService.findById(session.stationId()).orElse(null);
            var userSettings =
                    userSettingsRepository.findOrCreate(session.member().id());
            if (station != null) {
                themeInfo = new ThemeInfo(
                        station.defaultTheme(),
                        station.allowUserTheme(),
                        station.customThemeColors(),
                        userSettings.theme(),
                        userSettings.darkMode());
            }
        }

        ctx.json(new SessionInfo(
                new AccountInfo(
                        session.account().id(),
                        session.account().email(),
                        session.account().firstName(),
                        session.account().lastName()),
                session.stationUid() != null ? session.stationUid().toString() : null,
                memberInfo,
                roleNames,
                managedInfos,
                groups,
                tags,
                roleIds,
                groupIds,
                tagIds,
                profileComplete,
                disabledModules,
                themeInfo,
                session.stationId() != null
                        ? stationService
                                .findById(session.stationId())
                                .map(s -> s.publicKbMode().name())
                                .orElse("OFF")
                        : null));
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
                    String stationUid = station != null ? station.uid().toString() : null;
                    return new StationMembership(m.id(), stationUid, stationName);
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
                        s.token().equals(currentToken),
                        s.location()))
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
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Response records --

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

    @OpenApi(
            path = "/api/v1/session/account",
            methods = HttpMethod.DELETE,
            summary = "Delete account and anonymize all data (GDPR/DSGVO)",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "204"))
    private void deleteAccount(Context ctx) {
        UserSession session = UserSession.from(ctx);
        // Block deletion if the account is a station manager
        var memberships = stationMemberRepository.findAllByAccountId(session.accountId());
        for (var member : memberships) {
            var roles = stationMemberRepository.findRoles(member.id());
            boolean isManager = roles.stream().anyMatch(r -> r.role() == Roles.MANAGER);
            if (isManager) {
                throw new BadRequestResponse(
                        "Cannot delete account while you are a station manager. Transfer or delete the station first.");
            }
        }
        gdprDeletionService.deleteAccount(session.accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Retrieves the avatar for the current session's member. Returns 404 if no membership or no avatar.
     */
    private void getAvatar(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        serveAvatar(ctx, session.member().id());
    }

    /**
     * Retrieves the avatar for a specific member by their ID path parameter.
     */
    private void getAvatarByMember(Context ctx) {
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        serveAvatar(ctx, memberId);
    }

    /**
     * Serves a member's avatar from disk with appropriate content type and cache headers.
     */
    private void serveAvatar(Context ctx, int memberId) {
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        imageService
                .read(ImageCategory.AVATARS, String.valueOf(memberId), size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "private, max-age=300");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    private void uploadAvatar(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            throw new BadRequestResponse("No station membership");
        }
        var file = ctx.uploadedFile("avatar");
        if (file == null) {
            throw new BadRequestResponse("No file uploaded");
        }
        if (!ALLOWED_AVATAR_TYPES.contains(file.contentType())) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            imageService.store(
                    ImageCategory.AVATARS,
                    String.valueOf(session.member().id()),
                    data,
                    file.contentType(),
                    apiConfig.maxImageSizeBytes());
            ctx.json(new MessageResponse("Avatar updated"));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        } catch (IOException e) {
            log.error("Failed to process image", e);
            throw new InternalServerErrorResponse("Failed to process image");
        }
    }

    private void deleteAvatar(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            throw new BadRequestResponse("No station membership");
        }
        imageService.delete(
                ImageCategory.AVATARS, String.valueOf(session.member().id()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Avatar --

    @OpenApi(
            path = "/api/v1/session/gdpr-export",
            methods = HttpMethod.GET,
            summary = "Export all personal data (GDPR/DSGVO)",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "200"))
    private void gdprExport(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String locale = ctx.queryParam("locale");
        byte[] zipData = gdprExportService.exportAccountDataAsZip(session.accountId(), locale);
        ctx.contentType("application/zip");
        ctx.header("Content-Disposition", "attachment; filename=\"gdpr-export.zip\"");
        ctx.result(zipData);
    }

    /**
     * Aggregated session information returned to the authenticated user.
     *
     * @param account         the account details
     * @param stationId       the currently selected station, or {@code null} if none
     * @param member          the station membership info, or {@code null} if not a member
     * @param roles           sorted list of role names for the current station
     * @param managedMembers  list of members managed by this account
     * @param groups          groups the current member belongs to
     * @param profileComplete whether all required profile fields are filled
     * @param disabledModules set of modules disabled for the current station
     */
    public record SessionInfo(
            AccountInfo account,
            String stationId,
            MemberInfo member,
            List<String> roles,
            List<ManagedMemberInfo> managedMembers,
            List<MemberGroup> groups,
            List<UserTag> tags,
            List<Integer> roleIds,
            List<Integer> groupIds,
            List<Integer> tagIds,
            boolean profileComplete,
            Set<StationModule> disabledModules,
            ThemeInfo theme,
            String publicKbMode) {}

    public record ThemeInfo(
            String defaultTheme,
            boolean allowUserTheme,
            String customThemeColors,
            String userTheme,
            String userDarkMode) {}

    /**
     * Summary of a member managed by the current account.
     *
     * @param id        the member identifier
     * @param stationId the station identifier
     * @param accountId the member's account identifier, or 0 if none
     * @param name      the member's display name
     * @param email     the member's email, or empty string if unavailable
     */
    public record ManagedMemberInfo(int id, String stationId, int accountId, String name, String email) {}

    /**
     * Account information included in the session response.
     *
     * @param id        the account identifier
     * @param email     the email address
     * @param firstName the first name
     * @param lastName  the last name
     */
    public record AccountInfo(int id, String email, String firstName, String lastName) {}

    /**
     * Minimal member information for the current session.
     *
     * @param id        the member identifier
     * @param stationId the station identifier
     * @param accountId the account identifier
     */
    public record MemberInfo(int id, String stationId, int accountId) {}

    /**
     * A station membership entry listing which stations the user belongs to.
     *
     * @param memberId    the member identifier
     * @param stationId   the station identifier
     * @param stationName the station name
     */
    public record StationMembership(int memberId, String stationId, String stationName) {}

    /**
     * Represents an active session as returned to the user.
     *
     * @param id         the session identifier
     * @param userAgent  the client's user agent string
     * @param createdAt  when the session was created
     * @param lastUsedAt when the session was last used
     * @param expiresAt  when the session expires
     * @param isCurrent  whether this is the session making the current request
     * @param location   the client's location
     */
    public record ActiveSession(
            int id,
            String userAgent,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt,
            boolean isCurrent,
            String location) {}
}
