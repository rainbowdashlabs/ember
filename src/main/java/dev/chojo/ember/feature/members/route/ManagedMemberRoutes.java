/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.legal.service.GdprExportService;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.members.service.ProfileFieldService.FieldValueEntry;
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

@Singleton
public class ManagedMemberRoutes implements Routes {
    private static final Set<Roles> TEAM_ROLES = Set.of(
            Roles.TEAM,
            Roles.MANAGER,
            Roles.ADMIN,
            Roles.ATTENDENCE_MANAGEMENT,
            Roles.INVENTORY_MANAGEMENT,
            Roles.EVENT_MANAGEMENT,
            Roles.MEMBER_MANAGEMENT);

    private final StationMemberService memberService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final ProfileFieldService profileFieldService;
    private final InventoryService inventoryService;
    private final InventoryCheckService checkService;
    private final GdprExportService gdprExportService;

    @Inject
    public ManagedMemberRoutes(
            StationMemberService memberService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            ProfileFieldService profileFieldService,
            InventoryService inventoryService,
            InventoryCheckService checkService,
            GdprExportService gdprExportService) {
        this.memberService = memberService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.profileFieldService = profileFieldService;
        this.inventoryService = inventoryService;
        this.checkService = checkService;
        this.gdprExportService = gdprExportService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/managed-members", this::listManaged, Roles.GUARDIAN);
        routes.get(prefix + "/managed-members/{memberId}/profile", this::getProfile, Roles.GUARDIAN);
        routes.put(prefix + "/managed-members/{memberId}/profile", this::setProfile, Roles.GUARDIAN);
        routes.get(prefix + "/managed-members/{memberId}/inventory-items", this::getMemberInventory, Roles.GUARDIAN);
        routes.get(
                prefix + "/managed-members/{memberId}/inventory-requirements",
                this::getMemberRequirements,
                Roles.GUARDIAN);
        routes.get(prefix + "/managed-members/{memberId}/gdpr-export", this::gdprExport, Roles.GUARDIAN);
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
        var roles = stationMemberRepository.findRoles(memberId).stream()
                .map(Role::role)
                .toList();
        var scopes = new HashSet<ProfileFieldScope>();
        if (roles.contains(Roles.MEMBER)) scopes.add(ProfileFieldScope.MEMBER);
        if (roles.contains(Roles.GUARDIAN)) scopes.add(ProfileFieldScope.GUARDIAN);
        if (roles.stream().anyMatch(TEAM_ROLES::contains)) scopes.add(ProfileFieldScope.TEAM);
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
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        assertManages(session, memberId);
        var member = stationMemberRepository.findById(memberId).orElseThrow(NotFoundResponse::new);
        var fields = applicableFields(member.stationId(), memberId);
        var values = profileFieldService.findValues(memberId);
        var fieldIds = fields.stream().map(ProfileField::id).collect(Collectors.toSet());
        var filteredValues =
                values.stream().filter(v -> fieldIds.contains(v.fieldId())).toList();
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
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
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
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
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
                            item.lostAt());
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
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        assertManages(session, memberId);
        var member = stationMemberRepository.findById(memberId).orElseThrow(NotFoundResponse::new);
        var required = checkService.getRequiredItems(member.stationId(), memberId);
        ctx.json(required.stream()
                .map(r -> new MemberRequirement(r.inventoryId(), r.inventoryName(), r.requiredQuantity()))
                .toList());
    }

    public record MemberInventoryItem(
            int id,
            int inventoryId,
            String name,
            String internalId,
            String inventoryName,
            Integer sizeId,
            String sizeName,
            Instant lostAt) {}

    public record MemberRequirement(int inventoryId, String inventoryName, int requiredQuantity) {}

    public record ManagedMember(int id, int stationId, int accountId, String name, String email) {}

    public record MemberProfile(List<ProfileField> fields, List<ProfileFieldValue> values) {}

    @OpenApiName("ManagedMemberSetValuesRequest")
    public record SetValuesRequest(List<ValueEntry> values) {}

    public record ValueEntry(int fieldId, String value) {}

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/gdpr-export",
            methods = HttpMethod.GET,
            summary = "Export all personal data for a managed member (GDPR/DSGVO)",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void gdprExport(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        assertManages(session, memberId);
        var data = gdprExportService.exportMemberData(memberId);
        ctx.contentType("application/json");
        ctx.header("Content-Disposition", "attachment; filename=\"gdpr-export-member-" + memberId + ".json\"");
        ctx.json(data);
    }
}
