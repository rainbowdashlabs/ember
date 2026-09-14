/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.api.StepUpRequiredException;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The two questions the guard answers, and the difference between them.
 *
 * <p>Ordinary sensitive routes ask whether the session proved itself recently. The one route that
 * vouches for another device asks something stricter: whether somebody proved themselves here, just
 * now. Everything below is about that second question, because getting it wrong would let two
 * sessions vouch for each other in a circle with nobody ever proving anything.
 */
class StepUpGuardTest {

    private TwoFactorService twoFactorService;
    private StepUpGuard guard;

    @BeforeEach
    void setUp() {
        twoFactorService = mock(TwoFactorService.class);
        when(twoFactorService.availableProofs(anyInt()))
                .thenReturn(EnumSet.of(StepUpProof.PASSWORD, StepUpProof.PASSKEY));
        guard = new StepUpGuard(new Auth(), new Demo(), twoFactorService);
    }

    private static UserSession sessionProvedWith(StepUpProof proof, Instant when) {
        var account = new Account(1, null, "a@test.com", null, "A", "B", true, null, "A B", null, null);
        return new UserSession(account, 1, null, null, null, Set.of(), Set.of(), when, proof);
    }

    @Test
    void aLocalProofGivenJustNowIsEnoughToVouch() {
        var session = sessionProvedWith(StepUpProof.PASSWORD, Instant.now());
        assertDoesNotThrow(() -> guard.requireLocalProof(session, StepUpCategory.ACCOUNT_SECURITY));
    }

    @Test
    void aSessionThatProvedNothingCannotVouch() {
        var session = sessionProvedWith(null, null);
        assertThrows(
                StepUpRequiredException.class, () -> guard.requireLocalProof(session, StepUpCategory.ACCOUNT_SECURITY));
    }

    /**
     * The window for vouching is its own and far shorter than the one every other sensitive route
     * shares, so a proof given early in a session does not still buy an approval an hour later.
     */
    @Test
    void aLocalProofGoesStaleLongBeforeTheOrdinaryWindowDoes() {
        var session = sessionProvedWith(StepUpProof.PASSWORD, Instant.now().minusSeconds(240));

        assertTrue(guard.isFresh(session), "four minutes is still fresh for an ordinary sensitive route");
        assertThrows(
                StepUpRequiredException.class,
                () -> guard.requireLocalProof(session, StepUpCategory.ACCOUNT_SECURITY),
                "but it is not somebody answering now, which is what vouching asks for");
    }

    /**
     * The refusal is what the dialog renders, so a proof the route will not accept must not appear
     * in it. Today every proof is local and the set is unchanged; the assertion is here so that the
     * proof added for another device is excluded the moment it exists.
     */
    @Test
    void theRefusalOffersOnlyProofsTheRouteWouldAccept() {
        var session = sessionProvedWith(null, null);

        var refusal = assertThrows(
                StepUpRequiredException.class, () -> guard.requireLocalProof(session, StepUpCategory.ACCOUNT_SECURITY));

        assertTrue(refusal.proofs().stream().allMatch(StepUpProof::isLocal));
        assertEquals(StepUpCategory.ACCOUNT_SECURITY, refusal.category());
    }
}
