/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A partner that did not answer is named in the feature's words; anything it did send is passed on. */
class PartnerCallsTest {

    /** An answer is handed back, silence becomes the feature's refusal, and the partner's own refusal stays. */
    @Test
    void aCallThatAnswersIsNamedOnlyWhereItStaysSilent() {
        assertEquals("ok", PartnerCalls.answeredOr(() -> "ok", EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN));

        var silent = assertThrows(
                RefusalResponse.class,
                () -> PartnerCalls.answeredOr(
                        () -> {
                            throw FederationRefusal.FEDERATION_PARTNER_DID_NOT_ANSWER.raise();
                        },
                        EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN));
        assertEquals(EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN, silent.refusal());

        var said = assertThrows(
                RefusalResponse.class,
                () -> PartnerCalls.answeredOr(
                        () -> {
                            throw EventRefusal.NO_PLACES_LEFT_FOR_PARTNER.raise();
                        },
                        EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN));
        assertEquals(EventRefusal.NO_PLACES_LEFT_FOR_PARTNER, said.refusal());
    }

    /** A call that answers nothing is named the same way. */
    @Test
    void aCallThatAnswersNothingIsNamedTheSameWay() {
        assertDoesNotThrow(() -> PartnerCalls.deliveredOr(() -> {}, EventRefusal.NO_PLACES_LEFT_AT_HOLDER));
        var silent = assertThrows(
                RefusalResponse.class,
                () -> PartnerCalls.deliveredOr(
                        () -> {
                            throw FederationRefusal.FEDERATION_PARTNER_DID_NOT_ANSWER.raise();
                        },
                        EventRefusal.NO_PLACES_LEFT_AT_HOLDER));
        assertEquals(EventRefusal.NO_PLACES_LEFT_AT_HOLDER, silent.refusal());
    }
}
