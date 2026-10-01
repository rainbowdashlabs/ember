/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.feature.account.service.AuthService.AddressOutcome;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * How the address setup answers what became of an attempt.
 */
class AuthRoutesTest {

    @Test
    void everyAddressSetupThatFailedIsRefusedOnItsOwnTerms() {
        var refusals = Arrays.stream(AddressOutcome.values())
                .filter(outcome -> outcome != AddressOutcome.OK)
                .map(AuthRoutes::addressSetupRefusal)
                .toList();

        assertEquals(
                AddressOutcome.values().length - 1, refusals.stream().distinct().count());
        refusals.forEach(refusal -> assertEquals(4, refusal.status().getCode() / 100, refusal.name()));
    }

    @Test
    void anAddressThatWasSetIsNoRefusal() {
        assertThrows(IllegalArgumentException.class, () -> AuthRoutes.addressSetupRefusal(AddressOutcome.OK));
    }
}
