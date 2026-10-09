/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Email sending configuration including SMTP settings, authentication credentials,
 * sender identity, daily send limits, and notification digest intervals.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class Mailing {
    @Overwrite(env = @Env)
    private MailProviderType provider = MailProviderType.SMTP;

    @Overwrite(env = @Env)
    private MailSettings smtp = new MailSettings();

    @Overwrite(env = @Env)
    private String user = "";

    @Overwrite(env = @Env)
    private String password = "";

    @Overwrite(env = @Env)
    private String apiKey = "";

    @Overwrite(env = @Env)
    private String senderAddress = "";

    @Overwrite(env = @Env)
    private String senderName = "Ember";

    private Map<String, String> properties = Collections.emptyMap();

    @Overwrite(env = @Env)
    private int dailySendLimit = 200;

    @Overwrite(env = @Env)
    private int notificationDigestIntervalMinutes = 60;

    /**
     * The secret a mail provider must present to report delivery events. Empty switches the
     * webhook off, which is the default: an endpoint that accepts anything would let a stranger
     * mark mail as bounced.
     */
    @Overwrite(env = @Env)
    private String webhookSecret = "";

    /**
     * The providers mail is tried through, in order. The first is simply the first, not a provider
     * of a different kind.
     */
    private List<MailProviderEntry> providers = Collections.emptyList();

    /**
     * The shape this held before the providers became one list: the entries after the one written
     * directly on this element. Read so an instance that has not been saved since keeps sending,
     * and written no more; the first save through the administration page replaces both with
     * {@link #providers}.
     */
    private List<MailProviderEntry> fallbacks = Collections.emptyList();

    /**
     * How many attempts the provider configured here gets before the first fallback takes over.
     */
    @Overwrite(env = @Env)
    private int attempts = 2;

    /**
     * The signing secret Sweego issued for its webhook. Set it and every report from Sweego is
     * checked against it; leave it empty and the key in the address is what authorises a report,
     * as with the relays that do not sign at all.
     */
    @Overwrite(env = @Env)
    private String sweegoWebhookSecret = "";

    /**
     * The percentage of each instance provider's daily allowance that stations sending through the
     * instance may use together. The rest stays free for the instance's own mail.
     */
    @Overwrite(env = @Env)
    private int stationShare = 50;

    public MailProviderType provider() {
        return provider;
    }

    public MailSettings smtp() {
        return smtp;
    }

    public String apiKey() {
        return apiKey;
    }

    /**
     * Get the username for authentication.
     *
     * @return the username
     */
    public String user() {
        return user;
    }

    /**
     * Get the password for authentication.
     *
     * @return the password
     */
    public String password() {
        return password;
    }

    public String senderAddress() {
        return senderAddress;
    }

    /**
     * The secret that authorises a delivery-event report. Empty until Ember has generated one.
     */
    public String webhookSecret() {
        return webhookSecret;
    }

    /**
     * The providers mail is tried through, in order, from the top.
     *
     * <p>An instance written before the providers became one list has none of its own; its first
     * provider still stands in the fields on this element, with the rest behind {@code fallbacks}.
     * Both are folded into the same list here, so nothing has to be saved before it sends. A bare
     * configuration still names SMTP, so only a sender address says the old fields were ever filled
     * in; without one, an untouched instance would try to send through an empty host.
     */
    public List<MailProviderEntry> providers() {
        if (providers != null && !providers.isEmpty()) return providers;
        if (provider == null || provider == MailProviderType.NONE || senderAddress == null || senderAddress.isBlank()) {
            return Collections.emptyList();
        }
        List<MailProviderEntry> folded = new ArrayList<>();
        folded.add(new MailProviderEntry(
                provider,
                smtp.host(),
                smtp.port(),
                SmtpEncryption.fromLegacySsl(smtp.ssl()),
                user,
                password,
                apiKey,
                senderAddress,
                senderName,
                Math.max(1, attempts),
                dailySendLimit));
        if (fallbacks != null) folded.addAll(fallbacks);
        return folded;
    }

    /**
     * How many attempts the first provider gets before the chain moves on.
     */
    public int attempts() {
        return attempts;
    }

    /**
     * The signing secret Sweego issued, or empty when reports are not checked against one.
     */
    public String sweegoWebhookSecret() {
        return sweegoWebhookSecret;
    }

    /**
     * Sets the generated webhook secret.
     *
     * <p>Ember generates this itself on first start rather than asking an operator for it - nobody
     * needs another secret to look after - so unlike every other value here it is written from the
     * inside.
     */
    public void webhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String senderName() {
        return senderName;
    }

    /**
     * Get all mail properties including SMTP and IMAP settings.
     *
     * @return the properties
     */
    public int dailySendLimit() {
        return dailySendLimit;
    }

    public int notificationDigestIntervalMinutes() {
        return notificationDigestIntervalMinutes;
    }

    public void notificationDigestIntervalMinutes(int notificationDigestIntervalMinutes) {
        this.notificationDigestIntervalMinutes = notificationDigestIntervalMinutes;
    }

    /**
     * The percentage of each instance provider's daily allowance that stations may use together,
     * held between 0 and 100 whatever the file says.
     */
    public int stationShare() {
        return Math.clamp(stationShare, 0, 100);
    }

    public void stationShare(int stationShare) {
        this.stationShare = stationShare;
    }

    /**
     * Where mail goes, exactly as written here: the provider list and the fields the first
     * provider lived in before the providers became one list.
     */
    public Senders senders() {
        return new Senders(provider, smtp.host(), user, password, apiKey, senderAddress, providers, fallbacks);
    }

    /**
     * Replaces where mail goes, all of it at once.
     */
    public void senders(Senders senders) {
        provider = senders.provider();
        smtp.host(senders.host());
        user = senders.user();
        password = senders.password();
        apiKey = senders.apiKey();
        senderAddress = senders.senderAddress();
        providers = senders.providers();
        fallbacks = senders.fallbacks();
    }

    /**
     * Where mail goes, as one value, so a change to it is made and taken back in one piece.
     *
     * @param provider      the first provider in the shape written before the list
     * @param host          its host
     * @param user          its user
     * @param password      its password
     * @param apiKey        its API key
     * @param senderAddress its sender address, which also says whether it was ever filled in
     * @param providers     the provider list
     * @param fallbacks     the providers after the first in the shape written before the list
     */
    public record Senders(
            MailProviderType provider,
            String host,
            String user,
            String password,
            String apiKey,
            String senderAddress,
            List<MailProviderEntry> providers,
            List<MailProviderEntry> fallbacks) {

        /** Nowhere: the instance sends no mail. */
        public static final Senders NONE = new Senders(MailProviderType.NONE, "", "", "", "", "", List.of(), List.of());

        /**
         * These senders with the given provider list, which from then on says everything: the
         * providers written before the list are dropped with it.
         */
        public Senders withProviders(List<MailProviderEntry> list) {
            return new Senders(provider, host, user, password, apiKey, senderAddress, List.copyOf(list), List.of());
        }
    }

    /**
     * Builds a {@link Properties} object combining SMTP settings with any additional custom properties.
     *
     * @return the merged mail properties
     */
    public Properties properties() {
        Properties props = new Properties();
        props.putAll(smtp().properties("smtp"));
        props.putAll(properties);
        return props;
    }

    /**
     * What this holds, with the secrets reported as present rather than written out.
     *
     * <p>The configuration is logged once at start, and the log is kept in the database and read back
     * from the administration pages. Anything printed here is therefore readable by everybody who can
     * reach either, which no credential may be.
     */
    @Override
    public String toString() {
        return "Mailing{" + "smtp="
                + smtp + ", user='"
                + user + '\'' + ", passwordConfigured="
                + !password.isBlank() + ", apiKeyConfigured="
                + !apiKey.isBlank() + ", webhookSecretConfigured="
                + !webhookSecret.isBlank() + ", sweegoWebhookSecretConfigured="
                + !sweegoWebhookSecret.isBlank() + ", senderAddress='"
                + senderAddress + '\'' + ", senderName='"
                + senderName + '\'' + ", properties="
                + properties.keySet() + ", dailySendLimit="
                + dailySendLimit + ", stationShare="
                + stationShare + '}';
    }
}
