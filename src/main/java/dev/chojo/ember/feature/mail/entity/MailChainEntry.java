/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import org.jspecify.annotations.Nullable;

/**
 * One provider in the order a mail is tried through.
 *
 * <p>A relay accepting a message is not the same as the message arriving, and a relay can stop
 * being usable for reasons that have nothing to do with us - its address ending up on somebody's
 * block list, for one. So sending is not one provider but a list: each gets a number of attempts,
 * and when it has used them the next one takes over.
 *
 * @param position       where in the order this provider sits, counted from zero
 * @param smtpEncryption how the connection to the relay is secured
 * @param attempts       how many attempts it gets before the next one takes over
 * @param dailySendLimit how many mails it may send in a day, or zero for no limit. Free tiers are
 *                       sold by the day, so a chain that ignores the allowance keeps pushing at a
 *                       provider that has already spent it instead of moving to the next.
 * @param providerName     the provider name shown to members, empty when none was given
 * @param providerUrl      the provider website shown to members, empty when none was given
 * @param instancePosition where this provider sits in the instance's list when it is one of the
 *                         instance's, or null for a station's own provider. A station granted the
 *                         instance's providers has them after its own, so its chain holds both
 *                         kinds, and the allowance of an instance provider is counted by this
 *                         position whoever's mail it carries.
 */
public record MailChainEntry(
        int position,
        MailProviderType provider,
        String smtpHost,
        int smtpPort,
        SmtpEncryption smtpEncryption,
        String smtpUser,
        String smtpPassword,
        String apiKey,
        String senderAddress,
        String senderName,
        int attempts,
        int dailySendLimit,
        String providerName,
        String providerUrl,
        @Nullable Integer instancePosition) {

    /**
     * A station's own provider, which is what every entry read from a station's list is.
     */
    public MailChainEntry(
            int position,
            MailProviderType provider,
            String smtpHost,
            int smtpPort,
            SmtpEncryption smtpEncryption,
            String smtpUser,
            String smtpPassword,
            String apiKey,
            String senderAddress,
            String senderName,
            int attempts,
            int dailySendLimit,
            String providerName,
            String providerUrl) {
        this(
                position,
                provider,
                smtpHost,
                smtpPort,
                smtpEncryption,
                smtpUser,
                smtpPassword,
                apiKey,
                senderAddress,
                senderName,
                attempts,
                dailySendLimit,
                providerName,
                providerUrl,
                null);
    }

    /**
     * Whether this entry names a provider that could actually send something.
     */
    public boolean isConfigured() {
        return provider != null && provider != MailProviderType.NONE;
    }

    /**
     * Whether this is one of the instance's providers rather than a station's own.
     */
    public boolean isInstanceProvider() {
        return instancePosition != null;
    }

    /**
     * This entry at another place in the order it is tried through.
     */
    public MailChainEntry withPosition(int newPosition) {
        return new MailChainEntry(
                newPosition,
                provider,
                smtpHost,
                smtpPort,
                smtpEncryption,
                smtpUser,
                smtpPassword,
                apiKey,
                senderAddress,
                senderName,
                attempts,
                dailySendLimit,
                providerName,
                providerUrl,
                instancePosition);
    }

    /**
     * This entry marked as the instance's provider at the given place of the instance's list.
     */
    public MailChainEntry asInstanceProvider(int inInstanceList) {
        return new MailChainEntry(
                position,
                provider,
                smtpHost,
                smtpPort,
                smtpEncryption,
                smtpUser,
                smtpPassword,
                apiKey,
                senderAddress,
                senderName,
                attempts,
                dailySendLimit,
                providerName,
                providerUrl,
                inInstanceList);
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
                row.getEnum("smtp_encryption", SmtpEncryption.class),
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
