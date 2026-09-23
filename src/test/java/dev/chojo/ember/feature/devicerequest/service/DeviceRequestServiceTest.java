/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.devicerequest.service;

import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.LoginResult;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequestPurpose;
import dev.chojo.ember.feature.devicerequest.repository.DeviceRequestRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.passkey.service.PasskeyService;
import dev.chojo.ember.feature.passkey.service.TestAuthenticator;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.feature.twofactor.service.BackupCodeService;
import dev.chojo.ember.feature.twofactor.service.SecondFactorCredentialStore;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnRelyingPartyFactory;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceRequestServiceTest extends RepositoryTestBase {

    private static final DeviceRequestRepository deviceRepo = new DeviceRequestRepository();
    private static final EmailService emailService = mock(EmailService.class);

    /**
     * Minting a session is {@code AuthService}'s job and is tested where that lives. What matters
     * here is that a sign-in claim reaches it exactly once, with the account the approval named.
     */
    private static final AuthService authService = mock(AuthService.class);

    private static DeviceRequestService service;

    @BeforeAll
    static void setup() throws Exception {
        var settings = new WebAuthnSettings();
        var api = new Api();
        setField(api, "baseUrl", "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        var parties = WebAuthnRelyingPartyFactory.build(
                settings, api, store, new SecondFactorCredentialStore(twoFactorRepo, store));
        var challengeRepo = new WebAuthnChallengeRepository(TokenHasher.forTesting("repository-test-pepper"));
        var passkeyService = new PasskeyService(
                parties, twoFactorRepo, new TwoFactorAuditService(twoFactorRepo), challengeRepo, settings);
        var twoFactorSettings = new TwoFactorSettings();
        setField(twoFactorSettings, "enabled", true);
        setField(twoFactorSettings, "secretKey", validKey());
        var twoFactorService = new TwoFactorService(
                twoFactorRepo,
                new TotpService(twoFactorSettings, new Demo()),
                new BackupCodeService(twoFactorSettings),
                new TwoFactorAuditService(twoFactorRepo),
                accountRepo,
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                emailService);
        service = new DeviceRequestService(
                deviceRepo,
                passkeyService,
                accountRepo,
                authService,
                twoFactorService,
                new TwoFactorAuditService(twoFactorRepo),
                TokenHasher.forTesting("repository-test-pepper"),
                emailService,
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()));
    }

    private static String validKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) (i * 7);
        return java.util.Base64.getEncoder().encodeToString(key);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** The repository stores hashes, so a test reaching past the service has to hash the same way. */
    private static String hashOf(String raw) {
        return TokenHasher.forTesting("repository-test-pepper").hash(raw);
    }

    /** The mail mock is shared by every test here, so what one sent must not be counted by the next. */
    @BeforeEach
    void forgetEarlierMail() {
        reset(emailService);
    }

    /** Whatever the request is for, where the story under test is not about which door it came to. */
    private DeviceRequestService.PollResult poll(String pollSecret) {
        return service.poll(pollSecret, EnumSet.allOf(DeviceRequestPurpose.class));
    }

    private Account newAccount() {
        return accountRepo.create("device-" + UUID.randomUUID() + "@test.com", "Device", "Owner", true);
    }

    @Test
    void theCodeOpensTheRequestAndAWrongOneNothing() {
        var request = service.createRequest(
                DeviceRequestPurpose.ENROL_PASSKEY, newAccount().email(), "Firefox on Linux", "DE");
        assertEquals(8, request.code().length());

        var found = service.lookup(request.code()).orElseThrow();
        assertEquals("Firefox on Linux", found.requestedUserAgent());
        assertEquals("DE", found.requestedCountry());

        assertTrue(service.lookup("WRONGCOD").isEmpty(), "a wrong code earns nothing, not even a reason");
        assertTrue(
                service.lookup(request.code().toLowerCase()).isPresent(),
                "a code typed in lower case is the same code");
    }

    @Test
    void approvalHandsOutTheEnrolmentTokenExactlyOnce() {
        var account = newAccount();
        int accountId = account.id();
        var request =
                service.createRequest(DeviceRequestPurpose.ENROL_PASSKEY, account.email(), "Chrome on Windows", "DE");

        assertEquals(
                DeviceRequestService.PollStatus.PENDING,
                poll(request.pollSecret()).status());
        assertEquals(
                DeviceRequestService.ApprovalResult.APPROVED,
                service.approve(accountId, accountId, request.code(), request.matchNumber()));
        assertEquals(
                DeviceRequestService.ApprovalResult.UNKNOWN,
                service.approve(accountId, accountId, request.code(), request.matchNumber()),
                "an approval happens exactly once");

        var first = poll(request.pollSecret());
        assertEquals(DeviceRequestService.PollStatus.APPROVED, first.status());
        assertNotNull(first.claimToken(), "the first poll after the approval carries the token");

        var second = poll(request.pollSecret());
        assertEquals(DeviceRequestService.PollStatus.APPROVED, second.status());
        assertNull(second.claimToken(), "the token is delivered exactly once");
    }

    /**
     * The claim happens at the finish, so a garbage ceremony burns the token rather than leaving it
     * spendable a second time. Failing closed is the direction that matters: the member asks for a
     * new code, instead of an attacker getting another try at one they already hold.
     */
    @Test
    void theEnrolmentTokenHasExactlyOnePower() {
        var account = newAccount();
        int accountId = account.id();
        var request =
                service.createRequest(DeviceRequestPurpose.ENROL_PASSKEY, account.email(), "Safari on iPhone", null);
        service.approve(accountId, accountId, request.code(), request.matchNumber());
        String enrollToken = poll(request.pollSecret()).claimToken();

        var ceremony = service.beginEnrollment(enrollToken).orElseThrow();
        assertNotNull(ceremony.optionsJson());

        assertFalse(service.finishEnrollment(enrollToken, ceremony.challengeToken(), "{}", null));
        assertFalse(
                service.finishEnrollment(enrollToken, ceremony.challengeToken(), "{}", null),
                "a spent token opens nothing");
        assertTrue(service.beginEnrollment(enrollToken).isEmpty(), "a spent token opens no ceremony either");
    }

    @Test
    void aFinishedEnrolmentMintsTheFactorAndMailsTheNotice() throws Exception {
        var account = newAccount();
        int accountId = account.id();
        var request =
                service.createRequest(DeviceRequestPurpose.ENROL_PASSKEY, account.email(), "Chrome on Android", "DE");
        service.approve(accountId, accountId, request.code(), request.matchNumber());
        String enrollToken = poll(request.pollSecret()).claimToken();
        var ceremony = service.beginEnrollment(enrollToken).orElseThrow();

        assertTrue(service.finishEnrollment(
                enrollToken,
                ceremony.challengeToken(),
                new TestAuthenticator().register(ceremony.optionsJson()),
                "DE"));
        assertTrue(
                twoFactorRepo.hasSignInPasskey(accountId), "the ceremony that verified left a sign-in passkey behind");
        verify(emailService).sendPasskeyDeviceApprovedNotice(any(), any(), any(), any(), any());
    }

    /**
     * A sign-in leaves nothing behind, so the mail must not say a passkey was made. Somebody told
     * otherwise would go hunting through their security page for a credential that is not there.
     */
    @Test
    void aVouchedSignInSaysSoRatherThanClaimingACredentialWasMade() {
        var account = newAccount();
        int accountId = account.id();
        when(authService.admitVouchedForAccount(anyInt(), any(), any()))
                .thenReturn(LoginResult.success("granted-token", Instant.now().plusSeconds(600)));

        var request = service.createRequest(
                DeviceRequestPurpose.SIGN_IN, account.email(), "Firefox on a borrowed laptop", "DE");
        service.approve(accountId, accountId, request.code(), request.matchNumber());
        service.claimSignIn(poll(request.pollSecret()).claimToken(), "Firefox on a borrowed laptop", "DE");

        verify(emailService).sendDeviceSignedInNotice(any(), any(), any(), any(), any());
        verify(emailService, never()).sendPasskeyDeviceApprovedNotice(any(), any(), any(), any(), any());
    }

    @Test
    void theRequestRemembersWhatItsApprovalWillBuy() {
        var request = service.createRequest(
                DeviceRequestPurpose.ENROL_PASSKEY, newAccount().email(), "Firefox on Linux", "DE");
        var found = service.lookup(request.code()).orElseThrow();

        assertEquals(DeviceRequestPurpose.ENROL_PASSKEY, found.purpose());
        assertTrue(found.is(DeviceRequestPurpose.ENROL_PASSKEY));
        assertNull(found.subjectAccountId(), "who the grant is for is settled at approval and not before");
        assertNull(found.requestingSessionId(), "a request an unidentified device raised binds no session");
    }

    @Test
    void approvalNamesBothTheApproverAndWhoTheGrantIsFor() {
        var account = newAccount();
        int accountId = account.id();
        var request =
                service.createRequest(DeviceRequestPurpose.ENROL_PASSKEY, account.email(), "Chrome on Windows", "DE");

        assertEquals(
                DeviceRequestService.ApprovalResult.APPROVED,
                service.approve(accountId, accountId, request.code(), request.matchNumber()));
        poll(request.pollSecret());

        var stored = deviceRepo.findByPollSecret(hashOf(request.pollSecret())).orElseThrow();
        assertEquals(accountId, stored.approvedAccountId());
        assertEquals(accountId, stored.subjectAccountId(), "a passkey enrolled this way belongs to whoever approved");
    }

    /**
     * The purpose is a guard and not a label. A token minted to create a passkey must be unable to
     * buy anything else, which is what keeps three grants safely on one table.
     */
    @Test
    void aTokenIsOnlySpendableOnThePurposeItWasMintedFor() {
        var account = newAccount();
        int accountId = account.id();
        var request =
                service.createRequest(DeviceRequestPurpose.ENROL_PASSKEY, account.email(), "Firefox on Linux", "DE");
        service.approve(accountId, accountId, request.code(), request.matchNumber());
        String claimToken = poll(request.pollSecret()).claimToken();

        assertTrue(
                deviceRepo
                        .findByClaimToken(hashOf(claimToken), DeviceRequestPurpose.ENROL_PASSKEY)
                        .isPresent(),
                "the purpose it was minted for opens it");
        assertTrue(
                deviceRepo
                        .findByClaimToken(hashOf(claimToken), DeviceRequestPurpose.SIGN_IN)
                        .isEmpty(),
                "another purpose does not, even holding the right token");
        assertTrue(
                deviceRepo
                        .claimByToken(hashOf(claimToken), DeviceRequestPurpose.SIGN_IN)
                        .isEmpty(),
                "and it cannot be spent on one either");
    }

    /**
     * A step-up is the one purpose raised by somebody already known, so the row records which
     * session asked and the database refuses a row that does not.
     */
    @Test
    void aStepUpRequestIsBoundToTheSessionThatRaisedIt() {
        int accountId = newAccount().id();
        String sessionToken = "session-" + UUID.randomUUID();
        accountRepo.createSession(accountId, sessionToken, Instant.now().plusSeconds(600), "ua", null);
        int sessionId = accountRepo.findSession(sessionToken).orElseThrow().id();

        String pollSecret = "poll-" + UUID.randomUUID();
        deviceRepo.createStepUp(
                "code-" + UUID.randomUUID(),
                pollSecret,
                accountId,
                sessionId,
                StepUpCategory.ACCOUNT_SECURITY,
                42,
                List.of(13, 27, 42, 58, 71, 86),
                "Firefox on Linux",
                "DE",
                Instant.now().plusSeconds(600));

        var stored = deviceRepo.findByPollSecret(pollSecret).orElseThrow();
        assertEquals(DeviceRequestPurpose.STEP_UP, stored.purpose());
        assertEquals(accountId, stored.requestingAccountId());
        assertEquals(sessionId, stored.requestingSessionId());
        assertEquals(StepUpCategory.ACCOUNT_SECURITY, stored.stepUpCategory());
    }

    /**
     * Minting the claim token is where the one-time delivery is spent, so a poll that reached the
     * wrong door must not reach the row at all. The unauthenticated door would otherwise hand a
     * step-up grant to whoever holds the secret, and the step-up door would mint a sign-in token and
     * throw it away, stranding a grant nobody can now claim.
     */
    @Test
    void aPollOnlyEverHandsOverWhatItsOwnDoorIsFor() {
        var account = newAccount();
        int accountId = account.id();
        String sessionToken = "session-" + UUID.randomUUID();
        accountRepo.createSession(accountId, sessionToken, Instant.now().plusSeconds(600), "ua", null);
        int sessionId = accountRepo.findSession(sessionToken).orElseThrow().id();

        var stepUp =
                service.createStepUpRequest(accountId, sessionId, StepUpCategory.ACCOUNT_SECURITY, "Firefox", "DE");
        service.approve(accountId, accountId, stepUp.code(), stepUp.matchNumber());

        assertEquals(
                DeviceRequestService.PollStatus.UNKNOWN,
                service.poll(
                                stepUp.pollSecret(),
                                Set.of(DeviceRequestPurpose.ENROL_PASSKEY, DeviceRequestPurpose.SIGN_IN))
                        .status(),
                "the unauthenticated door says nothing at all about a step-up");

        var signIn = service.createRequest(DeviceRequestPurpose.SIGN_IN, account.email(), "Chrome", "DE");
        service.approve(accountId, accountId, signIn.code(), signIn.matchNumber());
        assertEquals(
                DeviceRequestService.PollStatus.UNKNOWN,
                service.poll(signIn.pollSecret(), Set.of(DeviceRequestPurpose.STEP_UP))
                        .status(),
                "and the step-up door does not burn a sign-in's one delivery");
        assertNotNull(poll(signIn.pollSecret()).claimToken(), "which is why the sign-in is still there to be claimed");
    }

    @Test
    void aStepUpRowWithoutItsBindingIsRefusedByTheDatabase() {
        assertThrows(
                Exception.class,
                () -> deviceRepo.create(
                        DeviceRequestPurpose.STEP_UP,
                        "code-" + UUID.randomUUID(),
                        "poll-" + UUID.randomUUID(),
                        null,
                        42,
                        List.of(13, 27, 42, 58, 71, 86),
                        "ua",
                        "DE",
                        Instant.now().plusSeconds(600)),
                "a step-up that names no session is a stamp aimed at nobody");
    }

    /**
     * The whole of the sign-in purpose: a device that holds no credential and wants none is signed
     * in by one that is already trusted, and the claim buys that once.
     */
    @Test
    void aSignInClaimBuysOneSessionForTheAccountTheApprovalNamed() {
        var account = newAccount();
        int accountId = account.id();
        when(authService.admitVouchedForAccount(anyInt(), any(), any()))
                .thenReturn(LoginResult.success("granted-token", Instant.now().plusSeconds(600)));

        var request = service.createRequest(
                DeviceRequestPurpose.SIGN_IN, account.email(), "Firefox on a borrowed laptop", "DE");
        assertEquals(
                DeviceRequestService.ApprovalResult.APPROVED,
                service.approve(accountId, accountId, request.code(), request.matchNumber()));
        var poll = poll(request.pollSecret());

        assertEquals(DeviceRequestPurpose.SIGN_IN, poll.purpose(), "the asking device is told what it may claim");

        var signedIn = service.claimSignIn(poll.claimToken(), "Firefox on a borrowed laptop", "DE");
        assertTrue(signedIn.isPresent());
        assertEquals("granted-token", signedIn.orElseThrow().token());
        verify(authService).admitVouchedForAccount(eq(accountId), any(), any());

        assertTrue(
                service.claimSignIn(poll.claimToken(), "Firefox on a borrowed laptop", "DE")
                        .isEmpty(),
                "a spent claim buys nothing a second time");
    }

    @Test
    void aSignInClaimCannotBeSpentOnAnEnrolment() {
        var account = newAccount();
        int accountId = account.id();
        when(authService.admitVouchedForAccount(anyInt(), any(), any()))
                .thenReturn(LoginResult.success("granted-token", Instant.now().plusSeconds(600)));

        var request = service.createRequest(
                DeviceRequestPurpose.SIGN_IN, account.email(), "Chrome on a shared machine", null);
        service.approve(accountId, accountId, request.code(), request.matchNumber());
        String claimToken = poll(request.pollSecret()).claimToken();

        assertTrue(
                service.beginEnrollment(claimToken).isEmpty(),
                "a token that buys a session opens no credential ceremony");
    }

    /**
     * A step-up confirmed elsewhere stamps the asking session with the fact that it was elsewhere.
     * That is what stops the session going on to vouch for a third device: every chain of approvals
     * has to end in somebody proving themselves at a keyboard.
     */
    @Test
    void aConfirmedStepUpStampsTheAskingSessionAsVouchedForAndNotAsLocal() {
        int accountId = newAccount().id();
        String sessionToken = "session-" + UUID.randomUUID();
        accountRepo.createSession(accountId, sessionToken, Instant.now().plusSeconds(600), "ua", null);
        int sessionId = accountRepo.findSession(sessionToken).orElseThrow().id();

        var request =
                service.createStepUpRequest(accountId, sessionId, StepUpCategory.ACCOUNT_SECURITY, "Firefox", "DE");
        assertEquals(
                DeviceRequestService.ApprovalResult.APPROVED,
                service.approve(accountId, accountId, request.code(), request.matchNumber()));
        String claimToken = poll(request.pollSecret()).claimToken();

        assertTrue(service.claimStepUp(claimToken));

        var stamped = accountRepo.findSession(sessionToken).orElseThrow();
        assertNotNull(stamped.twoFactorVerifiedAt(), "the session is fresh again");
        assertEquals(StepUpProof.ANOTHER_DEVICE, stamped.twoFactorProof());
        assertFalse(stamped.twoFactorProof().isLocal(), "and it knows the proof was not given here");

        assertFalse(service.claimStepUp(claimToken), "a confirmation is spent exactly once");
    }

    /**
     * Ten minutes can pass between an approval and the poll that spends it. Somebody who ends every
     * session in that window has said stop, and the grant must not outlive the saying.
     */
    @Test
    void endingEverySessionVoidsAGrantThatWasApprovedButNotYetClaimed() {
        var account = newAccount();
        int accountId = account.id();
        when(authService.admitVouchedForAccount(anyInt(), any(), any()))
                .thenReturn(LoginResult.success("granted-token", Instant.now().plusSeconds(600)));

        var request = service.createRequest(
                DeviceRequestPurpose.SIGN_IN, account.email(), "Firefox on a borrowed laptop", "DE");
        service.approve(accountId, accountId, request.code(), request.matchNumber());
        String claimToken = poll(request.pollSecret()).claimToken();

        accountRepo.deleteSessionsByAccount(accountId);

        assertTrue(
                service.claimSignIn(claimToken, "Firefox on a borrowed laptop", "DE")
                        .isEmpty(),
                "the approval died with the sessions");
    }

    @Test
    void anUnapprovedTokenAndAnUnknownSecretOpenNothing() {
        var request = service.createRequest(
                DeviceRequestPurpose.ENROL_PASSKEY, newAccount().email(), "Edge on Windows", null);
        assertTrue(service.beginEnrollment("no-such-token").isEmpty());
        assertEquals(
                DeviceRequestService.PollStatus.UNKNOWN, poll("no-such-secret").status());
        assertEquals(
                DeviceRequestService.PollStatus.PENDING,
                poll(request.pollSecret()).status());
    }
}
