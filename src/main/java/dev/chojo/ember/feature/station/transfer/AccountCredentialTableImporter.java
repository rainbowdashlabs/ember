/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.passkey.service.PasskeyModeService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static dev.chojo.ember.feature.station.transfer.WireValues.asString;

/**
 * For each transferred credential, locates the matching target account by email and installs the
 * source password hash plus a forced password change, but only when no credential exists yet.
 * Existing target credentials are never overwritten.
 *
 * <p>Two things deliberately do not travel. Passkeys cannot: a WebAuthn credential is bound to
 * the rpId it was created for, so rows imported here would promise a way in that no
 * authenticator will ever answer. And {@code password_login_disabled_at} does not either: the
 * credential row is created fresh, so an account that had switched its password sign-in off
 * arrives with it switched on, hash and forced rotation included, which is the rope for exactly
 * those members.
 *
 * <p>On a passwordless target instance no hash is installed at all, and the count of skipped
 * rows is said out loud rather than swallowed: those members are onboarded again on this side,
 * which is what the import's own re-onboarding mails set going.
 */
@Singleton
public class AccountCredentialTableImporter implements TableImporter {
    private static final Logger log = LoggerFactory.getLogger(AccountCredentialTableImporter.class);
    private final AccountRepository accountRepository;
    private final PasskeyModeService passkeyModeService;

    @Inject
    public AccountCredentialTableImporter(AccountRepository accountRepository, PasskeyModeService passkeyModeService) {
        this.accountRepository = accountRepository;
        this.passkeyModeService = passkeyModeService;
    }

    @Override
    public String table() {
        return "account_credential";
    }

    @Override
    @SuppressWarnings("unchecked")
    public int importRows(StationImportContext context, Object payload) {
        boolean passwordless = passkeyModeService.effectiveMode() == PasskeySettings.Mode.PASSWORDLESS;
        int installed = 0;
        int skippedPasswordless = 0;
        for (var row : (List<Map<String, Object>>) payload) {
            String email = asString(row.get("account_email"), null);
            String hash = asString(row.get("password_hash"), null);
            if (email == null || hash == null) continue;
            var account = accountRepository.findByEmail(email);
            if (account.isEmpty()) continue;
            int accountId = account.get().id();
            if (accountRepository.findCredential(accountId).isPresent()) continue;
            if (passwordless) {
                skippedPasswordless++;
                continue;
            }
            accountRepository.createCredential(accountId, hash);
            accountRepository.setForcePasswordChange(accountId, true);
            log.info("Imported credential for account {} ({}) with forced password change", accountId, email);
            installed++;
        }
        if (skippedPasswordless > 0) {
            log.info(
                    "Skipped {} credential row(s): this instance is passwordless, and those members are onboarded again instead",
                    skippedPasswordless);
        }
        return installed;
    }
}
