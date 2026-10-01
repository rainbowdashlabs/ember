/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import dev.chojo.ember.feature.station.entity.MailProviderType;
import jakarta.inject.Singleton;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Which delivery reports a provider has already handed over, by the id it gives each report.
 *
 * <p>A provider that does not hear back in time sends the same report again under the same id. The
 * id is a unique key, so of two copies arriving at once exactly one is claimed, and the other is
 * recognised as a repeat.
 */
@Singleton
public class MailWebhookReceiptRepository {

    /**
     * Claims a report for processing.
     *
     * @param provider  the provider that sent it
     * @param webhookId the id the provider gave it
     * @return true when the report is new, false when it was taken before
     */
    public boolean claim(MailProviderType provider, String webhookId) {
        return query("""
                        INSERT
                        INTO
                            mail_webhook_receipt(provider, webhook_id)
                        VALUES
                            (:provider, :webhook_id)
                        ON CONFLICT (provider, webhook_id) DO NOTHING;""")
                .single(call().bind("provider", provider).bind("webhook_id", webhookId))
                .insert()
                .changed();
    }

    /**
     * Gives a claimed report back, so a repeat of one that could not be processed is taken again.
     *
     * @param provider  the provider that sent it
     * @param webhookId the id the provider gave it
     */
    public void release(MailProviderType provider, String webhookId) {
        query("""
                        DELETE FROM
                            mail_webhook_receipt
                        WHERE
                            provider = :provider
                            AND webhook_id = :webhook_id;""")
                .single(call().bind("provider", provider).bind("webhook_id", webhookId))
                .delete();
    }

    /**
     * Forgets receipts older than any repeat a provider still sends.
     *
     * @param days how many days a receipt is kept
     * @return how many receipts were removed
     */
    public int prune(int days) {
        return query("""
                        DELETE FROM
                            mail_webhook_receipt
                        WHERE
                            received_at < now() - make_interval(days => :days);""").single(call().bind("days", days)).delete().rows();
    }
}
