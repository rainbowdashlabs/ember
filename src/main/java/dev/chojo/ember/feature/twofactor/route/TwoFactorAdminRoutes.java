/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.repository.AccountRepository.PickerAccount;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorAuditEntry;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorPolicy;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.service.TwoFactorPolicyService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Admin + station-admin policy management for 2FA mandates. Policy writes are step-up gated
 * (instance-wide = {@link StepUpCategory#INSTANCE_CONFIG}, station = {@link StepUpCategory#ACCOUNT_SECURITY}).
 * The audit-log viewer is instance-admin only.
 */
@Singleton
public class TwoFactorAdminRoutes implements Routes {
    private final TwoFactorPolicyService policyService;
    private final TwoFactorRepository repository;
    private final TwoFactorService twoFactorService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public TwoFactorAdminRoutes(
            TwoFactorPolicyService policyService,
            TwoFactorRepository repository,
            TwoFactorService twoFactorService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository) {
        this.policyService = policyService;
        this.repository = repository;
        this.twoFactorService = twoFactorService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    private static AccountSearchResult toAccountDto(PickerAccount a) {
        return new AccountSearchResult(a.id(), a.uid(), a.displayName(), a.firstName(), a.lastName(), a.email());
    }

    // -- Instance scope --

    private static StationUserType parseUserType(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return StationUserType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Unknown user type");
        }
    }

    private static short clampGraceDays(Integer requested) {
        if (requested == null) return 7;
        int v = requested;
        if (v < 0) v = 0;
        if (v > 7) v = 7;
        return (short) v;
    }

    private static int requireStation(Context ctx) {
        UserSession session = UserSession.from(ctx);
        return session.stationIdOpt()
                .orElseThrow(() -> new ForbiddenResponse("A station must be selected to manage station 2FA policy"));
    }

    // -- Station scope --

    private static Integer actorMemberId(Context ctx) {
        UserSession session = UserSession.from(ctx);
        return session.memberOpt().map(StationMember::id).orElse(null);
    }

    private static PolicyDto toDto(TwoFactorPolicy p) {
        return new PolicyDto(
                p.id(),
                p.scope().name(),
                p.stationId(),
                p.userType() == null ? null : p.userType().name(),
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

    private void listInstancePolicies(Context ctx) {
        var policies = policyService.listInstancePolicies();
        ctx.json(new PoliciesResponse(
                policies.stream().map(TwoFactorAdminRoutes::toDto).toList()));
    }

    private void upsertInstancePolicy(Context ctx) {
        var request = ctx.bodyAsClass(UpsertPolicyRequest.class);
        StationUserType userType = parseUserType(request.userType());
        TwoFactorPolicy saved = policyService.setInstancePolicy(
                userType, request.required(), clampGraceDays(request.graceDays()), actorMemberId(ctx));
        ctx.json(toDto(saved));
    }

    // -- Admin reset --

    private void deleteInstancePolicy(Context ctx) {
        int id = pathInt(ctx, "id");
        if (!policyService.deletePolicy(id)) {
            throw new BadRequestResponse("Policy not found");
        }
        ctx.json(new MessageResponse("Policy removed"));
    }

    private void listStationPolicies(Context ctx) {
        int stationId = requireStation(ctx);
        var policies = policyService.listStationPolicies(stationId);
        ctx.json(new PoliciesResponse(
                policies.stream().map(TwoFactorAdminRoutes::toDto).toList()));
    }

    // -- Account picker (instance-admin) --

    private void upsertStationPolicy(Context ctx) {
        int stationId = requireStation(ctx);
        var request = ctx.bodyAsClass(UpsertPolicyRequest.class);
        StationUserType userType = parseUserType(request.userType());
        TwoFactorPolicy saved = policyService.setStationPolicy(
                stationId, userType, request.required(), clampGraceDays(request.graceDays()), actorMemberId(ctx));
        ctx.json(toDto(saved));
    }

    private void deleteStationPolicy(Context ctx) {
        int stationId = requireStation(ctx);
        int id = pathInt(ctx, "id");
        // Defend against a station admin deleting policies that don't belong to them.
        var policies = policyService.listStationPolicies(stationId);
        if (policies.stream().noneMatch(p -> p.id() == id)) {
            throw new BadRequestResponse("Policy not found");
        }
        if (!policyService.deletePolicy(id)) {
            throw new BadRequestResponse("Policy not found");
        }
        ctx.json(new MessageResponse("Policy removed"));
    }

    // -- Audit log --

    private void listMemberStatus(Context ctx) {
        int stationId = requireStation(ctx);
        var members = policyService.listStationMemberStatus(stationId).stream()
                .map(m -> new MemberStatusDto(
                        m.memberId(),
                        m.accountId(),
                        m.firstName(),
                        m.lastName(),
                        m.email(),
                        m.userType().name(),
                        m.enrolled(),
                        m.mandated()))
                .toList();
        ctx.json(new MemberStatusResponse(members));
    }

    // -- Helpers --

    private void listAssignableUserTypes(Context ctx) {
        ctx.json(new UserTypesResponse(
                policyService.assignableUserTypes().stream().map(Enum::name).toList()));
    }

    private void resetByInstanceAdmin(Context ctx) {
        int targetId = pathInt(ctx, "id");
        UserSession actor = UserSession.from(ctx);
        if (!twoFactorService.resetAccount2FA(
                targetId, actor.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"))) {
            throw new NotFoundResponse();
        }
        ctx.json(new MessageResponse("2FA reset"));
    }

    private void resetByStationAdmin(Context ctx) {
        int targetId = pathInt(ctx, "id");
        UserSession actor = UserSession.from(ctx);
        int stationId = actor.stationIdOpt()
                .orElseThrow(() -> new ForbiddenResponse("A station must be selected to reset 2FA on a member"));

        // The target must be a member of the caller's station and must not be an instance admin -
        // station admins can only act on people they actually manage.
        var membership = stationMemberRepository.findByStationAndAccount(stationId, targetId);
        if (membership.isEmpty()) {
            throw new NotFoundResponse();
        }
        var targetAccount = accountRepository.findById(targetId);
        if (targetAccount.isEmpty()) {
            throw new NotFoundResponse();
        }
        if (targetAccount.get().instanceUserType() == InstanceUserType.ADMINISTRATOR) {
            throw new ForbiddenResponse("Instance administrators can only be reset by another instance administrator");
        }

        if (!twoFactorService.resetAccount2FA(
                targetId, actor.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"))) {
            throw new NotFoundResponse();
        }
        ctx.json(new MessageResponse("2FA reset"));
    }

    private void searchAccounts(Context ctx) {
        String q = ctx.queryParam("q");
        String uidParam = ctx.queryParam("uid");
        int requested = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        int limit = Math.clamp(requested, 1, 50);
        if (uidParam != null && !uidParam.isBlank()) {
            UUID lookup;
            try {
                lookup = UUID.fromString(uidParam);
            } catch (IllegalArgumentException e) {
                ctx.json(List.of());
                return;
            }
            var result = accountRepository
                    .findPickerByUid(lookup)
                    .map(TwoFactorAdminRoutes::toAccountDto)
                    .map(List::of)
                    .orElseGet(List::of);
            ctx.json(result);
            return;
        }
        var results = accountRepository.searchForPicker(q, limit).stream()
                .map(TwoFactorAdminRoutes::toAccountDto)
                .toList();
        ctx.json(results);
    }

    private void listAudit(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        String accountIdParam = ctx.queryParam("accountId");
        Integer accountId = accountIdParam == null ? null : Integer.parseInt(accountIdParam);
        if (limit <= 0 || limit > 200) limit = 50;
        if (offset < 0) offset = 0;
        List<TwoFactorAuditEntry> entries;
        if (accountId != null) {
            entries = repository.findAuditLog(accountId, limit, offset);
        } else {
            entries = repository.findRecentAudit(limit, offset);
        }
        ctx.json(new AuditResponse(entries.stream()
                .map(e -> new AuditEntryDto(
                        e.id(),
                        e.accountId(),
                        e.actorId(),
                        e.event().name(),
                        e.factorKind() == null ? null : e.factorKind().name(),
                        e.userAgent(),
                        e.country(),
                        e.createdAt()))
                .toList()));
    }

    // -- Request / response records --

    public record PolicyDto(
            int id,
            String scope,
            Integer stationId,
            String userType,
            boolean required,
            short graceDays,
            Integer createdBy,
            Instant createdAt) {}

    public record PoliciesResponse(List<PolicyDto> policies) {}

    public record UpsertPolicyRequest(String userType, boolean required, Integer graceDays) {}

    public record MemberStatusDto(
            int memberId,
            int accountId,
            String firstName,
            String lastName,
            String email,
            String userType,
            boolean enrolled,
            boolean mandated) {}

    public record MemberStatusResponse(List<MemberStatusDto> members) {}

    public record UserTypesResponse(List<String> userTypes) {}

    public record AuditEntryDto(
            int id,
            int accountId,
            Integer actorId,
            String event,
            String factorKind,
            String userAgent,
            String country,
            Instant createdAt) {}

    public record AuditResponse(List<AuditEntryDto> entries) {}

    public record AccountSearchResult(
            int id, UUID uid, String displayName, String firstName, String lastName, String email) {}
}
