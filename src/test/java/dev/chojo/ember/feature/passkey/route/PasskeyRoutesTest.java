/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.SessionCookies;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequest;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequestPurpose;
import dev.chojo.ember.feature.devicerequest.service.DeviceApprovalGuards;
import dev.chojo.ember.feature.devicerequest.service.DeviceRequestService;
import dev.chojo.ember.feature.members.service.ManagedAccessService;
import dev.chojo.ember.feature.passkey.service.PasskeyAccountService;
import dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService;
import dev.chojo.ember.feature.passkey.service.PasskeyModeService;
import dev.chojo.ember.feature.passkey.service.PasskeyService;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The passkey routes that read accounts through the services: who may read a code another device
 * shows, under which name the reader answers it, and the account a new passkey is made for.
 */
class PasskeyRoutesTest {
    private final PasskeyAccountService accounts = mock(PasskeyAccountService.class);
    private final PasskeyModeService mode = mock(PasskeyModeService.class);
    private final DeviceApprovalGuards guards = mock(DeviceApprovalGuards.class);
    private final DeviceRequestService devices = mock(DeviceRequestService.class);
    private final RouteHarness harness = RouteHarness.serving(new PasskeyRoutes(
            mock(PasskeyService.class),
            accounts,
            mode,
            mock(AuthService.class),
            guards,
            mock(AuthRateLimiter.class),
            mock(RelyingParties.class),
            devices,
            mock(PasskeyEnrollmentService.class),
            mock(TotpService.class),
            mock(StepUpGuard.class),
            mock(TwoFactorService.class),
            mock(ManagedAccessService.class),
            mock(Api.class),
            mock(SessionCookies.class)));

    @Test
    void aCodeIsShownOnlyToWhoeverMayAnswerIt() {
        var open = mock(DeviceRequest.class);
        when(open.purpose()).thenReturn(DeviceRequestPurpose.SIGN_IN);
        when(open.is(DeviceRequestPurpose.SIGN_IN)).thenReturn(true);
        when(devices.lookup("mine")).thenReturn(Optional.of(open));
        when(devices.lookup("theirs")).thenReturn(Optional.of(open));
        when(guards.mayConfirm(TestSessions.ACCOUNT_ID, open)).thenReturn(true, false);
        when(guards.nameOf(TestSessions.ACCOUNT_ID)).thenReturn("Tom Reader");

        harness.run((server, client) -> {
            var reader = harness.as(TestSessions.member(3, StationPermission.LOGIN));
            var shown = client.post(PREFIX + "/account/passkeys/device-lookup", body("""
                    {"code":"mine"}"""), reader);
            assertEquals(
                    "Tom Reader",
                    json(shown).get("candidates").get(0).get("name").asString());
            assertEquals(
                    Refusal.DEVICE_CODE_NOT_YOURS_ON_LOOKUP,
                    refusalOf(client.post(PREFIX + "/account/passkeys/device-lookup", body("""
                            {"code":"theirs"}"""), reader)));
        });
    }

    @Test
    void aPasskeyForAGoneAccountIsRefused() {
        when(mode.effectiveMode()).thenReturn(PasskeySettings.Mode.PREFERRED);
        when(accounts.account(TestSessions.ACCOUNT_ID)).thenThrow(Refusal.ACCOUNT_NOT_HERE_ON_PASSKEY_CREATION.raise());
        var reader = harness.as(TestSessions.member(3, StationPermission.LOGIN));

        var response = harness.request(client -> client.post(PREFIX + "/account/passkeys/begin", null, reader));

        assertEquals(Refusal.ACCOUNT_NOT_HERE_ON_PASSKEY_CREATION, refusalOf(response));
    }
}
