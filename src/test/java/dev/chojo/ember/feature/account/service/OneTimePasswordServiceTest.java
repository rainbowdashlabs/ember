/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.refusal.AdminRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.auth.BreachCheckWorker;
import dev.chojo.ember.auth.HibpClient;
import dev.chojo.ember.auth.OneTimePasswords;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.IssuedOneTimePassword;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailConfirmationPolicy;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.service.TrustedDeviceService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One-time passwords against the database: who may be given one from a station, what the first
 * sign-in with one asks, and what is left behind.
 */
class OneTimePasswordServiceTest extends RepositoryTestBase {
    private static final Pattern SHAPE =
            Pattern.compile("[%1$s]{4}-[%1$s]{4}-[%1$s]{4}-[%1$s]{4}".formatted(OneTimePasswords.ALPHABET));
    private static final String CHOSEN = "ChosenByThePerson-2026";

    private static AuthService auth;
    private static TwoFactorRepository twoFactorRepository;
    private static TwoFactorAuditService audit;

    private Station station;
    private Account administrator;
    private Account target;
    private OneTimePasswordService service;

    @BeforeAll
    static void services() {
        twoFactorRepository = new TwoFactorRepository();
        audit = new TwoFactorAuditService(twoFactorRepository);
        var hibp = mock(HibpClient.class);
        when(hibp.isPwned(anyString())).thenReturn(false);
        var confirmation = mock(MailConfirmationPolicy.class);
        var locales = new MailLocaleService(accountRepo, new ApplicationSettingRepository());
        var mail = mock(EmailService.class);
        auth = new AuthService(
                accountRepo,
                new AccountEmailService(accountRepo, locales, mail),
                confirmation,
                locales,
                new MailRecipientService(accountRepo, stationMemberRepo),
                registrationCodeRepo,
                stationMemberRepo,
                newGroupMemberships(),
                new PasswordHasher(),
                mail,
                new Auth(),
                new Demo(),
                hibp,
                mock(BreachCheckWorker.class),
                twoFactorRepository,
                new TrustedDeviceService(
                        twoFactorRepository, TokenHasher.forTesting("otp-pepper"), new TwoFactorSettings()),
                passkeyModeService);
    }

    @BeforeEach
    void station() {
        station = stationRepo.create("One-time " + System.nanoTime());
        administrator = account("admin");
        target = account("target");
        stationMemberRepo.create(station.id(), administrator.id());
        stationMemberRepo.create(station.id(), target.id());
        service = new OneTimePasswordService(accountRepo, stationMemberRepo, auth, audit);
    }

    private static Account account(String label) {
        return accountRepo.create("otp-" + label + "-" + System.nanoTime() + "@test.com", "Lena", "Weber", true);
    }

    private IssuedOneTimePassword issueHere() {
        return service.issueForStation(station.id(), administrator.id(), target.id(), "agent", "DE");
    }

    private static void assertRefused(Refusal expected, Executable call) {
        var refused = assertThrows(RefusalResponse.class, call);
        assertEquals(expected, refused.refusal());
    }

    @Test
    void theIssuedPasswordIsShownInFourGroupsWithTheNameToTypeAndASevenDayDeadline() {
        Instant before = Instant.now();

        var issued = issueHere();

        assertTrue(SHAPE.matcher(issued.password()).matches(), issued.password());
        assertEquals(target.email(), issued.loginName());
        assertEquals("Lena Weber", issued.name());
        assertFalse(issued.expiresAt().isBefore(before.plus(7, ChronoUnit.DAYS)));
        assertTrue(issued.expiresAt().isBefore(before.plus(7, ChronoUnit.DAYS).plusSeconds(60)));
    }

    @Test
    void theFirstSignInAsksForANewPasswordAndTheOneTimePasswordWorksOnlyUntilThen() {
        var issued = issueHere();

        var first = auth.login(target.email(), issued.password(), "agent", "DE");
        assertTrue(first.passwordChangeRequired(), "the first sign-in asks for a new password");
        assertFalse(first.isSession());

        var chosen = auth.setPasswordAndSignIn(first.token(), CHOSEN, "agent", "DE");
        assertEquals(AuthService.SetPasswordOutcome.OK, chosen.outcome());
        assertTrue(chosen.login().isSession(), "a session follows the new password");

        assertFalse(auth.login(target.email(), issued.password(), "agent", "DE").success());
        assertTrue(auth.login(target.email(), CHOSEN, "agent", "DE").isSession());
        assertNull(accountRepo.findCredential(target.id()).orElseThrow().oneTimePasswordExpiresAt());
    }

    @Test
    void anExpiredOneTimePasswordIsRefusedWithItsOwnReason() {
        var lastWeek = Clock.fixed(Instant.now().minus(8, ChronoUnit.DAYS), ZoneOffset.UTC);
        var issued = new OneTimePasswordService(accountRepo, stationMemberRepo, auth, audit, lastWeek)
                .issueForStation(station.id(), administrator.id(), target.id(), null, null);

        var login = auth.login(target.email(), issued.password(), "agent", "DE");

        assertFalse(login.success());
        assertTrue(login.oneTimePasswordExpired());
        assertFalse(
                auth.login(target.email(), "not-the-password", "agent", "DE").oneTimePasswordExpired());
    }

    @Test
    void issuingEndsEverySessionAndIsRecorded() {
        accountRepo.createSession(
                target.id(), "otp-session-" + System.nanoTime(), Instant.now().plusSeconds(600), "a", "DE");

        issueHere();

        assertTrue(accountRepo.findSessionsByAccount(target.id()).isEmpty());
        var row = twoFactorRepository.findAuditLog(target.id(), 10, 0).getFirst();
        assertEquals(TwoFactorEvent.ONE_TIME_PASSWORD_ISSUED, row.event());
        assertEquals(administrator.id(), row.actorId());
        assertEquals(station.id(), row.stationId());
    }

    @Test
    void anInstanceAdministratorIssuesFromNoStation() {
        var elsewhere = stationRepo.create("One-time elsewhere " + System.nanoTime());
        stationMemberRepo.create(elsewhere.id(), target.id());

        var issued = service.issueForInstance(administrator.id(), target.id(), "agent", "DE");

        assertTrue(auth.login(target.email(), issued.password(), "agent", "DE").passwordChangeRequired());
        assertNull(
                twoFactorRepository.findAuditLog(target.id(), 10, 0).getFirst().stationId());
    }

    @Test
    void aStationAdministratorIsRefusedForAnAccountThatIsAMemberElsewhere() {
        var elsewhere = stationRepo.create("One-time elsewhere " + System.nanoTime());
        stationMemberRepo.create(elsewhere.id(), target.id());

        assertRefused(MemberRefusal.ONE_TIME_PASSWORD_FOR_SHARED_ACCOUNT, this::issueHere);
    }

    @Test
    void aStationAdministratorIsRefusedForAnAccountThatWasOnceAMemberElsewhere() {
        var elsewhere = stationRepo.create("One-time former " + System.nanoTime());
        var former = stationMemberRepo.create(elsewhere.id(), target.id());
        stationMemberRepo.setFormer(former.id(), true);

        assertRefused(MemberRefusal.ONE_TIME_PASSWORD_FOR_SHARED_ACCOUNT, this::issueHere);
    }

    @Test
    void aStationAdministratorIsRefusedForAnAccountWithAnAssociationRole() {
        var association = clusterRepo.create("One-time association " + System.nanoTime(), null, station.id());
        clusterRepo.addMember(association.id(), target.id(), ClusterUserType.CLUSTER_USER);

        assertRefused(MemberRefusal.ONE_TIME_PASSWORD_FOR_ASSOCIATION_ACCOUNT, this::issueHere);
    }

    @Test
    void aStationAdministratorIsRefusedForAnInstanceAdministrator() {
        accountRepo.setInstanceUserType(target.id(), InstanceUserType.ADMINISTRATOR);

        assertRefused(MemberRefusal.ONE_TIME_PASSWORD_FOR_INSTANCE_ADMINISTRATOR, this::issueHere);
    }

    @Test
    void nobodyIssuesOneForThemselves() {
        assertRefused(
                MemberRefusal.ONE_TIME_PASSWORD_FOR_YOURSELF,
                () -> service.issueForStation(station.id(), administrator.id(), administrator.id(), null, null));
        assertRefused(
                AdminRefusal.ONE_TIME_PASSWORD_FOR_YOURSELF,
                () -> service.issueForInstance(administrator.id(), administrator.id(), null, null));
    }

    @Test
    void anAccountOfAnotherStationOrNoneAtAllIsNotFound() {
        var stranger = account("stranger");

        assertRefused(
                MemberRefusal.ACCOUNT_NOT_HERE_ON_ONE_TIME_PASSWORD,
                () -> service.issueForStation(station.id(), administrator.id(), stranger.id(), null, null));
        assertRefused(
                AdminRefusal.ACCOUNT_NOT_HERE_ON_ONE_TIME_PASSWORD,
                () -> service.issueForInstance(administrator.id(), Integer.MAX_VALUE, null, null));
    }

    @Test
    void aPasswordlessInstanceIsNamedAtEitherDoor() {
        var passwordless = mock(AuthService.class);
        when(passwordless.issueOneTimePassword(any(), anyString(), any()))
                .thenReturn(AuthService.SetPasswordOutcome.PASSWORDLESS_MODE);
        var refusing = new OneTimePasswordService(accountRepo, stationMemberRepo, passwordless, audit);

        assertRefused(
                MemberRefusal.ONE_TIME_PASSWORD_PASSWORDS_SWITCHED_OFF,
                () -> refusing.issueForStation(station.id(), administrator.id(), target.id(), null, null));
        assertRefused(
                AdminRefusal.ONE_TIME_PASSWORD_PASSWORDS_SWITCHED_OFF,
                () -> refusing.issueForInstance(administrator.id(), target.id(), null, null));
    }
}
