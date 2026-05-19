/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

@Singleton
public class GdprDeletionService {
    private static final Logger log = getLogger(GdprDeletionService.class);
    private static final String ANONYMOUS = "Gelöscht";
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public GdprDeletionService(AccountRepository accountRepository, StationMemberRepository stationMemberRepository) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    public void deleteAccount(int accountId) {
        log.info("GDPR: Starting account deletion for account {}", accountId);

        var members = stationMemberRepository.findAllByAccountId(accountId);

        for (var member : members) {
            anonymizeMember(member.id());
        }

        // Delete account-level data
        deleteAccountData(accountId);

        log.info("GDPR: Account {} deleted and {} memberships anonymized", accountId, members.size());
    }

    private void anonymizeMember(int memberId) {
        // Delete profile field values
        Query.query("DELETE FROM profile_field_value WHERE member_id = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();

        // Delete notification settings
        Query.query("DELETE FROM user_notification_settings WHERE member_id = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();
        Query.query("DELETE FROM user_settings WHERE member_id = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();

        // Delete notifications
        Query.query("DELETE FROM notification WHERE member_id = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();

        // Remove manager relationships
        Query.query("DELETE FROM member_manager WHERE manager_id = :id OR managed_id = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();

        // Remove group memberships
        Query.query("DELETE FROM member_group_entry WHERE member_id = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();

        // Remove tag assignments
        Query.query("DELETE FROM user_tag_entry WHERE member_id = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();

        // Anonymize inventory item history
        Query.query("UPDATE inventory_item_history SET member_name = :anon WHERE member_id = :id;")
                .single(Call.of().bind("anon", ANONYMOUS).bind("id", memberId))
                .update();

        // Unassign inventory items
        Query.query("UPDATE inventory_item SET assigned_to = NULL WHERE assigned_to = :id;")
                .single(Call.of().bind("id", memberId))
                .update();

        // Anonymize news author names (keep the content)
        Query.query("UPDATE news SET author_id = NULL WHERE author_id = :id;")
                .single(Call.of().bind("id", memberId))
                .update();

        // Delete news comments
        Query.query("DELETE FROM news_comment WHERE author_id = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();

        // Delete profile field change data
        Query.query("DELETE FROM profile_field_change_acknowledgement WHERE acknowledged_by = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();
        Query.query("DELETE FROM profile_field_change WHERE member_id = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();

        // Mark as former and disconnect from account
        Query.query("UPDATE station_member SET former = TRUE, account_id = NULL WHERE id = :id;")
                .single(Call.of().bind("id", memberId))
                .update();
    }

    private void deleteAccountData(int accountId) {
        // Delete sessions
        accountRepository.deleteSessionsByAccount(accountId);

        // Delete tokens
        Query.query("DELETE FROM account_token WHERE account_id = :id;")
                .single(Call.of().bind("id", accountId))
                .delete();

        // Delete credentials
        accountRepository.deleteCredential(accountId);

        // Delete external auth
        Query.query("DELETE FROM account_external_auth WHERE account_id = :id;")
                .single(Call.of().bind("id", accountId))
                .delete();

        // Delete saved filters
        Query.query("DELETE FROM saved_filter WHERE account_id = :id;")
                .single(Call.of().bind("id", accountId))
                .delete();

        // Delete the account itself
        accountRepository.delete(accountId);
    }
}
