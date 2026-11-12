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
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.account.entity.AccountPasswordState;
import dev.chojo.ember.feature.account.entity.AccountSetupMail;
import dev.chojo.ember.feature.account.entity.AccountSort;
import dev.chojo.ember.feature.account.entity.IssuedOneTimePassword;
import dev.chojo.ember.feature.account.service.AccountOverviewService;
import dev.chojo.ember.feature.account.service.AccountOverviewService.AccountOverviewPage;
import dev.chojo.ember.feature.account.service.AccountSetupMailService;
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
 * The instance administrator's account list, and the one-time password and the setup mail sent from
 * it. The other actions on an account in that list (resetting its second factor, retiring its
 * password) keep the routes they always had.
 */
@Singleton
public class AccountAdminRoutes implements Routes {
    private final AccountOverviewService overviewService;
    private final OneTimePasswordService oneTimePasswords;
    private final AccountSetupMailService setupMails;

    @Inject
    public AccountAdminRoutes(
            AccountOverviewService overviewService,
            OneTimePasswordService oneTimePasswords,
            AccountSetupMailService setupMails) {
        this.overviewService = overviewService;
        this.oneTimePasswords = oneTimePasswords;
        this.setupMails = setupMails;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/accounts", this::listAccounts, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/accounts/{id}/one-time-password",
                this::issueOneTimePassword,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(prefix + "/admin/accounts/{id}/setup-mail", this::sendSetupMail, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/admin/accounts",
            methods = HttpMethod.GET,
            summary = "List every account of the instance, filtered, sorted and a page at a time",
            description =
                    "Every filter left out narrows nothing. Repeated values of one filter are alternatives; the filters all have to hold together.",
            tags = {"Admin Accounts"},
            queryParams = {
                @OpenApiParam(name = "q", description = "A part of the name, the address or the sign-in name"),
                @OpenApiParam(name = "page", type = Integer.class, description = "The page, counted from zero"),
                @OpenApiParam(name = "size", type = Integer.class, description = "How many accounts a page holds"),
                @OpenApiParam(
                        name = "role",
                        type = InstanceUserType.class,
                        description = "A role on the instance, repeatable"),
                @OpenApiParam(
                        name = "password",
                        type = AccountPasswordState.class,
                        description = "A state of the password, repeatable"),
                @OpenApiParam(
                        name = "twoFactor",
                        type = Boolean.class,
                        description = "Whether a second factor is asked after the password"),
                @OpenApiParam(
                        name = "passkeysMin",
                        type = Integer.class,
                        description = "The fewest passkeys to sign in with, inclusive"),
                @OpenApiParam(
                        name = "passkeysMax",
                        type = Integer.class,
                        description = "The most passkeys to sign in with, inclusive"),
                @OpenApiParam(
                        name = "confirmed",
                        type = Boolean.class,
                        description = "Whether a real address was confirmed; stand-in addresses match neither"),
                @OpenApiParam(
                        name = "withoutAddress",
                        type = Boolean.class,
                        description = "Accounts with only a stand-in address, alone or besides the confirmation"),
                @OpenApiParam(name = "signedInFrom", description = "The first day of the last sign-in, inclusive"),
                @OpenApiParam(name = "signedInTo", description = "The last day of the last sign-in, inclusive"),
                @OpenApiParam(
                        name = "neverSignedIn",
                        type = Boolean.class,
                        description = "Accounts that never signed in, alone or besides the days"),
                @OpenApiParam(name = "station", description = "A station uid the account is a member of, repeatable"),
                @OpenApiParam(
                        name = "withoutStation",
                        type = Boolean.class,
                        description = "Accounts in no station, alone or besides the stations"),
                @OpenApiParam(
                        name = "association",
                        description = "An association uid the account holds a role in, repeatable"),
                @OpenApiParam(
                        name = "withoutAssociation",
                        type = Boolean.class,
                        description = "Accounts in no association, alone or besides the associations"),
                @OpenApiParam(name = "sort", type = AccountSort.class, description = "The order, by name by default"),
                @OpenApiParam(name = "direction", description = "asc or desc, asc by default")
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AccountOverviewPage.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void listAccounts(Context ctx) {
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(AccountOverviewService.DEFAULT_SIZE);
        ctx.json(overviewService.list(AccountListQueries.of(ctx), page, size));
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

    @OpenApi(
            path = "/api/v1/admin/accounts/{id}/setup-mail",
            methods = HttpMethod.POST,
            summary = "Send an account that is not yet ready the mail it waits for",
            description =
                    "An account without a password of its own is sent the invitation to set one with a fresh link; one with a password and an unconfirmed real address is sent the request to confirm it. Refused for an account that needs neither and for one nobody can be written to about.",
            tags = {"Admin Accounts"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AccountSetupMail.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void sendSetupMail(Context ctx) {
        ctx.json(setupMails.send(pathInt(ctx, "id")));
    }
}
