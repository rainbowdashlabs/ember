/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.legal.service.GdprExportService;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.ManagedAccessService;
import dev.chojo.ember.feature.members.service.ManagedAccessService.ManagedAccess;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for guardians/managers to view and manage members they are responsible for,
 * including profile fields, inventory items, and GDPR data export.
 */
@Singleton
public class ManagedMemberRoutes implements Routes {
    private static final Set<StationPermission> TEAM_PERMISSIONS = Set.of(
            StationPermission.STATION_ADMINISTRATOR,
            StationPermission.ATTENDANCE_MANAGER,
            StationPermission.INVENTORY_MANAGER,
            StationPermission.EVENT_MANAGER,
            StationPermission.MEMBER_MANAGER);

    private final StationMemberService memberService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final ProfileFieldService profileFieldService;
    private final InventoryService inventoryService;
    private final InventoryCheckService checkService;
    private final GdprExportService gdprExportService;
    private final AccessManager accessManager;
    private final ManagedAccessService accessService;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public ManagedMemberRoutes(
            StationMemberService memberService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            ProfileFieldService profileFieldService,
            InventoryService inventoryService,
            InventoryCheckService checkService,
            GdprExportService gdprExportService,
            AccessManager accessManager,
            ManagedAccessService accessService,
            MemberIdentityFactory memberIdentityFactory) {
        this.memberIdentityFactory = memberIdentityFactory;
        this.accessService = accessService;
        this.memberService = memberService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.profileFieldService = profileFieldService;
        this.inventoryService = inventoryService;
        this.checkService = checkService;
        this.gdprExportService = gdprExportService;
        this.accessManager = accessManager;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/managed-members", this::listManaged, StationPermission.MEMBER_GUARDIAN);
        routes.get(prefix + "/managed-members/{memberId}/profile", this::getProfile, StationPermission.MEMBER_GUARDIAN);
        routes.put(prefix + "/managed-members/{memberId}/profile", this::setProfile, StationPermission.MEMBER_GUARDIAN);
        routes.get(prefix + "/managed-members/{memberId}/access", this::getAccess, StationPermission.MEMBER_GUARDIAN);
        routes.put(prefix + "/managed-members/{memberId}/email", this::setEmail, StationPermission.MEMBER_GUARDIAN);
        routes.put(
                prefix + "/managed-members/{memberId}/username", this::setUsername, StationPermission.MEMBER_GUARDIAN);
        routes.put(
                prefix + "/managed-members/{memberId}/password", this::setPassword, StationPermission.MEMBER_GUARDIAN);
        routes.put(prefix + "/managed-members/{memberId}/login", this::setLogin, StationPermission.MEMBER_GUARDIAN);
        routes.get(
                prefix + "/managed-members/{memberId}/inventory-items",
                this::getMemberInventory,
                StationPermission.MEMBER_GUARDIAN);
        routes.get(
                prefix + "/managed-members/{memberId}/inventory-requirements",
                this::getMemberRequirements,
                StationPermission.MEMBER_GUARDIAN);
        routes.get(
                prefix + "/managed-members/{memberId}/gdpr-export",
                this::gdprExport,
                StationPermission.MEMBER_GUARDIAN);
    }

    private void assertManages(UserSession session, int memberId) {
        var managed = memberService.findManaged(session.member().id());
        boolean manages = managed.stream().anyMatch(m -> m.id() == memberId);
        if (!manages) {
            throw new ForbiddenResponse("You do not manage this member");
        }
    }

    @OpenApi(
            path = "/api/v1/managed-members",
            methods = HttpMethod.GET,
            summary = "List members managed by the current user",
            tags = {"Managed Members"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedMember[].class)))
    private void listManaged(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var managed = memberService.findManaged(session.member().id());
        var result = managed.stream().map(this::toMemberWithName).toList();
        ctx.json(result);
    }

    private Set<ProfileFieldScope> applicableScopes(int memberId) {
        var permissions = accessManager.resolveExpandedMemberPermissions(memberId);
        var scopes = new HashSet<ProfileFieldScope>();
        if (permissions.contains(StationPermission.USER)) scopes.add(ProfileFieldScope.MEMBER);
        if (permissions.contains(StationPermission.MEMBER_GUARDIAN)) scopes.add(ProfileFieldScope.GUARDIAN);
        if (permissions.stream().anyMatch(TEAM_PERMISSIONS::contains)) scopes.add(ProfileFieldScope.TEAM);
        if (permissions.contains(StationPermission.STATION_ADMINISTRATOR)) scopes.add(ProfileFieldScope.MANAGER);
        return scopes;
    }

    private List<ProfileField> applicableFields(int stationId, int memberId) {
        var scopes = applicableScopes(memberId);
        return profileFieldService.findByStation(stationId).stream()
                .filter(f -> f.scope() != null && scopes.contains(f.scope()))
                .filter(f -> f.scope() != ProfileFieldScope.GROUP)
                .toList();
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/profile",
            methods = HttpMethod.GET,
            summary = "Get the profile of a managed member",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberProfile.class)),
                @OpenApiResponse(status = "404")
            })
    private void getProfile(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        assertManages(session, memberId);
        var member = stationMemberRepository.findById(memberId).orElseThrow(NotFoundResponse::new);
        var fields = applicableFields(member.stationId(), memberId);
        var values = profileFieldService.findValues(memberId);
        var fieldIds = fields.stream().map(ProfileField::id).collect(Collectors.toSet());
        // A guardian answers for the station's own questions only. A cluster's questions are asked of the
        // member, and the two id spaces are separate, so origin decides before the id does.
        var filteredValues = values.stream()
                .filter(v -> v.origin() == FieldOrigin.STATION)
                .filter(v -> fieldIds.contains(v.fieldId()))
                .map(v -> new ProfileFieldValue(memberId, v.fieldId(), v.value()))
                .toList();
        ctx.json(new MemberProfile(fields, filteredValues));
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/profile",
            methods = HttpMethod.PUT,
            summary = "Set profile values for a managed member",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetValuesRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void setProfile(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        assertManages(session, memberId);
        var member = stationMemberRepository.findById(memberId).orElseThrow(NotFoundResponse::new);
        var allowedFieldIds = applicableFields(member.stationId(), memberId).stream()
                .map(ProfileField::id)
                .collect(Collectors.toSet());
        var request = ctx.bodyAsClass(SetValuesRequest.class);
        var entries = request.values().stream()
                .filter(e -> allowedFieldIds.contains(e.fieldId()))
                .map(e -> new FieldValueEntry(e.fieldId(), e.value()))
                .toList();
        ctx.json(profileFieldService.setValues(
                memberId, entries, session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/access",
            methods = HttpMethod.GET,
            summary = "Read the access of a managed member",
            description = "The address the account is reached at and whether the member may sign in.",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedAccess.class)))
    private void getAccess(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(accessService.get(session.member().id(), pathInt(ctx, "memberId")));
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/email",
            methods = HttpMethod.PUT,
            summary = "Set the email address of a managed member",
            description = "Takes effect at once and ends the open sessions of that member. Where there was an "
                    + "address before, the old and the new one are told.",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetEmailRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedAccess.class)))
    private void setEmail(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SetEmailRequest.class);
        ctx.json(accessService.setEmail(session.member().id(), pathInt(ctx, "memberId"), request.email()));
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/username",
            methods = HttpMethod.PUT,
            summary = "Set the name a managed member signs in with",
            description = "A member with a name of their own needs no address to sign in: everything Ember "
                    + "would write to them goes to their guardians instead. An empty name clears it.",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetUsernameRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedAccess.class)))
    private void setUsername(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SetUsernameRequest.class);
        ctx.json(accessService.setUsername(session.member().id(), pathInt(ctx, "memberId"), request.username()));
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/password",
            methods = HttpMethod.PUT,
            summary = "Set the password of a managed member",
            description = "Only for a member with no address of their own, whose invitation would land in the "
                    + "guardian's postbox anyway. The usual password rules apply, the member's open sessions end, "
                    + "and whoever looks after them is told.",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetPasswordRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedAccess.class)))
    private void setPassword(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SetPasswordRequest.class);
        ctx.json(accessService.setPassword(session.member().id(), pathInt(ctx, "memberId"), request.password()));
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/login",
            methods = HttpMethod.PUT,
            summary = "Allow or refuse signing in for a managed member",
            description = "Allowing it sends the invitation to set a password when the account has none. "
                    + "Refusing it ends the sessions that are open.",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetLoginRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedAccess.class)))
    private void setLogin(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SetLoginRequest.class);
        ctx.json(accessService.setLogin(session.member().id(), pathInt(ctx, "memberId"), request.enabled()));
    }

    /**
     * @param email the address the managed member's account should carry
     */
    public record SetEmailRequest(String email) {}

    /**
     * @param username the name the managed member signs in with, or empty to clear it
     */
    public record SetUsernameRequest(String username) {}

    /**
     * @param password the password the managed member signs in with
     */
    public record SetPasswordRequest(String password) {}

    /**
     * @param enabled whether the managed member may sign in
     */
    public record SetLoginRequest(boolean enabled) {}

    private ManagedMember toMemberWithName(StationMember m) {
        Account account = accountRepository.findById(m.accountId()).orElse(null);
        String name = account != null ? account.fullName() : "";
        String email = account != null ? account.email() : "";
        return new ManagedMember(m.id(), m.stationId(), m.accountId(), name, email);
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/inventory-items",
            methods = HttpMethod.GET,
            summary = "Get inventory items for a managed member",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberInventoryItem[].class)))
    private void getMemberInventory(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        assertManages(session, memberId);
        var items = inventoryService.findItemsByMember(memberId);
        ctx.json(items.stream()
                .map(item -> {
                    String inventoryName = inventoryService
                            .findById(item.inventoryId())
                            .map(Inventory::name)
                            .orElse("");
                    String sizeName = null;
                    if (item.sizeId() != null) {
                        sizeName = inventoryService.findSizes(item.inventoryId()).stream()
                                .filter(s -> s.id() == item.sizeId())
                                .map(InventorySize::label)
                                .findFirst()
                                .orElse(null);
                    }
                    return new MemberInventoryItem(
                            item.id(),
                            item.inventoryId(),
                            item.name(),
                            item.internalId(),
                            inventoryName,
                            item.sizeId(),
                            sizeName,
                            item.lostAt(),
                            item.lostNote(),
                            item.lostNoteBy() == null ? null : memberIdentityFactory.fromMemberId(item.lostNoteBy()));
                })
                .toList());
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/inventory-requirements",
            methods = HttpMethod.GET,
            summary = "Get inventory requirements for a managed member",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void getMemberRequirements(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        assertManages(session, memberId);
        var member = stationMemberRepository.findById(memberId).orElseThrow(NotFoundResponse::new);
        var required = checkService.getRequiredItems(member.stationId(), memberId);
        ctx.json(required.stream()
                .map(r -> new MemberRequirement(r.inventoryId(), r.inventoryName(), r.requiredQuantity()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/gdpr-export",
            methods = HttpMethod.GET,
            summary = "Export all personal data for a managed member (GDPR/DSGVO)",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void gdprExport(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        assertManages(session, memberId);
        var data = gdprExportService.exportMemberData(memberId);
        ctx.contentType("application/json");
        ctx.header("Content-Disposition", "attachment; filename=\"gdpr-export-member-" + memberId + ".json\"");
        ctx.json(data);
    }

    public record MemberInventoryItem(
            int id,
            int inventoryId,
            String name,
            String internalId,
            String inventoryName,
            Integer sizeId,
            String sizeName,
            Instant lostAt,
            /** What was written when it was reported missing, which a guardian may have written themselves. */
            String lostNote,
            MemberIdentity lostNoteBy) {}

    public record MemberRequirement(int inventoryId, String inventoryName, int requiredQuantity) {}

    public record ManagedMember(int id, int stationId, int accountId, String name, String email) {}

    public record MemberProfile(List<ProfileField> fields, List<ProfileFieldValue> values) {}

    @OpenApiName("ManagedMemberSetValuesRequest")
    public record SetValuesRequest(List<ValueEntry> values) {}

    public record ValueEntry(int fieldId, String value) {}
}
