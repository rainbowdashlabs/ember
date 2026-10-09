/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountAction;
import dev.chojo.ember.feature.account.entity.IssuedOneTimePassword;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.AuthService.EmailChangeResult;
import dev.chojo.ember.feature.account.service.OneTimePasswordService;
import dev.chojo.ember.feature.members.service.MemberAccountService;
import dev.chojo.ember.feature.members.service.MemberAccountService.UpdateAccountRequest;
import dev.chojo.ember.feature.members.service.MemberAccountService.UpdateAccountResponse;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService;
import dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService.IssuedCode;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Acting on the accounts behind a station's members, over HTTP: the route reads the request and
 * leaves every decision about whose account may be touched to its service.
 */
class MemberRoutesTest {
    private static final int TARGET = 42;

    private AuthService auth;
    private MemberAccountService memberAccounts;
    private PasskeyEnrollmentService enrollment;
    private OneTimePasswordService oneTimePasswords;
    private RouteHarness harness;
    private UserSession manager;

    private static Account target() {
        return new Account(TARGET, null, "t@x.local", null, "Tom", "T", true, null, "Tom T", null, null);
    }

    @BeforeEach
    void setup() {
        auth = mock(AuthService.class);
        memberAccounts = mock(MemberAccountService.class);
        enrollment = mock(PasskeyEnrollmentService.class);
        oneTimePasswords = mock(OneTimePasswordService.class);
        harness = RouteHarness.serving(new MemberRoutes(
                auth, memberAccounts, mock(StationMemberInviteService.class), enrollment, oneTimePasswords));
        manager = TestSessions.member(3, StationPermission.MEMBER_EDIT, StationPermission.LOGIN);
    }

    @Test
    void anAccountUpdateIsHandedToTheService() {
        when(memberAccounts.update(any(), eq(3), eq(TARGET), any()))
                .thenReturn(new UpdateAccountResponse("Account updated", EmailChangeResult.COMMITTED, false));

        var answer = harness.request(client -> client.put(
                PREFIX + "/members/" + TARGET,
                body("{\"email\": \"new@test.com\", \"firstName\": \"Tim\", \"lastName\": \"T\"}"),
                harness.as(manager)));

        assertEquals("COMMITTED", json(answer).path("emailChange").asString());
        verify(memberAccounts)
                .update(any(), eq(3), eq(TARGET), eq(new UpdateAccountRequest("new@test.com", null, "Tim", "T")));
    }

    @Test
    void aPasswordResetAsksForAnAccountTheCallerMayActOn() {
        when(auth.adminResetPassword(TARGET, true)).thenReturn(true);

        harness.run((server, client) -> {
            var reset = client.post(
                    PREFIX + "/members/reset-password",
                    body("{\"accountId\": 42, \"forceChange\": true}"),
                    harness.as(manager));
            assertEquals(200, reset.code());
            var unnamed = client.post(PREFIX + "/members/reset-password", body("{}"), harness.as(manager));
            assertEquals(MemberRefusal.ACCOUNT_NOT_NAMED_ON_PASSWORD_RESET, refusalOf(unnamed));
            var failed =
                    client.post(PREFIX + "/members/reset-password", body("{\"accountId\": 43}"), harness.as(manager));
            assertEquals(MemberRefusal.ACCOUNT_NOT_HERE_ON_PASSWORD_RESET_MAIL, refusalOf(failed));
        });

        verify(memberAccounts)
                .actionableAccount(
                        eq(TARGET),
                        any(),
                        eq(MemberRefusal.ACCOUNT_NOT_HERE_ON_PASSWORD_RESET),
                        eq(AccountAction.PASSWORD_RESET));
    }

    @Test
    void onboardingAgainAsksForAnAccountTheCallerMayActOn() {
        when(enrollment.onboardAgain(eq(TARGET), anyInt(), any(), any())).thenReturn(true);

        harness.run((server, client) -> {
            var again =
                    client.post(PREFIX + "/members/onboard-again", body("{\"accountId\": 42}"), harness.as(manager));
            assertTrue(json(again).path("mailed").asBoolean());
            var unnamed = client.post(PREFIX + "/members/onboard-again", body("{}"), harness.as(manager));
            assertEquals(MemberRefusal.ACCOUNT_NOT_NAMED_ON_ONBOARDING_AGAIN, refusalOf(unnamed));
        });

        verify(memberAccounts)
                .actionableAccount(
                        eq(TARGET),
                        any(),
                        eq(MemberRefusal.ACCOUNT_NOT_HERE_ON_ONBOARDING_AGAIN),
                        eq(AccountAction.ONBOARD_AGAIN));
    }

    @Test
    void aStationAdministratorIsHandedTheOneTimePasswordForTheirStation() {
        var administrator = TestSessions.member(3, StationPermission.STATION_ADMINISTRATOR, StationPermission.LOGIN);
        when(oneTimePasswords.issueForStation(eq(3), eq(TestSessions.ACCOUNT_ID), eq(TARGET), any(), any()))
                .thenReturn(new IssuedOneTimePassword(TARGET, "Tom T", "tom", "k7mq-x2pd-9wtr-hb4z", Instant.EPOCH));

        harness.run((server, client) -> {
            var issued = client.post(
                    PREFIX + "/members/" + TARGET + "/one-time-password", body("{}"), harness.as(administrator));
            assertEquals("k7mq-x2pd-9wtr-hb4z", json(issued).path("password").asString());
            var memberEditor =
                    client.post(PREFIX + "/members/" + TARGET + "/one-time-password", body("{}"), harness.as(manager));
            assertEquals(403, memberEditor.code(), "editing members is not enough");
        });
    }

    @Test
    void theOneTimePasswordAsksForAFreshSecondFactor() {
        var router = mock(JavalinDefaultRoutingApi.class);

        new MemberRoutes(auth, memberAccounts, mock(StationMemberInviteService.class), enrollment, oneTimePasswords)
                .register(router, PREFIX);

        verify(router)
                .post(
                        eq(PREFIX + "/members/{accountId}/one-time-password"),
                        any(),
                        eq(StationPermission.STATION_ADMINISTRATOR),
                        eq(StepUpCategory.ACCOUNT_SECURITY));
    }

    @Test
    void aPasskeyCodeIsIssuedForAnAddresslessAccountAndRevoked() {
        when(memberAccounts.addresslessAccount(eq(TARGET), any())).thenReturn(target());
        when(enrollment.issueCodeWithQr(eq(TARGET), anyInt(), any(), any(), any()))
                .thenReturn(new IssuedCode("123456", "png", Instant.EPOCH));

        harness.run((server, client) -> {
            var issued =
                    client.post(PREFIX + "/members/passkey-code", body("{\"accountId\": 42}"), harness.as(manager));
            assertEquals("123456", json(issued).path("code").asString());
            var unnamed = client.post(PREFIX + "/members/passkey-code", body("{}"), harness.as(manager));
            assertEquals(MemberRefusal.ACCOUNT_NOT_NAMED_ON_PASSKEY_CODE, refusalOf(unnamed));
            assertEquals(
                    200,
                    client.delete(PREFIX + "/members/passkey-code/42", null, harness.as(manager))
                            .code());
        });

        verify(memberAccounts).requireStationAccount(TARGET, 3);
        verify(enrollment).revokeCode(TARGET);
    }
}
