/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.legal.service.GdprDeletionService;
import dev.chojo.ember.feature.legal.service.GdprExportService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserSettingsRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.system.service.RequirementsService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
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
    private final AvatarService avatarService;
    private final UserSettingsRepository userSettingsRepository;
    private final UserTagRepository userTagRepository;
    private final Conf conf;
    private final TokenHasher tokenHasher;
    private final StationRepository stationRepository;
    private final FederationRepository federationRepository;
    private final NotificationService notificationService;
    private final RequirementsService requirementsService;
    private final AccessManager accessManager;

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
            AvatarService avatarService,
            UserSettingsRepository userSettingsRepository,
            UserTagRepository userTagRepository,
            Conf conf,
            TokenHasher tokenHasher,
            StationRepository stationRepository,
            FederationRepository federationRepository,
            NotificationService notificationService,
            RequirementsService requirementsService,
            AccessManager accessManager) {
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
        this.avatarService = avatarService;
        this.userSettingsRepository = userSettingsRepository;
        this.userTagRepository = userTagRepository;
        this.conf = conf;
        this.tokenHasher = tokenHasher;
        this.stationRepository = stationRepository;
        this.federationRepository = federationRepository;
        this.notificationService = notificationService;
        this.requirementsService = requirementsService;
        this.accessManager = accessManager;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/session", this::getSessionInfo, StationPermission.LOGIN);
        routes.get(prefix + "/session/stations", this::getStations, StationPermission.LOGIN);
        routes.get(
                prefix + "/session/cross-station-dashboard", this::getCrossStationDashboard, StationPermission.LOGIN);
        routes.get(prefix + "/session/active", this::getActiveSessions, StationPermission.LOGIN);
        routes.delete(prefix + "/session/active/{id}", this::invalidateSession, StationPermission.LOGIN);
        routes.post(
                prefix + "/session/invalidate-all",
                this::invalidateAll,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.get(prefix + "/session/gdpr-export", this::gdprExport, StationPermission.LOGIN);
        routes.get(prefix + "/session/avatar", this::getAvatar, StationPermission.LOGIN);
        routes.post(prefix + "/session/avatar", this::uploadAvatar, StationPermission.LOGIN);
        routes.delete(prefix + "/session/avatar", this::deleteAvatar, StationPermission.LOGIN);
        routes.get(prefix + "/accounts/{accountUid}/avatar", this::getAvatarByAccount, StationPermission.LOGIN);
        routes.get(
                prefix + "/members/{stationUid}/{memberUid}/avatar", this::getAvatarByMember, StationPermission.LOGIN);
        routes.delete(prefix + "/session/account", this::deleteAccount, StationPermission.LOGIN);
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
            roleIds = stationMemberRepository.findPermissions(session.member().id()).stream()
                    .map(Permission::id)
                    .toList();
            groupIds = groups.stream().map(MemberGroup::id).toList();
            tagIds = tags.stream().map(UserTag::id).toList();
            memberInfo = new MemberInfo(
                    session.member().id(),
                    session.stationUid() != null ? session.stationUid().toString() : null,
                    session.member().accountId(),
                    session.member().uid());
        }

        var roleNames = session.permissions().stream().map(Enum::name).sorted().toList();
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
                    UUID managedStationUid = managedStation != null ? managedStation.uid() : null;
                    UUID memberUid = m.uid() != null ? m.uid() : null;
                    return new ManagedMemberInfo(
                            m.id(),
                            managedStationUid,
                            memberUid,
                            m.accountId() != null ? m.accountId() : 0,
                            name,
                            email);
                })
                .toList();

        var disabledModules = session.stationId() != null
                ? stationService.findDisabledModules(session.stationId())
                : Set.<StationModule>of();

        // Build theme info
        ThemeInfo themeInfo = null;
        var theming = conf.main().theming();
        if (session.member() != null && session.stationId() != null) {
            var station = stationService.findById(session.stationId()).orElse(null);
            var userSettings =
                    userSettingsRepository.findOrCreate(session.member().id());
            if (station != null) {
                themeInfo = new ThemeInfo(
                        theming.defaultTheme(),
                        theming.defaultFeel(),
                        theming.lockFeel(),
                        station.defaultTheme(),
                        station.defaultFeel(),
                        station.allowUserTheme(),
                        station.allowUserFeel(),
                        station.customThemeColors(),
                        userSettings.theme(),
                        userSettings.darkMode(),
                        userSettings.feel());
            }
        }
        if (themeInfo == null) {
            themeInfo = new ThemeInfo(
                    theming.defaultTheme(),
                    theming.defaultFeel(),
                    theming.lockFeel(),
                    "ember",
                    ThemeFeel.ROUNDED,
                    true,
                    true,
                    null,
                    null,
                    null,
                    null);
        }

        Station currentStation = session.stationId() != null
                ? stationService.findById(session.stationId()).orElse(null)
                : null;

        ctx.json(new SessionInfo(
                new AccountInfo(
                        session.account().id(),
                        session.account().uid() != null
                                ? session.account().uid().toString()
                                : null,
                        session.account().email(),
                        session.account().firstName(),
                        session.account().lastName()),
                session.stationUid() != null ? session.stationUid().toString() : null,
                memberInfo,
                roleNames,
                session.userType(),
                session.instanceUserType(),
                managedInfos,
                groups,
                tags,
                roleIds,
                groupIds,
                tagIds,
                profileComplete,
                disabledModules,
                themeInfo,
                currentStation != null ? currentStation.publicKbMode() : null,
                currentStation != null ? currentStation.setupCompletedAt() : null));
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
                    UUID stationUid = station != null ? station.uid() : null;
                    return new StationMembership(m.id(), stationUid, stationName);
                })
                .toList();
        ctx.json(result);
    }

    private void getCrossStationDashboard(Context ctx) {
        UserSession session = UserSession.from(ctx);
        List<StationMember> memberships = memberService.findByAccount(session.accountId());

        var stationSummaries = new ArrayList<CrossStationSummary>();
        var allNotifications = new ArrayList<CrossStationNotification>();

        for (StationMember member : memberships) {
            if (member.former()) continue;
            var station = stationService.findById(member.stationId()).orElse(null);
            if (station == null) continue;

            int notificationCount = notificationService.countUnacknowledged(member.id());

            var permissions = accessManager.resolveExpandedMemberPermissions(member);
            var roleNames = permissions.stream().map(Enum::name).toList();
            int requirementCount = requirementsService.countPending(member.id(), member.stationId(), roleNames);

            stationSummaries.add(
                    new CrossStationSummary(station.uid(), station.name(), notificationCount, requirementCount));

            for (Notification n : notificationService.findUnacknowledged(member.id())) {
                allNotifications.add(new CrossStationNotification(
                        station.uid(),
                        station.name(),
                        n.id(),
                        n.type().name(),
                        n.type().localeKey(),
                        n.data().paramsAsMap(),
                        n.data().link() != null
                                ? new CrossStationNotificationLink(
                                        n.data().link().route(), n.data().link().routeParams())
                                : null,
                        n.createdAt()));
            }
        }

        allNotifications.sort(
                Comparator.comparing(CrossStationNotification::createdAt).reversed());
        var limited = allNotifications.size() > 20 ? allNotifications.subList(0, 20) : allNotifications;

        ctx.json(new CrossStationDashboard(stationSummaries, limited));
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
        String currentHash = currentToken.isEmpty() ? "" : tokenHasher.hash(currentToken);
        List<AccountSession> sessions = authService.findSessionsByAccount(session.accountId());
        List<ActiveSession> result = sessions.stream()
                .map(s -> new ActiveSession(
                        s.id(),
                        s.userAgent(),
                        s.createdAt(),
                        s.lastUsedAt(),
                        s.expiresAt(),
                        s.tokenHash().equals(currentHash),
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
        int id = pathInt(ctx, "id");
        accountRepository.deleteSessionById(id, session.accountId());
        ctx.status(HttpStatus.NO_CONTENT);
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
            var roles = stationMemberRepository.findPermissions(member.id());
            boolean isManager = roles.stream().anyMatch(r -> r.permission() == StationPermission.STATION_ADMINISTRATOR);
            if (isManager) {
                throw new BadRequestResponse(
                        "Cannot delete account while you are a station manager. Transfer or delete the station first.");
            }
        }
        gdprDeletionService.deleteAccount(session.accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Retrieves the avatar for the current session's account. Returns 404 if no avatar is stored.
     */
    private void getAvatar(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.account() == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        var accountUid = accountRepository.resolveUid(session.account().id());
        if (accountUid == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        serveAvatar(ctx, accountUid);
    }

    /**
     * Retrieves the avatar for a specific account by its UUID path parameter. Returns
     * 404 when the caller has no relationship to the target account (no shared station
     * membership, no federation partnership, no admin role).
     */
    private void getAvatarByAccount(Context ctx) {
        UUID accountUid;
        try {
            accountUid = UUID.fromString(ctx.pathParam("accountUid"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        var target = accountRepository.findByUid(accountUid).orElse(null);
        if (target == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        UserSession session = UserSession.from(ctx);
        if (!canSeeAccountAvatar(session, target.id())) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        serveAvatar(ctx, accountUid);
    }

    // -- Response records --

    /**
     * Retrieves the avatar for a specific member by their UUID path parameter. Kept for
     * the transition window while the frontend migrates to the account-keyed endpoint;
     * resolves the underlying account UUID and falls through to the same disk lookup.
     */
    private void getAvatarByMember(Context ctx) {
        UUID stationUid;
        UUID memberUid;
        try {
            stationUid = UUID.fromString(ctx.pathParam("stationUid"));
            memberUid = UUID.fromString(ctx.pathParam("memberUid"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }

        var targetStation = stationRepository.findByUid(stationUid).orElse(null);
        if (targetStation == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        var targetMember =
                stationMemberRepository.findByUid(targetStation.id(), memberUid).orElse(null);
        if (targetMember == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }

        UserSession session = UserSession.from(ctx);
        if (!canSeeMemberAvatar(session, targetStation.id())) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        if (targetMember.accountId() == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        var accountUid = accountRepository.resolveUid(targetMember.accountId());
        if (accountUid == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        serveAvatar(ctx, accountUid);
    }

    /**
     * Returns true when the calling session is allowed to view an avatar belonging
     * to {@code targetStationId}: the caller has a membership at the target station,
     * is an instance administrator, or the caller's currently selected station has
     * an active federation partnership with the target station. All other cases —
     * including a logged-in account with no station memberships — fall through to
     * 404 to avoid leaking whether the target member exists.
     */
    private boolean canSeeMemberAvatar(UserSession session, int targetStationId) {
        if (session.account() == null) return false;
        if (session.account().instanceUserType() == InstanceUserType.ADMINISTRATOR) {
            return true;
        }
        if (stationMemberRepository
                .findByStationAndAccount(targetStationId, session.account().id())
                .isPresent()) {
            return true;
        }
        if (session.stationId() == null) return false;
        UUID targetUid = stationRepository.resolveUid(targetStationId);
        if (targetUid == null) return false;
        return federationRepository
                .findPartnerByStationAndRemoteUid(session.stationId(), targetUid)
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .isPresent();
    }

    /**
     * Returns true when the calling session is allowed to view the avatar of the
     * account with id {@code targetAccountId}. Visibility rules: caller is the owner,
     * caller is an instance administrator, caller shares any station membership with
     * the target, or caller's currently-selected station has an active federation
     * partnership with any station the target is a member of.
     */
    private boolean canSeeAccountAvatar(UserSession session, int targetAccountId) {
        if (session.account() == null) return false;
        if (session.account().id() == targetAccountId) return true;
        if (session.account().instanceUserType() == InstanceUserType.ADMINISTRATOR) {
            return true;
        }
        var targetMemberships = stationMemberRepository.findByAccount(targetAccountId);
        if (targetMemberships.isEmpty()) return false;
        var callerMemberships =
                stationMemberRepository.findByAccount(session.account().id());
        var callerStationIds =
                callerMemberships.stream().map(StationMember::stationId).toList();
        for (var targetMembership : targetMemberships) {
            if (callerStationIds.contains(targetMembership.stationId())) return true;
        }
        if (session.stationId() == null) return false;
        for (var targetMembership : targetMemberships) {
            UUID targetUid = stationRepository.resolveUid(targetMembership.stationId());
            if (targetUid == null) continue;
            var partner = federationRepository
                    .findPartnerByStationAndRemoteUid(session.stationId(), targetUid)
                    .orElse(null);
            if (partner != null && partner.status() == FederationPartner.FederationStatus.ACTIVE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Serves an avatar with appropriate content type and cache headers, given the account UUID
     * the avatar is stored under.
     */
    private void serveAvatar(Context ctx, UUID accountUid) {
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        avatarService
                .read(accountUid, size)
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
        if (session.account() == null) {
            throw new BadRequestResponse("No account in session");
        }
        var file = ctx.uploadedFile("avatar");
        if (file == null) {
            throw new BadRequestResponse("No file uploaded");
        }
        if (!ALLOWED_AVATAR_TYPES.contains(file.contentType())) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        var accountUid = accountRepository.resolveUid(session.account().id());
        if (accountUid == null) {
            throw new BadRequestResponse("Account UUID not found");
        }
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
        UserSession session = UserSession.from(ctx);
        if (session.account() == null) {
            throw new BadRequestResponse("No account in session");
        }
        var accountUid = accountRepository.resolveUid(session.account().id());
        if (accountUid == null) {
            throw new BadRequestResponse("Account UUID not found");
        }
        avatarService.delete(accountUid);
        ctx.status(HttpStatus.NO_CONTENT);
    }

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

    public record CrossStationDashboard(
            List<CrossStationSummary> stations, List<CrossStationNotification> recentNotifications) {}

    public record CrossStationSummary(UUID stationId, String stationName, int notifications, int requirements) {}

    public record CrossStationNotification(
            UUID stationId,
            String stationName,
            int id,
            String type,
            String localeKey,
            Map<String, String> params,
            CrossStationNotificationLink link,
            Instant createdAt) {}

    // -- Avatar --

    public record CrossStationNotificationLink(String route, Map<String, Object> routeParams) {}

    /**
     * Aggregated session information returned to the authenticated user.
     *
     * @param account           the account details
     * @param stationId         the currently selected station, or {@code null} if none
     * @param member            the station membership info, or {@code null} if not a member
     * @param permissions       sorted list of permission names for the current station
     * @param managedMembers    list of members managed by this account
     * @param groups            groups the current member belongs to
     * @param profileComplete   whether all required profile fields are filled
     * @param disabledModules   set of modules disabled for the current station
     * @param setupCompletedAt  timestamp at which the station setup wizard was finished, or
     *                          {@code null} while the wizard still applies. Drives the first-login
     *                          redirect to {@code /station/setup} for administrators
     */
    public record SessionInfo(
            AccountInfo account,
            String stationId,
            MemberInfo member,
            List<String> permissions,
            StationUserType userType,
            InstanceUserType instanceUserType,
            List<ManagedMemberInfo> managedMembers,
            List<MemberGroup> groups,
            List<UserTag> tags,
            List<Integer> roleIds,
            List<Integer> groupIds,
            List<Integer> tagIds,
            boolean profileComplete,
            Set<StationModule> disabledModules,
            ThemeInfo theme,
            PublicKbMode publicKbMode,
            Instant setupCompletedAt) {}

    public record ThemeInfo(
            String instanceDefaultTheme,
            ThemeFeel instanceDefaultFeel,
            boolean instanceLockFeel,
            String defaultTheme,
            ThemeFeel defaultFeel,
            boolean allowUserTheme,
            boolean allowUserFeel,
            String customThemeColors,
            String userTheme,
            String userDarkMode,
            String userFeel) {}

    /**
     * Summary of a member managed by the current account.
     *
     * @param id        the member identifier
     * @param stationId the station identifier
     * @param accountId the member's account identifier, or 0 if none
     * @param name      the member's display name
     * @param email     the member's email, or empty string if unavailable
     */
    public record ManagedMemberInfo(int id, UUID stationId, UUID uid, int accountId, String name, String email) {}

    /**
     * Account information included in the session response.
     *
     * @param id        the account identifier
     * @param email     the email address
     * @param firstName the first name
     * @param lastName  the last name
     */
    public record AccountInfo(int id, String uid, String email, String firstName, String lastName) {}

    /**
     * Minimal member information for the current session.
     *
     * @param id        the member identifier
     * @param stationId the station identifier
     * @param accountId the account identifier
     */
    public record MemberInfo(int id, String stationId, int accountId, UUID uid) {}

    /**
     * A station membership entry listing which stations the user belongs to.
     *
     * @param memberId    the member identifier
     * @param stationId   the station identifier
     * @param stationName the station name
     */
    public record StationMembership(int memberId, UUID stationId, String stationName) {}

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
