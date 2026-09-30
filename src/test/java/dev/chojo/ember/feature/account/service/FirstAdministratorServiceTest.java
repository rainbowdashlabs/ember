/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService;
import dev.chojo.ember.feature.passkey.service.PasskeyModeService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The administrator a fresh instance starts with, and the station it no longer starts with.
 */
class FirstAdministratorServiceTest extends RepositoryTestBase {

    private static FirstAdministratorService service(PasskeySettings.Mode mode) {
        var modes = mock(PasskeyModeService.class);
        when(modes.effectiveMode()).thenReturn(mode);
        var enrollment = mock(PasskeyEnrollmentService.class);
        when(enrollment.issueCode(anyInt(), any())).thenReturn("code");
        var api = mock(Api.class);
        when(api.baseUrl()).thenReturn("https://ember.example");
        return new FirstAdministratorService(accountRepo, new PasswordHasher(), modes, enrollment, api);
    }

    @Test
    void theFirstAdministratorComesWithoutAStation() {
        int stationsBefore = stationRepo.findAll().size();

        var admin = service(PasskeySettings.Mode.OPTIONAL).create();

        assertEquals(stationsBefore, stationRepo.findAll().size(), "bootstrap founds no station");
        assertTrue(stationMemberRepo.findByAccount(admin.id()).isEmpty(), "and belongs to none");
        assertEquals(InstanceUserType.ADMINISTRATOR, admin.instanceUserType());
        assertNull(admin.email(), "the address is asked for at the first sign-in");
        assertNotNull(accountRepo.findCredential(admin.id()).orElse(null), "a password to sign in with");
        accountRepo.delete(admin.id());
    }

    @Test
    void aPasswordlessInstanceGivesItsAdministratorNoPassword() {
        var admin = service(PasskeySettings.Mode.PASSWORDLESS).create();

        assertTrue(accountRepo.findCredential(admin.id()).isEmpty());
        accountRepo.delete(admin.id());
    }

    @Test
    void anInstanceThatHasAnAdministratorGetsNoOther() {
        var first = service(PasskeySettings.Mode.OPTIONAL).create();
        int accountsBefore = accountRepo.findAll().size();

        service(PasskeySettings.Mode.OPTIONAL).createIfMissing();

        assertEquals(accountsBefore, accountRepo.findAll().size());
        accountRepo.delete(first.id());
    }
}
