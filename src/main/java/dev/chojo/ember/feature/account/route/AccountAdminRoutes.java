/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.account.entity.IssuedOneTimePassword;
import dev.chojo.ember.feature.account.service.AccountOverviewService;
import dev.chojo.ember.feature.account.service.AccountOverviewService.AccountOverviewPage;
import dev.chojo.ember.feature.account.service.OneTimePasswordService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The instance administrator's account list, and the one-time password issued from it. The other
 * actions on an account in that list (resetting its second factor, retiring its password) keep the
 * routes they always had.
 */
@Singleton
public class AccountAdminRoutes implements Routes {
    private final AccountOverviewService overviewService;
    private final OneTimePasswordService oneTimePasswords;

    @Inject
    public AccountAdminRoutes(AccountOverviewService overviewService, OneTimePasswordService oneTimePasswords) {
        this.overviewService = overviewService;
        this.oneTimePasswords = oneTimePasswords;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/accounts", this::listAccounts, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/accounts/{id}/one-time-password",
                this::issueOneTimePassword,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.ACCOUNT_SECURITY);
    }

    @OpenApi(
            path = "/api/v1/admin/accounts",
            methods = HttpMethod.GET,
            summary = "List every account of the instance, a page at a time",
            tags = {"Admin Accounts"},
            queryParams = {
                @OpenApiParam(name = "q", description = "A part of the name, the address or the sign-in name"),
                @OpenApiParam(name = "page", type = Integer.class, description = "The page, counted from zero"),
                @OpenApiParam(name = "size", type = Integer.class, description = "How many accounts a page holds")
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AccountOverviewPage.class)))
    private void listAccounts(Context ctx) {
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(AccountOverviewService.DEFAULT_SIZE);
        ctx.json(overviewService.list(ctx.queryParam("q"), page, size));
    }

    @OpenApi(
            path = "/api/v1/admin/accounts/{id}/one-time-password",
            methods = HttpMethod.POST,
            summary = "Issue a one-time password for any account but one's own",
            description =
                    "Lays a generated password down as the account's password, ends its sessions and asks for a new password at the next sign-in. The password is answered once and works for seven days.",
            tags = {"Admin Accounts"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = IssuedOneTimePassword.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void issueOneTimePassword(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(oneTimePasswords.issueForInstance(
                session.accountId(), pathInt(ctx, "id"), ctx.userAgent(), ctx.header("CF-IPCountry")));
    }
}
