/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.station.entity.MailProviderType;

/**
 * One provider in the order a mail is tried through.
 *
 * <p>A relay accepting a message is not the same as the message arriving, and a relay can stop
 * being usable for reasons that have nothing to do with us - its address ending up on somebody's
 * block list, for one. So sending is not one provider but a list: each gets a number of attempts,
 * and when it has used them the next one takes over.
 *
 * @param position       where in the order this provider sits, counted from zero
 * @param attempts       how many attempts it gets before the next one takes over
 * @param dailySendLimit how many mails it may send in a day, or zero for no limit. Free tiers are
 *                       sold by the day, so a chain that ignores the allowance keeps pushing at a
 *                       provider that has already spent it instead of moving to the next.
 * @param providerName   the provider name shown to members, empty when none was given
 * @param providerUrl    the provider website shown to members, empty when none was given
 */
public record MailChainEntry(
        int position,
        MailProviderType provider,
        String smtpHost,
        int smtpPort,
        boolean smtpSsl,
        String smtpUser,
        String smtpPassword,
        String apiKey,
        String senderAddress,
        String senderName,
        int attempts,
        int dailySendLimit,
        String providerName,
        String providerUrl) {

    /**
     * Whether this entry names a provider that could actually send something.
     */
    public boolean isConfigured() {
        return provider != null && provider != MailProviderType.NONE;
    }

    /**
     * Whether this entry has room for another mail today.
     *
     * @param sentToday what it has already sent
     */
    public boolean hasRoomToday(int sentToday) {
        return dailySendLimit <= 0 || sentToday < dailySendLimit;
    }

    public static RowMapping<MailChainEntry> map() {
        return row -> new MailChainEntry(
                row.getInt("position"),
                row.getEnum("provider", MailProviderType.class),
                row.getString("smtp_host"),
                row.getInt("smtp_port"),
                row.getBoolean("smtp_ssl"),
                row.getString("smtp_user"),
                row.getString("smtp_password"),
                row.getString("api_key"),
                row.getString("sender_address"),
                row.getString("sender_name"),
                row.getInt("attempts"),
                row.getInt("daily_limit"),
                row.getString("provider_name"),
                row.getString("provider_url"));
    }
}
