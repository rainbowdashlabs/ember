/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AccountEmailService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.AuthService.SetPasswordOutcome;
import dev.chojo.ember.feature.account.service.LoginNameService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ManagedLoginNoticeRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A guardian speaks for a child: they may give it an address, a name to sign in with, and switch its
 * access on and off. They may do none of it for anyone else.
 */
class ManagedAccessServiceTest extends RepositoryTestBase {

    private static ManagedAccessService service;
    private static AuthService authService;
    private static dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService enrollmentService;
    private static ManagedLoginNoticeRepository noticeRepo;
    private static Station station;
    private static Account guardianAccount;
    private static Account childAccount;
    private static Account strangerAccount;
    private static StationMember guardian;
    private static StationMember child;
    private static StationMember stranger;

    @BeforeAll
    static void setup() {
        var memberService = new StationMemberService(
                stationMemberRepo, stationRepo, accountRepo, mock(AuthService.class), mock(MemberLookupService.class));
        noticeRepo = new ManagedLoginNoticeRepository();
        authService = mock(AuthService.class);
        service = new ManagedAccessService(
                stationMemberRepo,
                accountRepo,
                new LoginNameService(accountRepo),
                memberService,
                new ManagedLoginNoticeService(
                        noticeRepo,
                        stationMemberRepo,
                        accountRepo,
                        stationRepo,
                        new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                        new MailRecipientService(accountRepo, stationMemberRepo),
                        mock(AuthService.class),
                        mock(EmailService.class),
                        new Auth()),
                authService,
                new AccountEmailService(
                        accountRepo,
                        new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                        mock(EmailService.class)),
                enrollmentService = mock(dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService.class));

        station = stationRepo.create("Managed Access Station");
        guardianAccount = accountRepo.create("guardian@test.com", "Petra", "Sommer");
        childAccount = accountRepo.create("child-1@managed.local", "Lena", "Sommer");
        strangerAccount = accountRepo.create("stranger@test.com", "Fremd", "Person");

        guardian = stationMemberRepo.create(station.id(), guardianAccount.id());
        child = stationMemberRepo.create(station.id(), childAccount.id());
        stranger = stationMemberRepo.create(station.id(), strangerAccount.id());
        stationMemberRepo.setUserType(guardian.id(), StationUserType.GUARDIAN);
        stationMemberRepo.setUserType(child.id(), StationUserType.MEMBER);
        stationMemberRepo.setUserType(stranger.id(), StationUserType.MEMBER);
        stationMemberRepo.addManager(guardian.id(), child.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(guardianAccount.id());
        accountRepo.delete(childAccount.id());
        accountRepo.delete(strangerAccount.id());
    }

    @BeforeEach
    void resetChild() {
        reset(authService);
        noticeRepo.cancel(child.id());
        accountRepo.updateUsername(childAccount.id(), null);
        accountRepo.updateEmail(childAccount.id(), "child-1@managed.local");
        stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(permission -> stationMemberRepo.revokePermission(child.id(), permission.id()));
    }

    @Test
    void aSyntheticAddressCountsAsNoAddress() {
        var access = service.get(guardian.id(), child.id());

        assertNull(access.email());
        assertFalse(access.canSignIn());
        assertFalse(access.loginEnabled());
    }

    @Test
    void theGuardianGivesTheChildAnAddress() {
        var access = service.setEmail(guardian.id(), child.id(), "  Lena@Example.ORG ");

        assertEquals("lena@example.org", access.email());
        assertTrue(access.canSignIn());
        assertEquals(
                "lena@example.org",
                accountRepo.findById(childAccount.id()).orElseThrow().email());
    }

    @Test
    void anAddressThatBelongsToSomeoneElseIsRefused() {
        assertThrows(BadRequestResponse.class, () -> service.setEmail(guardian.id(), child.id(), "stranger@test.com"));
    }

    @Test
    void nonsenseIsNotAnAddress() {
        assertThrows(BadRequestResponse.class, () -> service.setEmail(guardian.id(), child.id(), "keine-adresse"));
    }

    @Test
    void signingInNeedsAnAddressOrANameFirst() {
        assertThrows(BadRequestResponse.class, () -> service.setLogin(guardian.id(), child.id(), true));
    }

    @Test
    void accessIsSwitchedOnAndOffAgain() {
        service.setEmail(guardian.id(), child.id(), "lena@example.org");

        assertTrue(service.setLogin(guardian.id(), child.id(), true).loginEnabled());
        assertFalse(service.setLogin(guardian.id(), child.id(), false).loginEnabled());
    }

    @Test
    void switchingAccessOnLeavesTheMemberSomethingToBeTold() {
        service.setEmail(guardian.id(), child.id(), "lena@example.org");

        service.setLogin(guardian.id(), child.id(), true);

        var waiting = noticeRepo.find(child.id()).orElseThrow();
        assertTrue(waiting.granted());
    }

    @Test
    void switchingBackBeforeTheMailLeftTellsNobody() {
        service.setEmail(guardian.id(), child.id(), "lena@example.org");

        service.setLogin(guardian.id(), child.id(), true);
        service.setLogin(guardian.id(), child.id(), false);

        assertTrue(noticeRepo.find(child.id()).isEmpty());
    }

    @Test
    void switchingOnTwiceAroundAnUndoLeavesOneThingToBeTold() {
        service.setEmail(guardian.id(), child.id(), "lena@example.org");

        service.setLogin(guardian.id(), child.id(), true);
        service.setLogin(guardian.id(), child.id(), false);
        service.setLogin(guardian.id(), child.id(), true);

        var waiting = noticeRepo.find(child.id()).orElseThrow();
        assertTrue(waiting.granted());
    }

    @Test
    void aNameOfTheirOwnIsEnoughToSignInWithoutAnAddress() {
        var access = service.setUsername(guardian.id(), child.id(), "lena.sommer");

        assertEquals("lena.sommer", access.username());
        assertNull(access.email());
        assertTrue(access.canSignIn());
        assertTrue(service.setLogin(guardian.id(), child.id(), true).loginEnabled());
    }

    @Test
    void theNameThatIsTheOnlyWayInCannotBeTakenAway() {
        service.setUsername(guardian.id(), child.id(), "lena.sommer");

        assertThrows(BadRequestResponse.class, () -> service.setUsername(guardian.id(), child.id(), ""));
    }

    @Test
    void aNameIsGivenUpOnceThereIsAnAddress() {
        service.setUsername(guardian.id(), child.id(), "lena.sommer");
        service.setEmail(guardian.id(), child.id(), "lena@example.org");

        assertNull(service.setUsername(guardian.id(), child.id(), "").username());
    }

    @Test
    void aPasswordIsSetForAChildWithNoAddressOfTheirOwn() {
        when(authService.setPasswordFor(any(), eq("ein-gutes-passwort"))).thenReturn(SetPasswordOutcome.OK);
        service.setUsername(guardian.id(), child.id(), "lena.sommer");

        var access = service.setPassword(guardian.id(), child.id(), "ein-gutes-passwort");

        assertEquals("lena.sommer", access.username());
        verify(authService).setPasswordFor(any(), eq("ein-gutes-passwort"));
    }

    @Test
    void aChildWithAnAddressOfTheirOwnKeepsThatDoorToThemselves() {
        service.setEmail(guardian.id(), child.id(), "lena@example.org");

        assertThrows(
                ForbiddenResponse.class, () -> service.setPassword(guardian.id(), child.id(), "ein-gutes-passwort"));
    }

    @Test
    void aPasswordTheRulesRefuseIsRefusedHereToo() {
        when(authService.setPasswordFor(any(), eq("kurz"))).thenReturn(SetPasswordOutcome.PASSWORD_TOO_SHORT);

        assertThrows(BadRequestResponse.class, () -> service.setPassword(guardian.id(), child.id(), "kurz"));
    }

    @Test
    void anEmptyPasswordNeverReachesTheRules() {
        assertThrows(BadRequestResponse.class, () -> service.setPassword(guardian.id(), child.id(), " "));
        verify(authService, never()).setPasswordFor(any(), eq(" "));
    }

    @Test
    void aMemberTheyDoNotManageIsNoneOfTheirBusiness() {
        assertThrows(ForbiddenResponse.class, () -> service.get(guardian.id(), stranger.id()));
        assertThrows(ForbiddenResponse.class, () -> service.setEmail(guardian.id(), stranger.id(), "neu@example.org"));
        assertThrows(ForbiddenResponse.class, () -> service.setLogin(guardian.id(), stranger.id(), true));
    }

    @Test
    void aManagedMemberOfAnotherTypeIsRefused() {
        stationMemberRepo.setUserType(child.id(), StationUserType.TEAM);
        try {
            assertThrows(ForbiddenResponse.class, () -> service.setLogin(guardian.id(), child.id(), true));
        } finally {
            stationMemberRepo.setUserType(child.id(), StationUserType.MEMBER);
        }
    }

    @Test
    void theGuardianGetsAPasskeyCodeOnlyForAnAddresslessChild() {
        var issued = new dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService.IssuedCode(
                "CODE1234", "png", java.time.Instant.now());
        org.mockito.Mockito.when(enrollmentService.issueCodeWithQr(
                        org.mockito.ArgumentMatchers.eq(childAccount.id()), any(), any(), any(), any()))
                .thenReturn(issued);

        assertEquals(
                "CODE1234",
                service.issuePasskeyCode(guardian.id(), child.id(), guardianAccount.id(), "ua", null)
                        .code());

        service.revokePasskeyCode(guardian.id(), child.id());
        org.mockito.Mockito.verify(enrollmentService).revokeCode(childAccount.id());

        // With an address of their own the mail path is theirs, and the button is refused.
        service.setEmail(guardian.id(), child.id(), "lena-passkey@example.org");
        assertThrows(
                ForbiddenResponse.class,
                () -> service.issuePasskeyCode(guardian.id(), child.id(), guardianAccount.id(), "ua", null));
    }
}
