/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.account.repository.AccountRepository;
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
 */
@Singleton
public class AccountCredentialTableImporter implements TableImporter {
    private static final Logger log = LoggerFactory.getLogger(AccountCredentialTableImporter.class);
    private final AccountRepository accountRepository;

    @Inject
    public AccountCredentialTableImporter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public String table() {
        return "account_credential";
    }

    @Override
    @SuppressWarnings("unchecked")
    public int importRows(StationImportContext context, Object payload) {
        int installed = 0;
        for (var row : (List<Map<String, Object>>) payload) {
            String email = asString(row.get("account_email"), null);
            String hash = asString(row.get("password_hash"), null);
            if (email == null || hash == null) continue;
            var account = accountRepository.findByEmail(email);
            if (account.isEmpty()) continue;
            int accountId = account.get().id();
            if (accountRepository.findCredential(accountId).isPresent()) continue;
            accountRepository.createCredential(accountId, hash);
            accountRepository.setForcePasswordChange(accountId, true);
            log.info("Imported credential for account {} ({}) with forced password change", accountId, email);
            installed++;
        }
        return installed;
    }
}
