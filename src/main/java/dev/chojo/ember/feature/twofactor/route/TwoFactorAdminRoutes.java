/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorPolicy;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAdminService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorPolicyService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Admin + station-admin policy management for 2FA mandates. Policy writes are step-up gated
 * (instance-wide = {@link StepUpCategory#INSTANCE_CONFIG}, station = {@link StepUpCategory#ACCOUNT_SECURITY}).
 * The audit-log viewer is instance-admin only.
 */
@Singleton
public class TwoFactorAdminRoutes implements Routes {
    private final TwoFactorPolicyService policyService;
    private final TwoFactorService twoFactorService;
    private final TwoFactorAdminService adminService;

    @Inject
    public TwoFactorAdminRoutes(
            TwoFactorPolicyService policyService,
            TwoFactorService twoFactorService,
            TwoFactorAdminService adminService) {
        this.policyService = policyService;
        this.twoFactorService = twoFactorService;
        this.adminService = adminService;
    }

    // -- Instance scope --

    private static short clampGraceDays(@Nullable Integer requested) {
        if (requested == null) return 7;
        int v = requested;
        if (v < 0) v = 0;
        if (v > 7) v = 7;
        return (short) v;
    }

    private static int requireStation(Context ctx) {
        UserSession session = UserSession.from(ctx);
        return session.stationIdOpt().orElseThrow(Refusal.NO_STATION_CHOSEN_ON_POLICY::raise);
    }

    // -- Station scope --

    private static @Nullable Integer actorMemberId(Context ctx) {
        UserSession session = UserSession.from(ctx);
        return session.memberOpt().map(StationMember::id).orElse(null);
    }

    private static TwoFactorPolicyEntry toEntry(TwoFactorPolicy p) {
        return new TwoFactorPolicyEntry(
                p.id(),
                p.scope(),
                p.stationId(),
                p.userType(),
                p.required(),
                p.graceDays(),
                p.createdBy(),
                p.createdAt());
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Instance-admin: policies across every station.
        routes.get(prefix + "/admin/2fa/policies", this::listInstancePolicies, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/2fa/policies",
                this::upsertInstancePolicy,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.delete(
                prefix + "/admin/2fa/policies/{id}",
                this::deleteInstancePolicy,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/2fa/audit", this::listAudit, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/accounts/search", this::searchAccounts, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/accounts/{id}/2fa/reset",
                this::resetByInstanceAdmin,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);

        // Station-admin: policies for the caller's currently-selected station only.
        routes.get(
                prefix + "/station/2fa/policies", this::listStationPolicies, StationPermission.STATION_ADMINISTRATOR);
        routes.put(
                prefix + "/station/2fa/policies",
                this::upsertStationPolicy,
                StationPermission.STATION_ADMINISTRATOR,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.delete(
                prefix + "/station/2fa/policies/{id}",
                this::deleteStationPolicy,
                StationPermission.STATION_ADMINISTRATOR,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.get(prefix + "/station/2fa/members", this::listMemberStatus, StationPermission.STATION_ADMINISTRATOR);
        routes.get(
                prefix + "/station/2fa/user-types",
                this::listAssignableUserTypes,
                StationPermission.STATION_ADMINISTRATOR);
        routes.post(
                prefix + "/station/accounts/{id}/2fa/reset",
                this::resetByStationAdmin,
                StationPermission.STATION_ADMINISTRATOR,
                StepUpCategory.ACCOUNT_SECURITY);
    }

    @OpenApi(
            path = "/api/v1/admin/2fa/policies",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PoliciesResponse.class)))
    private void listInstancePolicies(Context ctx) {
        var policies = policyService.listInstancePolicies();
        ctx.json(new PoliciesResponse(
                policies.stream().map(TwoFactorAdminRoutes::toEntry).toList()));
    }

    @OpenApi(
            path = "/api/v1/admin/2fa/policies",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpsertPolicyRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TwoFactorPolicyEntry.class)))
    private void upsertInstancePolicy(Context ctx) {
        var request = ctx.bodyAsClass(UpsertPolicyRequest.class);
        TwoFactorPolicy saved = policyService.setInstancePolicy(
                request.userType(), request.required(), clampGraceDays(request.graceDays()), actorMemberId(ctx));
        ctx.json(toEntry(saved));
    }

    @OpenApi(
            path = "/api/v1/admin/2fa/policies/{id}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void deleteInstancePolicy(Context ctx) {
        int id = pathInt(ctx, "id");
        if (!policyService.deletePolicy(id)) {
            throw Refusal.POLICY_NOT_HERE.raise();
        }
        ctx.json(new MessageResponse("Policy removed"));
    }

    @OpenApi(
            path = "/api/v1/station/2fa/policies",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PoliciesResponse.class)))
    private void listStationPolicies(Context ctx) {
        int stationId = requireStation(ctx);
        var policies = policyService.listStationPolicies(stationId);
        ctx.json(new PoliciesResponse(
                policies.stream().map(TwoFactorAdminRoutes::toEntry).toList()));
    }

    @OpenApi(
            path = "/api/v1/station/2fa/policies",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpsertPolicyRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TwoFactorPolicyEntry.class)))
    private void upsertStationPolicy(Context ctx) {
        int stationId = requireStation(ctx);
        var request = ctx.bodyAsClass(UpsertPolicyRequest.class);
        TwoFactorPolicy saved = policyService.setStationPolicy(
                stationId,
                request.userType(),
                request.required(),
                clampGraceDays(request.graceDays()),
                actorMemberId(ctx));
        ctx.json(toEntry(saved));
    }

    /**
     * Removes a rule from the caller's own station.
     *
     * <p>The rule is looked for in that station's own list before it is removed, so a station
     * administrator cannot reach a rule belonging to a station they do not administer by naming its
     * number.
     */
    @OpenApi(
            path = "/api/v1/station/2fa/policies/{id}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void deleteStationPolicy(Context ctx) {
        int stationId = requireStation(ctx);
        int id = pathInt(ctx, "id");
        var policies = policyService.listStationPolicies(stationId);
        if (policies.stream().noneMatch(p -> p.id() == id)) {
            throw Refusal.POLICY_NOT_HERE_ON_STATION_DELETE.raise();
        }
        if (!policyService.deletePolicy(id)) {
            throw Refusal.POLICY_NOT_HERE_ON_STATION_DELETE.raise();
        }
        ctx.json(new MessageResponse("Policy removed"));
    }

    @OpenApi(
            path = "/api/v1/station/2fa/members",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberStatusResponse.class)))
    private void listMemberStatus(Context ctx) {
        int stationId = requireStation(ctx);
        ctx.json(new MemberStatusResponse(policyService.listStationMemberStatus(stationId)));
    }

    @OpenApi(
            path = "/api/v1/station/2fa/user-types",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = UserTypesResponse.class)))
    private void listAssignableUserTypes(Context ctx) {
        ctx.json(new UserTypesResponse(policyService.assignableUserTypes()));
    }

    @OpenApi(
            path = "/api/v1/admin/accounts/{id}/2fa/reset",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void resetByInstanceAdmin(Context ctx) {
        int targetId = pathInt(ctx, "id");
        UserSession actor = UserSession.from(ctx);
        if (!twoFactorService.resetAccount2FA(
                targetId, actor.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"))) {
            throw Refusal.SECOND_FACTOR_NOT_RESET.raise();
        }
        ctx.json(new MessageResponse("2FA reset"));
    }

    /**
     * Clears a member's second factor on behalf of the station that looks after them.
     */
    @OpenApi(
            path = "/api/v1/station/accounts/{id}/2fa/reset",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void resetByStationAdmin(Context ctx) {
        int targetId = pathInt(ctx, "id");
        UserSession actor = UserSession.from(ctx);
        int stationId = actor.stationIdOpt().orElseThrow(Refusal.NO_STATION_CHOSEN_ON_RESET::raise);
        adminService.resetForStation(
                stationId, targetId, actor.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        ctx.json(new MessageResponse("2FA reset"));
    }

    @OpenApi(
            path = "/api/v1/admin/accounts/search",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = TwoFactorAdminService.AccountSearchResult[].class)))
    private void searchAccounts(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        ctx.json(adminService.searchAccounts(ctx.queryParam("q"), ctx.queryParam("uid"), limit));
    }

    @OpenApi(
            path = "/api/v1/admin/2fa/audit",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = TwoFactorAdminService.AuditResponse.class)))
    private void listAudit(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        Integer accountId = ctx.queryParam("accountId") == null
                ? null
                : ctx.queryParamAsClass("accountId", Integer.class).get();
        ctx.json(adminService.audit(accountId, limit, offset));
    }

    /**
     * One second-factor rule.
     *
     * @param scope     whether the rule holds for the whole instance or for one station
     * @param stationId the station a station rule belongs to, absent on an instance rule
     * @param userType  the member type the rule is for, absent where it is for every type
     * @param createdBy the member who set it, absent where nobody did
     */
    public record TwoFactorPolicyEntry(
            int id,
            TwoFactorPolicy.PolicyScope scope,
            @Nullable Integer stationId,
            @Nullable StationUserType userType,
            boolean required,
            short graceDays,
            @Nullable Integer createdBy,
            Instant createdAt) {}

    public record PoliciesResponse(List<TwoFactorPolicyEntry> policies) {}

    /**
     * @param userType  the member type the rule is for, or {@code null} for every type
     * @param graceDays the days before the rule is enforced, or {@code null} for the longest grace
     */
    public record UpsertPolicyRequest(
            @Nullable StationUserType userType,
            boolean required,
            @Nullable Integer graceDays) {}

    public record MemberStatusResponse(List<TwoFactorPolicyService.MemberStatus> members) {}

    public record UserTypesResponse(List<StationUserType> userTypes) {}
}
