/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Service for GDPR-compliant account deletion and member anonymization.
 * Handles the right to erasure (Art. 17 GDPR) by deleting personal data
 * and anonymizing records that must be retained for business purposes.
 */
@Singleton
public class GdprDeletionService {
    private static final Logger log = getLogger(GdprDeletionService.class);
    private static final String ANONYMOUS = "Gelöscht";
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final ImageService imageService;

    @Inject
    public GdprDeletionService(
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            ImageService imageService) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.imageService = imageService;
    }

    /**
     * Deletes an account and anonymizes all associated station memberships.
     * This anonymizes each member record and then removes account-level data
     * (sessions, tokens, credentials, external auth, saved filters, and the account itself).
     *
     * @param accountId the account to delete
     */
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

    /**
     * Anonymizes a station member by deleting personal data (profile fields, notifications, settings,
     * group memberships, tags, comments) and replacing names in history records. The member is marked
     * as former and disconnected from the account.
     *
     * @param memberId the station member ID to anonymize
     */
    public void anonymizeMember(int memberId) {
        // Remember the account before we disconnect it
        var member = stationMemberRepository.findById(memberId).orElse(null);
        Integer accountId = member != null ? member.accountId() : null;

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

        // Anonymize news author (keep the content) — match by member UUID
        var memberUid = stationMemberRepository.resolveUid(memberId);
        if (memberUid != null) {
            Query.query(
                            "UPDATE news SET author_station_uid = NULL, author_member_uid = NULL WHERE author_member_uid = :uid::uuid;")
                    .single(Call.of().bind("uid", memberUid, StandardValueConverter.UUID_STRING))
                    .update();

            // Delete news comments by member UUID
            Query.query("DELETE FROM news_comment WHERE author_member_uid = :uid::uuid;")
                    .single(Call.of().bind("uid", memberUid, StandardValueConverter.UUID_STRING))
                    .delete();
        }

        // Delete profile field change data
        Query.query("DELETE FROM profile_field_change_acknowledgement WHERE acknowledged_by = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();
        Query.query("DELETE FROM profile_field_change WHERE member_id = :id;")
                .single(Call.of().bind("id", memberId))
                .delete();

        // Delete avatar from disk (keyed by member UUID)
        if (memberUid != null) {
            imageService.delete(ImageCategory.AVATARS, memberUid.toString());
        }

        // Mark as former and disconnect from account
        Query.query("UPDATE station_member SET former = TRUE, account_id = NULL WHERE id = :id;")
                .single(Call.of().bind("id", memberId))
                .update();

        // If the account has no remaining members, delete the account entirely
        if (accountId != null) {
            var remainingMembers = stationMemberRepository.findAllByAccountId(accountId);
            if (remainingMembers.isEmpty()) {
                log.info("GDPR: Account {} has no remaining members, deleting account", accountId);
                deleteAccountData(accountId);
            }
        }
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
