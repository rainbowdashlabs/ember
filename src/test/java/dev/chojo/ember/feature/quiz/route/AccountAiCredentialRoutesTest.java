/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.refusal.BodyRefusal;
import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.feature.quiz.service.AiCredentialService;
import dev.chojo.ember.feature.quiz.service.AiCredentialService.AiCredentialSummary;
import dev.chojo.ember.feature.quiz.service.AiCredentialService.SaveOutcome;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A person's own AI key over HTTP: the provider goes in and comes out as its constant. */
class AccountAiCredentialRoutesTest {
    private final AiCredentialService credentials = mock(AiCredentialService.class);
    private final RouteHarness harness = RouteHarness.serving(new AccountAiCredentialRoutes(credentials));

    @Test
    void theProviderIsTakenAndAnsweredAsTheEnum() {
        when(credentials.save(TestSessions.ACCOUNT_ID, AiVendor.GEMINI, "flash", "sk-g"))
                .thenReturn(SaveOutcome.SAVED);
        when(credentials.summary(TestSessions.ACCOUNT_ID))
                .thenReturn(Optional.of(new AiCredentialSummary(AiVendor.GEMINI, "flash", true, "sk-g")));

        harness.run((server, client) -> {
            var reader = harness.as(TestSessions.member(3));
            var saved = client.put(PREFIX + "/account/ai-credential", body("""
                    {"provider":"GEMINI","model":"flash","apiKey":"sk-g"}"""), reader);
            assertEquals("GEMINI", json(saved).get("provider").asString());
        });
    }

    @Test
    void aProviderThisInstanceCannotCallIsRefused() {
        harness.run((server, client) -> {
            var reader = harness.as(TestSessions.member(3));
            assertEquals(
                    BodyRefusal.BODY_DOES_NOT_MATCH,
                    refusalOf(client.put(PREFIX + "/account/ai-credential", body("""
                            {"provider":"mystery","apiKey":"sk"}"""), reader)));
        });

        verifyNoInteractions(credentials);
    }
}
