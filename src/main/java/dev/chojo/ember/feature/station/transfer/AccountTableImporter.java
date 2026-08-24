/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.feature.station.transfer.WireValues.asInteger;
import static dev.chojo.ember.feature.station.transfer.WireValues.asString;
import static dev.chojo.ember.feature.station.transfer.WireValues.asUuid;

/**
 * Imports the source's account rows, populating the {@code account} entry in the run's id map for
 * every row so downstream tables (notably {@code station_member}, group memberships, audit
 * pointers) can remap their {@code account_id} foreign key without relying on email lookups.
 *
 * <p>Matching rules:
 * <ul>
 *   <li>A row whose source {@code email} is non-blank and already exists on the destination
 *       reuses that destination account - this is the "same human, different instance" merge
 *       case. The destination keeps its own UID.</li>
 *   <li>Every other row (blank email, or non-blank email with no destination match) creates a
 *       fresh destination account. Blank-email rows are intentional in this product - youth
 *       too young to have an address still need a member record.</li>
 * </ul>
 *
 * <p>Newly-created accounts try to preserve the source UID so UID-typed columns elsewhere
 * (e.g. {@code author_account_uid} on comments) round-trip without remap. A unique-constraint
 * collision on UID is rare; when it happens we accept the auto-generated UID and move on.
 *
 * <p>The name an account signs in with travels with it, and is dropped when the destination already
 * has it: two instances name their people independently, and the one already here was not asked. The
 * address is then the login name again, which is what it is for everybody who never had a name. An
 * account that has no address either arrives with no way in at all, and is logged as such, because
 * inventing a name nobody was told about would be worse than saying so.
 */
@Singleton
public class AccountTableImporter implements TableImporter {
    private static final Logger log = LoggerFactory.getLogger(AccountTableImporter.class);
    private final AccountRepository accountRepository;

    @Inject
    public AccountTableImporter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public String table() {
        return "account";
    }

    @Override
    @SuppressWarnings("unchecked")
    public int importRows(StationImportContext context, Object payload) {
        int created = 0;
        for (var row : (List<Map<String, Object>>) payload) {
            Integer sourceId = asInteger(row.get("id"));
            String rawEmail = asString(row.get("email"), null);
            boolean hasEmail = rawEmail != null && !rawEmail.isBlank();
            String storedEmail = hasEmail ? rawEmail : null;
            int targetId;
            var existing = hasEmail ? accountRepository.findByEmail(rawEmail) : Optional.<Account>empty();
            if (existing.isPresent()) {
                targetId = existing.get().id();
            } else {
                String first = asString(row.get("first_name"), "");
                String last = asString(row.get("last_name"), "");
                var newAccount = accountRepository.create(storedEmail, first, last, true, context.stationId());
                targetId = newAccount.id();
                UUID sourceUid = asUuid(row.get("uid"));
                UUID destinationUid = newAccount.uid();
                if (sourceUid != null && !sourceUid.equals(destinationUid)) {
                    try {
                        accountRepository.setUid(newAccount.id(), sourceUid);
                        destinationUid = sourceUid;
                    } catch (Exception e) {
                        log.warn(
                                "Source account UID {} already used on destination; keeping generated UID {}",
                                sourceUid,
                                destinationUid);
                    }
                }
                if (sourceUid != null) {
                    context.addNewAccount(sourceUid, destinationUid);
                }
                carryUsername(newAccount.id(), asString(row.get("username"), null), storedEmail);
                created++;
            }
            if (sourceId != null) context.idMap().put("account", sourceId, targetId);
        }
        return created;
    }

    /**
     * Gives the arriving account the name it signed in with at home, unless somebody here already
     * carries it.
     */
    private void carryUsername(int accountId, String username, String email) {
        if (username == null || username.isBlank()) return;
        if (accountRepository.usernameTaken(username, accountId)) {
            if (email == null) {
                log.warn(
                        "Account {} arrives with the username '{}' already taken here and no address of its own; it has no way to sign in until a new name is set",
                        accountId,
                        username);
            } else {
                log.info(
                        "Username '{}' is already taken here; account {} keeps its address as login name",
                        username,
                        accountId);
            }
            return;
        }
        accountRepository.updateUsername(accountId, username);
    }
}
