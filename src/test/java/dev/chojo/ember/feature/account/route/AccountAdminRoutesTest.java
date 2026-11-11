/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.refusal.AdminRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountListQuery;
import dev.chojo.ember.feature.account.entity.AccountPasswordState;
import dev.chojo.ember.feature.account.entity.AccountSort;
import dev.chojo.ember.feature.account.entity.IssuedOneTimePassword;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountOverviewService;
import dev.chojo.ember.feature.account.service.AccountOverviewService.AccountOverviewPage;
import dev.chojo.ember.feature.account.service.AccountSetupMailService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.OneTimePasswordService;
import dev.chojo.ember.feature.mail.service.MailConfirmationPolicy;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The instance administrator's account list and the one-time password and setup mail sent from it,
 * over HTTP.
 */
class AccountAdminRoutesTest {
    private AccountOverviewService overview;
    private OneTimePasswordService oneTimePasswords;
    private AccountRepository accounts;
    private MailRecipientService recipients;
    private AuthService auth;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        overview = mock(AccountOverviewService.class);
        oneTimePasswords = mock(OneTimePasswordService.class);
        accounts = mock(AccountRepository.class);
        recipients = mock(MailRecipientService.class);
        auth = mock(AuthService.class);
        harness = RouteHarness.serving(routes());
    }

    private AccountAdminRoutes routes() {
        return new AccountAdminRoutes(
                overview,
                oneTimePasswords,
                new AccountSetupMailService(accounts, recipients, auth, mock(MailConfirmationPolicy.class)));
    }

    private static Account account(@Nullable Instant setupCompletedAt) {
        return new Account(
                42,
                UUID.randomUUID(),
                "lena@test.com",
                null,
                "Lena",
                "Weber",
                true,
                InstanceUserType.USER,
                "Lena Weber",
                null,
                setupCompletedAt);
    }

    @Test
    void theListIsAskedForTheSearchAndThePageItNames() {
        when(overview.list(AccountListQuery.search("lena"), 2, 10))
                .thenReturn(new AccountOverviewPage(List.of(), 21, 2, 10));
        when(overview.list(AccountListQuery.search(null), 0, AccountOverviewService.DEFAULT_SIZE))
                .thenReturn(new AccountOverviewPage(List.of(), 3, 0, AccountOverviewService.DEFAULT_SIZE));

        harness.run((server, client) -> {
            var page = client.get(
                    PREFIX + "/admin/accounts?q=lena&page=2&size=10", harness.as(TestSessions.administrator()));
            assertEquals(21, json(page).path("total").asInt());
            var first = client.get(PREFIX + "/admin/accounts", harness.as(TestSessions.administrator()));
            assertEquals(3, json(first).path("total").asInt());
            var stationAdministrator = client.get(
                    PREFIX + "/admin/accounts",
                    harness.as(TestSessions.member(3, StationPermission.STATION_ADMINISTRATOR)));
            assertEquals(403, stationAdministrator.code(), "a station's administration does not see every account");
        });
    }

    @Test
    void everyFilterAndTheOrderReachTheQuery() {
        var captured = ArgumentCaptor.forClass(AccountListQuery.class);
        when(overview.list(captured.capture(), eq(0), eq(AccountOverviewService.DEFAULT_SIZE)))
                .thenReturn(new AccountOverviewPage(List.of(), 0, 0, AccountOverviewService.DEFAULT_SIZE));
        UUID station = UUID.randomUUID();
        UUID association = UUID.randomUUID();
        String address = "%s/admin/accounts?role=ADMINISTRATOR&password=NONE&password=ONE_TIME&twoFactor=true"
                        .formatted(PREFIX)
                + "&passkeysMin=1&passkeysMax=3&confirmed=false&withoutAddress=true&signedInFrom=2026-03-01&signedInTo=2026-03-31"
                + "&neverSignedIn=true&station=%s&withoutStation=true&association=%s".formatted(station, association)
                + "&sort=lastSignIn&direction=desc";

        var answer = harness.request(client -> client.get(address, harness.as(TestSessions.administrator())));

        assertEquals(200, answer.code());
        var query = captured.getValue();
        assertEquals(Set.of(InstanceUserType.ADMINISTRATOR), query.roles());
        assertEquals(Set.of(AccountPasswordState.NONE, AccountPasswordState.ONE_TIME), query.passwords());
        assertEquals(Boolean.TRUE, query.twoFactor());
        assertEquals(1, query.passkeysMin());
        assertEquals(3, query.passkeysMax());
        assertEquals(Boolean.FALSE, query.confirmed());
        assertTrue(query.withoutAddress());
        assertEquals(LocalDate.of(2026, 3, 1), query.signedInFrom());
        assertEquals(LocalDate.of(2026, 3, 31), query.signedInTo());
        assertTrue(query.neverSignedIn());
        assertEquals(Set.of(station), query.stations());
        assertTrue(query.withoutStation());
        assertEquals(Set.of(association), query.associations());
        assertFalse(query.withoutAssociation());
        assertEquals(AccountSort.LAST_SIGN_IN, query.sort());
        assertTrue(query.descending());
        assertNull(query.search());
    }

    @Test
    void aFilterNamingNothingThatExistsIsRefused() {
        harness.run((server, client) -> {
            for (String wrong : List.of(
                    "role=KING",
                    "confirmed=maybe",
                    "signedInFrom=yesterday",
                    "station=north",
                    "passkeysMin=many",
                    "sort=age",
                    "direction=sideways")) {
                var answer = client.get(PREFIX + "/admin/accounts?" + wrong, harness.as(TestSessions.administrator()));
                assertEquals(AdminRefusal.ACCOUNT_LIST_UNKNOWN_CHOICE, refusalOf(answer), wrong);
            }
        });
        verify(overview, never()).list(any(), anyInt(), anyInt());
    }

    @Test
    void anInstanceAdministratorIsHandedTheOneTimePassword() {
        when(oneTimePasswords.issueForInstance(eq(TestSessions.ACCOUNT_ID), eq(42), any(), any()))
                .thenReturn(new IssuedOneTimePassword(42, "Lena Weber", "lena", "k7mq-x2pd-9wtr-hb4z", Instant.EPOCH));

        var answer = harness.request(client -> client.post(
                PREFIX + "/admin/accounts/42/one-time-password", body("{}"), harness.as(TestSessions.administrator())));

        assertEquals("lena", json(answer).path("loginName").asString());
        verify(oneTimePasswords).issueForInstance(eq(TestSessions.ACCOUNT_ID), eq(42), any(), any());
    }

    @Test
    void theOneTimePasswordAsksForAFreshSecondFactor() {
        var router = mock(JavalinDefaultRoutingApi.class);

        routes().register(router, PREFIX);

        verify(router)
                .post(
                        eq(PREFIX + "/admin/accounts/{id}/one-time-password"),
                        any(),
                        eq(InstancePermission.ADMINISTRATOR),
                        eq(StepUpCategory.ACCOUNT_SECURITY));
    }

    @Test
    void theSetupMailSendsWhatTheAccountWaitsFor() {
        when(accounts.findById(42)).thenReturn(Optional.of(account(null)));
        when(recipients.isReachable(42)).thenReturn(true);

        var answer = harness.request(client -> client.post(
                PREFIX + "/admin/accounts/42/setup-mail", body("{}"), harness.as(TestSessions.administrator())));

        assertEquals("INVITATION_SENT", json(answer).path("outcome").asString());
        verify(auth).sendPasswordSetup(42);
    }

    @Test
    void theSetupMailIsRefusedAsTheStationsResendIs() {
        when(accounts.findById(42)).thenReturn(Optional.of(account(Instant.EPOCH)));
        when(accounts.hasChosenPassword(42)).thenReturn(true);

        harness.run((server, client) -> {
            var setUp = client.post(
                    PREFIX + "/admin/accounts/42/setup-mail", body("{}"), harness.as(TestSessions.administrator()));
            assertEquals(MemberRefusal.ACCOUNT_ALREADY_SET_UP, refusalOf(setUp));
            var unknown = client.post(
                    PREFIX + "/admin/accounts/7/setup-mail", body("{}"), harness.as(TestSessions.administrator()));
            assertEquals(MemberRefusal.ACCOUNT_NOT_HERE_ON_SETUP_MAIL, refusalOf(unknown));
            var station = client.post(
                    PREFIX + "/admin/accounts/42/setup-mail",
                    body("{}"),
                    harness.as(TestSessions.member(3, StationPermission.STATION_ADMINISTRATOR)));
            assertEquals(403, station.code(), "a station's administration cannot write to any account");
        });
        verify(auth, never()).sendPasswordSetup(anyInt());
    }
}
