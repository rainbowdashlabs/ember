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
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two questions the guard answers, and the difference between them.
 *
 * <p>Ordinary sensitive routes ask whether the session proved itself recently. The one route that
 * vouches for another device asks something stricter: whether somebody proved themselves here, just
 * now. Most of what follows is about that second question, because getting it wrong would let two
 * sessions vouch for each other in a circle with nobody ever proving anything.
 */
class StepUpGuardTest {

    private TwoFactorService twoFactorService;
    private StepUpGuard guard;

    @BeforeEach
    void setUp() {
        twoFactorService = mock(TwoFactorService.class);
        when(twoFactorService.availableProofs(anyInt()))
                .thenReturn(EnumSet.of(StepUpProof.PASSWORD, StepUpProof.PASSKEY, StepUpProof.ANOTHER_DEVICE));
        when(twoFactorService.spendSessionProof(anyInt(), any())).thenReturn(true);
        guard = new StepUpGuard(new Auth(), new Demo(), twoFactorService);
    }

    private static Account account() {
        return new Account(1, null, "a@test.com", null, "A", "B", true, null, "A B", null, null);
    }

    private static UserSession sessionProvedWith(StepUpProof proof, Instant when) {
        return new UserSession(account(), 1, null, null, null, Set.of(), Set.of(), when, proof);
    }

    private static UserSession sessionProvedWith(StepUpProof proof, Instant when, StepUpCategory category) {
        return new UserSession(account(), 1, null, null, null, Set.of(), Set.of(), when, proof, category, false);
    }

    private static UserSession vouchedSessionProvedWith(StepUpProof proof, Instant when) {
        return new UserSession(account(), 1, null, null, null, Set.of(), Set.of(), when, proof, null, true);
    }

    @Test
    void aLocalProofGivenJustNowIsEnoughToVouch() {
        var session = sessionProvedWith(StepUpProof.PASSWORD, Instant.now());
        assertDoesNotThrow(() -> guard.spendLocalProof(session, StepUpCategory.ACCOUNT_SECURITY));
    }

    @Test
    void aSessionThatProvedNothingCannotVouch() {
        var session = sessionProvedWith(null, null);
        assertThrows(
                StepUpRequiredException.class, () -> guard.spendLocalProof(session, StepUpCategory.ACCOUNT_SECURITY));
    }

    /**
     * The circle this whole feature rests on not closing. A session that exists because another
     * device approved it may never approve one in turn, however it has proved itself since: two
     * stolen cookies would otherwise vouch for each other for ever on one proof from months ago.
     */
    @Test
    void aVouchedForSessionMayNeverVouchInTurn() {
        var session = vouchedSessionProvedWith(StepUpProof.PASSWORD, Instant.now());

        assertThrows(ForbiddenResponse.class, () -> guard.spendLocalProof(session, StepUpCategory.ACCOUNT_SECURITY));
        verify(twoFactorService, never()).spendSessionProof(anyInt(), any());
    }

    /**
     * The same bar on the public demo, where every other proof is waived. The waiver exists because a
     * visitor there clicked a face and has nothing to prove themselves with; the relay is closed on
     * every instance regardless, because it rests on how the session was born and not on what it can
     * prove.
     */
    @Test
    void theDemoWaiverDoesNotOpenTheCircle() throws Exception {
        var demo = new Demo();
        Field enabled = Demo.class.getDeclaredField("enabled");
        enabled.setAccessible(true);
        enabled.set(demo, true);
        var demoGuard = new StepUpGuard(new Auth(), demo, twoFactorService);

        assertDoesNotThrow(
                () -> demoGuard.spendLocalProof(sessionProvedWith(null, null), StepUpCategory.ACCOUNT_SECURITY));
        assertThrows(
                ForbiddenResponse.class,
                () -> demoGuard.spendLocalProof(
                        vouchedSessionProvedWith(StepUpProof.PASSWORD, Instant.now()),
                        StepUpCategory.ACCOUNT_SECURITY));
    }

    /**
     * The other half of the relay. Being vouched for makes a session fresh, and freshness is what the
     * ordinary routes ask for, so without this the stamp itself would carry the vouching onward.
     */
    @Test
    void aStampAnotherDeviceProducedIsNotSomebodyProvingThemselvesHere() {
        var session = sessionProvedWith(StepUpProof.ANOTHER_DEVICE, Instant.now());

        assertThrows(
                StepUpRequiredException.class, () -> guard.spendLocalProof(session, StepUpCategory.ACCOUNT_SECURITY));
        verify(twoFactorService, never()).spendSessionProof(anyInt(), any());
    }

    /**
     * The window for vouching is its own and far shorter than the one every other sensitive route
     * shares, so a proof given early in a session does not still buy an approval an hour later.
     */
    @Test
    void aLocalProofGoesStaleLongBeforeTheOrdinaryWindowDoes() {
        var session = sessionProvedWith(StepUpProof.PASSWORD, Instant.now().minusSeconds(240));

        assertTrue(
                guard.isFresh(session, StepUpCategory.ACCOUNT_SECURITY),
                "four minutes is still fresh for an ordinary sensitive route");
        assertThrows(
                StepUpRequiredException.class,
                () -> guard.spendLocalProof(session, StepUpCategory.ACCOUNT_SECURITY),
                "but it is not somebody answering now, which is what vouching asks for");
    }

    /**
     * The proof buys one approval and not a window of them. Of two requests racing on one stamp the
     * loser finds nothing left to spend and is refused, rather than both riding the same answer.
     */
    @Test
    void aProofAlreadySpentDoesNotBuyASecondApproval() {
        when(twoFactorService.spendSessionProof(anyInt(), any())).thenReturn(false);
        var session = sessionProvedWith(StepUpProof.PASSWORD, Instant.now());

        assertThrows(
                StepUpRequiredException.class, () -> guard.spendLocalProof(session, StepUpCategory.ACCOUNT_SECURITY));
    }

    /**
     * A confirmation given on another device answers the category its approver was shown. Otherwise a
     * stolen cookie could raise the mildest demand the product makes, have it confirmed, and spend
     * the answer on the gravest.
     */
    @Test
    void aConfirmationGivenElsewhereAnswersOnlyTheCategoryItNamed() {
        var session = sessionProvedWith(StepUpProof.ANOTHER_DEVICE, Instant.now(), StepUpCategory.FEDERATION);

        assertTrue(guard.isFresh(session, StepUpCategory.FEDERATION));
        assertFalse(guard.isFresh(session, StepUpCategory.ACCOUNT_SECURITY));
        assertThrows(StepUpRequiredException.class, () -> guard.require(session, StepUpCategory.ACCOUNT_SECURITY));
    }

    /**
     * A proof given at this keyboard carries no category and answers every one of them, which is how
     * the product has always worked: somebody who just typed their password has shown who they are.
     */
    @Test
    void aProofGivenHereStillAnswersEveryCategory() {
        var session = sessionProvedWith(StepUpProof.PASSWORD, Instant.now());

        assertTrue(guard.isFresh(session, StepUpCategory.FEDERATION));
        assertTrue(guard.isFresh(session, StepUpCategory.ACCOUNT_SECURITY));
    }

    /**
     * The refusal is what the dialog renders, so a proof the route will not accept must not appear in
     * it. Confirming elsewhere is offered by the ordinary step-up and never by this one: nobody could
     * answer a demand to prove themselves here by asking somebody over there.
     */
    @Test
    void theRefusalOffersOnlyProofsTheRouteWouldAccept() {
        var session = sessionProvedWith(null, null);

        var refusal = assertThrows(
                StepUpRequiredException.class, () -> guard.spendLocalProof(session, StepUpCategory.ACCOUNT_SECURITY));

        assertTrue(refusal.proofs().stream().allMatch(StepUpProof::isLocal));
        assertFalse(refusal.proofs().contains(StepUpProof.ANOTHER_DEVICE));
        assertEquals(StepUpCategory.ACCOUNT_SECURITY, refusal.category());
    }
}
