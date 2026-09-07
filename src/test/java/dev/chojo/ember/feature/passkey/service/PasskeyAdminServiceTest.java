/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.File;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.passkey.repository.PasskeyRepository;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The operator's decisions as pure logic: every collaborator is handed in, so each refusal and
 * each figure is asked about directly. The queries behind the figures are covered where they
 * live, in the repository's own test.
 */
class PasskeyAdminServiceTest {

    private Conf conf;
    private Auth auth;
    private PasskeyModeService modeService;
    private PasskeyRepository passkeyRepository;
    private EmailQueueRepository emailQueueRepository;
    private AccountRepository accountRepository;
    private TwoFactorAuditService auditService;
    private PasskeyAdminService service;

    @BeforeEach
    void setup() {
        auth = new Auth();
        File file = mock(File.class);
        when(file.auth()).thenReturn(auth);
        conf = mock(Conf.class);
        when(conf.main()).thenReturn(file);
        doNothing().when(conf).save();

        modeService = mock(PasskeyModeService.class);
        passkeyRepository = mock(PasskeyRepository.class);
        emailQueueRepository = mock(EmailQueueRepository.class);
        accountRepository = mock(AccountRepository.class);
        auditService = mock(TwoFactorAuditService.class);

        RelyingParty relyingParty = mock(RelyingParty.class);
        when(relyingParty.getIdentity())
                .thenReturn(RelyingPartyIdentity.builder()
                        .id("ember.test")
                        .name("Ember")
                        .build());

        service = new PasskeyAdminService(
                conf,
                modeService,
                passkeyRepository,
                emailQueueRepository,
                new RelyingParties(relyingParty, relyingParty, false),
                accountRepository,
                auditService);
    }

    @Test
    void theStatusCarriesTheModeTheReadinessAndTheFigures() {
        when(modeService.effectiveMode()).thenReturn(PasskeySettings.Mode.OPTIONAL);
        when(emailQueueRepository.findLastSentAt()).thenReturn(Optional.of(Instant.EPOCH));
        when(passkeyRepository.countAccountsDependingOnPasskey()).thenReturn(3);
        when(passkeyRepository.adoptionFigures()).thenReturn(new PasskeyRepository.AdoptionFigures(5, 40, 35));

        var status = service.status();

        assertEquals(PasskeySettings.Mode.OPTIONAL, status.configured());
        assertEquals("ember.test", status.rpId());
        assertEquals(Instant.EPOCH, status.lastMailSentAt());
        assertEquals(3, status.dependentAccounts());
        assertEquals(40, status.figures().accountsWithPassword());
        assertFalse(status.localhostFallback());
    }

    @Test
    void passwordlessIsRefusedUntilAMailHasProvenTheInstanceCanDeliver() {
        when(emailQueueRepository.findLastSentAt()).thenReturn(Optional.empty());

        var result = service.setMode(PasskeySettings.Mode.PASSWORDLESS);

        assertEquals(PasskeyAdminService.SetModeResult.Outcome.NO_MAIL_PROOF, result.outcome());
        assertEquals(PasskeySettings.Mode.OPTIONAL, auth.passkeys().mode(), "a refused change writes nothing");
    }

    @Test
    void goingBelowEncouragedIsRefusedWhileAnyAccountDependsOnAPasskey() {
        when(passkeyRepository.countAccountsDependingOnPasskey()).thenReturn(2);

        var result = service.setMode(PasskeySettings.Mode.OFF);

        assertEquals(PasskeyAdminService.SetModeResult.Outcome.ACCOUNTS_DEPEND, result.outcome());
        assertEquals(2, result.dependentAccounts());
    }

    @Test
    void anAllowedChangeIsWrittenAndSaved() {
        when(emailQueueRepository.findLastSentAt()).thenReturn(Optional.of(Instant.EPOCH));

        var result = service.setMode(PasskeySettings.Mode.PASSWORDLESS);

        assertEquals(PasskeyAdminService.SetModeResult.Outcome.OK, result.outcome());
        assertEquals(PasskeySettings.Mode.PASSWORDLESS, auth.passkeys().mode());
        verify(conf).save();
    }

    @Test
    void retiringTakesTheRopeOnlyFromSomebodyHoldingTheOtherOne() {
        when(accountRepository.findCredential(1)).thenReturn(Optional.empty());
        assertEquals(PasskeyAdminService.RetireOutcome.NO_PASSWORD, service.retirePassword(1, null, "ua", null));

        when(accountRepository.findCredential(2)).thenReturn(Optional.of(mock(AccountCredential.class)));
        when(passkeyRepository.hasTriedSignInPasskey(2)).thenReturn(false);
        assertEquals(PasskeyAdminService.RetireOutcome.NO_TRIED_PASSKEY, service.retirePassword(2, null, "ua", null));

        when(passkeyRepository.hasTriedSignInPasskey(3)).thenReturn(true);
        when(accountRepository.findCredential(3)).thenReturn(Optional.of(mock(AccountCredential.class)));
        assertEquals(PasskeyAdminService.RetireOutcome.RETIRED, service.retirePassword(3, 9, "ua", null));
        verify(accountRepository).deleteCredential(3);
    }

    @Test
    void theBulkFormActsOnTheEligibleAndCountsThePassedOver() {
        when(passkeyRepository.adoptionFigures()).thenReturn(new PasskeyRepository.AdoptionFigures(2, 5, 3));
        when(passkeyRepository.listRetireEligibleAccounts()).thenReturn(List.of(3, 4));
        when(accountRepository.findCredential(anyInt())).thenReturn(Optional.of(mock(AccountCredential.class)));
        when(passkeyRepository.hasTriedSignInPasskey(3)).thenReturn(true);
        when(passkeyRepository.hasTriedSignInPasskey(4)).thenReturn(true);

        var result = service.retireAllEligible(null, "ua", null);

        assertEquals(2, result.retired());
        assertEquals(3, result.passedOver());
    }

    @Test
    void theReportAndTheResidueComeStraightFromTheRepository() {
        when(passkeyRepository.passwordlessReport()).thenReturn(new PasskeyRepository.PasswordlessReport(4, 3, 2, 1));
        when(passkeyRepository.listResidue()).thenReturn(List.of());

        assertEquals(4, service.passwordlessReport().wouldKeepPassword());
        assertTrue(service.residue().isEmpty());
    }
}
