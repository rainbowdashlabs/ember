/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.inventory.entity.MyInventoryItem;
import dev.chojo.ember.feature.members.service.ManagedAccessService;
import dev.chojo.ember.feature.members.service.ManagedAccessService.ManagedAccess;
import dev.chojo.ember.feature.members.service.ManagedMemberService;
import dev.chojo.ember.feature.members.service.ManagedMemberService.ManagedMember;
import dev.chojo.ember.feature.members.service.ManagedMemberService.MemberProfile;
import dev.chojo.ember.feature.members.service.ManagedMemberService.MemberRequirement;
import dev.chojo.ember.feature.members.service.ManagedMemberService.ValueEntry;
import dev.chojo.ember.feature.members.service.ProfileFieldService.MergedValue;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.SafeContentDisposition;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for guardians/managers to view and manage members they are responsible for,
 * including profile fields, inventory items, and GDPR data export.
 */
@Singleton
public class ManagedMemberRoutes implements Routes {
    private final ManagedMemberService managedMembers;
    private final ManagedAccessService accessService;

    @Inject
    public ManagedMemberRoutes(ManagedMemberService managedMembers, ManagedAccessService accessService) {
        this.managedMembers = managedMembers;
        this.accessService = accessService;
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
                prefix + "/managed-members/{memberId}/password",
                this::setPassword,
                StationPermission.MEMBER_GUARDIAN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/managed-members/{memberId}/passkey-code",
                this::issuePasskeyCode,
                StationPermission.MEMBER_GUARDIAN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.delete(
                prefix + "/managed-members/{memberId}/passkey-code",
                this::revokePasskeyCode,
                StationPermission.MEMBER_GUARDIAN);
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

    private static int guardianId(Context ctx) {
        return UserSession.from(ctx).member().id();
    }

    @OpenApi(
            path = "/api/v1/managed-members",
            methods = HttpMethod.GET,
            summary = "List members managed by the current user",
            tags = {"Managed Members"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedMember[].class)))
    private void listManaged(Context ctx) {
        ctx.json(managedMembers.managed(guardianId(ctx)));
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
        ctx.json(managedMembers.profile(guardianId(ctx), pathInt(ctx, "memberId")));
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/profile",
            methods = HttpMethod.PUT,
            summary = "Set profile values for a managed member",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ManagedMemberSetValuesRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MergedValue[].class)))
    private void setProfile(Context ctx) {
        int memberId = pathInt(ctx, "memberId");
        var request = ctx.bodyAsClass(ManagedMemberSetValuesRequest.class);
        ctx.json(managedMembers.setProfile(guardianId(ctx), memberId, request.values()));
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
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetManagedPasswordRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedAccess.class)))
    private void setPassword(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SetManagedPasswordRequest.class);
        ctx.json(accessService.setPassword(session.member().id(), pathInt(ctx, "memberId"), request.password()));
    }

    /**
     * The QR code held up in the room, behind the same proof as every credential-planting action:
     * a hijacked guardian session putting a credential on a child's account is what this is
     * otherwise wide open to.
     */
    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/passkey-code",
            methods = HttpMethod.POST,
            summary = "Issue a passkey code for a managed member",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PasskeyCodeResponse.class)))
    private void issuePasskeyCode(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var issued = accessService.issuePasskeyCode(
                session.member().id(),
                pathInt(ctx, "memberId"),
                session.accountId(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(new PasskeyCodeResponse(issued.code(), issued.qrPng(), issued.expiresAt()));
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/passkey-code",
            methods = HttpMethod.DELETE,
            summary = "Revoke the passkey code of a managed member",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void revokePasskeyCode(Context ctx) {
        UserSession session = UserSession.from(ctx);
        accessService.revokePasskeyCode(session.member().id(), pathInt(ctx, "memberId"));
        ctx.json(new MessageResponse("Code revoked"));
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
    public record SetManagedPasswordRequest(String password) {}

    /**
     * @param enabled whether the managed member may sign in
     */
    public record SetLoginRequest(boolean enabled) {}

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/inventory-items",
            methods = HttpMethod.GET,
            summary = "Get inventory items for a managed member",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MyInventoryItem[].class)))
    private void getMemberInventory(Context ctx) {
        ctx.json(managedMembers.inventory(guardianId(ctx), pathInt(ctx, "memberId")));
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/inventory-requirements",
            methods = HttpMethod.GET,
            summary = "Get inventory requirements for a managed member",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberRequirement[].class)))
    private void getMemberRequirements(Context ctx) {
        ctx.json(managedMembers.requirements(guardianId(ctx), pathInt(ctx, "memberId")));
    }

    @OpenApi(
            path = "/api/v1/managed-members/{memberId}/gdpr-export",
            methods = HttpMethod.GET,
            summary = "Export all personal data for a managed member (GDPR/DSGVO)",
            tags = {"Managed Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Object.class)))
    private void gdprExport(Context ctx) {
        var export = managedMembers.export(guardianId(ctx), pathInt(ctx, "memberId"));
        String filename = DocumentName.of("json", DocumentWord.DATA_EXPORT.in("de"), DocumentName.part(export.name()));
        ctx.contentType("application/json");
        ctx.header(
                "Content-Disposition",
                SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, filename));
        ctx.json(export.data());
    }

    public record ManagedMemberSetValuesRequest(List<ValueEntry> values) {}
}
