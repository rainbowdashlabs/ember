/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailAttachment;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.entity.SignatureInvitation;
import dev.chojo.ember.feature.mail.entity.SignedCopy;
import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import dev.chojo.ember.feature.mail.entity.WaitlistInvitationDetails;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.MailProviderBlockRepository;
import dev.chojo.ember.feature.mail.service.mail.MailProvider;
import dev.chojo.ember.feature.mail.service.mail.SmtpMailProvider;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central email service handling both global system emails and per-station notification emails.
 * Uses a queued architecture with a background worker that processes pending emails every 10 seconds.
 * Supports multiple mail providers (SMTP, Rapidmail, Twilio SendGrid, Sweego, Brevo) and holds every
 * provider to its daily send limit, as {@link MailAllowance} counts it: per provider of a station's
 * own list, per instance provider for everybody together, and for stations sending through the
 * instance's providers also to their share of each and to their own daily limit.
 */
@Singleton
public class EmailService implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * The header Brevo passes through from a sent message to every delivery event it reports about
     * it. Brevo assigns its own message id on the relay and never tells us what it was, so this is
     * the only thing that ties an event back to the mail it belongs to.
     */
    private static final String BREVO_CORRELATION_HEADER = "X-Mailin-custom";

    /**
     * SendGrid carries arbitrary values through to its events as well, but expects them inside a
     * small JSON document rather than as a plain header value. What comes back is a field named
     * after the key, which is why the route reads {@code ember_id}.
     */
    private static final String SENDGRID_CORRELATION_HEADER = "X-SMTPAPI";

    private static final String SENDGRID_CORRELATION_FORMAT = "{\"unique_args\":{\"ember_id\":\"%s\"}}";

    /**
     * Sweego returns the headers of a message inside every event it reports about it, and names
     * this one as the slot a sender may put its own value in.
     */
    private static final String SWEEGO_CORRELATION_HEADER = "X-Custom-Header";

    private final Mailing mailing;
    private final Api api;
    private final Demo demoConfig;
    private final EmailQueueRepository queueRepository;
    private final MailTemplateRenderer templateRenderer;
    private final StationReadOnlyGuard readOnlyGuard;
    private final MailChainService chainService;
    private final MailProviderBlockRepository blockRepository;
    private final MailRetryService retryService;
    private final MailAllowance allowance;

    @Inject
    public EmailService(
            Mailing mailing,
            Api api,
            Demo demoConfig,
            EmailQueueRepository queueRepository,
            MailTemplateRenderer templateRenderer,
            StationReadOnlyGuard readOnlyGuard,
            MailChainService chainService,
            MailProviderBlockRepository blockRepository,
            MailRetryService retryService,
            MailAllowance allowance) {
        this.allowance = allowance;
        this.chainService = chainService;
        this.blockRepository = blockRepository;
        this.retryService = retryService;
        this.mailing = mailing;
        this.api = api;
        this.demoConfig = demoConfig;
        this.queueRepository = queueRepository;
        this.templateRenderer = templateRenderer;
        this.readOnlyGuard = readOnlyGuard;
        var instanceChain = chainService.forInstance();
        if (instanceChain.isEmpty()) {
            log.warn(
                    "Mail service starting without a global mail provider; transactional emails will not be delivered until one is configured");
        } else {
            var first = instanceChain.getFirst();
            log.info(
                    "Mail service initialized: {} provider(s), first={} sender={} dailyLimit={}",
                    instanceChain.size(),
                    first.provider(),
                    first.senderAddress(),
                    first.dailySendLimit());
        }
    }

    private void runCleanup() {
        try {
            queueRepository.cleanupOldEntries(30);
            log.debug("Email queue cleanup completed");
        } catch (Exception e) {
            log.error("Email queue cleanup failed", e);
        }
    }

    /**
     * Resolves the station-specific mail provider based on the station's mail configuration.
     * Does not fall back to the global provider; returns empty if no station config exists.
     *
     * @param stationId the station ID to resolve a provider for
     * @return the configured mail provider, or empty if not configured
     */
    public Optional<MailProvider> resolveStationProvider(@Nullable Integer stationId) {
        if (stationId == null) return Optional.empty();
        return chainService.firstForStation(stationId).map(EmailService::buildProvider);
    }

    /**
     * Builds a {@link MailProvider} from one entry of a list, without persisting anything, sending
     * under the entry's display name with replies going where the entry says. Returns {@code null}
     * when the entry's provider is {@link MailProviderType#NONE}.
     */
    private static @Nullable MailProvider buildProvider(MailChainEntry entry) {
        var provider = buildProvider(
                entry.provider(),
                entry.smtpHost(),
                entry.smtpPort(),
                entry.smtpEncryption(),
                entry.smtpUser(),
                entry.smtpPassword(),
                entry.apiKey(),
                entry.senderAddress(),
                entry.senderName());
        return provider == null ? null : provider.replyingTo(entry.replyTo());
    }

    /**
     * Builds a {@link MailProvider} from raw config values without persisting anything. Returns
     * {@code null} when the provider is {@link MailProviderType#NONE}. The relays with a fixed
     * address all require STARTTLS; only a plain server and Sweego, whose address is configured, take
     * the encryption from the configuration. Sweego gives every account its own relay host and port.
     * Brevo carries the correlation header through to its delivery events, which ties an event back
     * to its mail.
     */
    private static @Nullable SmtpMailProvider buildProvider(
            MailProviderType provider,
            String smtpHost,
            int smtpPort,
            SmtpEncryption smtpEncryption,
            String user,
            String password,
            String apiKey,
            String senderAddress,
            String senderName) {
        return switch (provider) {
            case SMTP ->
                new SmtpMailProvider(smtpHost, smtpPort, smtpEncryption, user, password, senderAddress, senderName);
            case RAPIDMAIL ->
                new SmtpMailProvider(
                        "smtp.rapidmail.de", 587, SmtpEncryption.STARTTLS, user, apiKey, senderAddress, senderName);
            case TWILIO ->
                new SmtpMailProvider(
                        "smtp.sendgrid.net",
                        587,
                        SmtpEncryption.STARTTLS,
                        "apikey",
                        apiKey,
                        senderAddress,
                        senderName,
                        SENDGRID_CORRELATION_HEADER,
                        SENDGRID_CORRELATION_FORMAT);
            case SWEEGO ->
                new SmtpMailProvider(
                        smtpHost,
                        smtpPort,
                        smtpEncryption,
                        user,
                        apiKey,
                        senderAddress,
                        senderName,
                        SWEEGO_CORRELATION_HEADER,
                        null);
            case BREVO ->
                new SmtpMailProvider(
                        "smtp-relay.brevo.com",
                        587,
                        SmtpEncryption.STARTTLS,
                        user,
                        apiKey,
                        senderAddress,
                        senderName,
                        BREVO_CORRELATION_HEADER,
                        null);
            case NONE -> null;
        };
    }

    /**
     * Attempts a real connection against the given mail config without persisting anything.
     *
     * @return {@code null} on success, or the underlying error message on failure
     */
    public @Nullable String testMailConnection(
            MailProviderType provider,
            String smtpHost,
            int smtpPort,
            SmtpEncryption smtpEncryption,
            String user,
            String password,
            String apiKey,
            String senderAddress,
            String senderName) {
        MailProvider mailProvider = buildProvider(
                provider, smtpHost, smtpPort, smtpEncryption, user, password, apiKey, senderAddress, senderName);
        if (mailProvider == null) return "No mail provider configured";
        var result = mailProvider.testConnection();
        if (result.success()) return null;
        if (!result.authFailure()) return result.error();
        return result.error() + authGuidance(provider);
    }

    /**
     * Provider-specific advice appended to authentication failures, pointing at the credential
     * kind each relay actually expects.
     */
    private static String authGuidance(MailProviderType provider) {
        return switch (provider) {
            case BREVO ->
                " Brevo expects your Brevo account login email as user and an SMTP key (starts with 'xsmtpsib-') from Settings > SMTP & API. The regular API key ('xkeysib-') does not work for sending mail.";
            case TWILIO -> " Twilio SendGrid expects an API key starting with 'SG.' as the key.";
            case RAPIDMAIL ->
                " RapidMail expects the SMTP username and password generated for a project under Transactional emails > Manage projects.";
            case SWEEGO -> " Sweego expects the SMTP login and password generated in the Sweego dashboard.";
            case SMTP -> " Check the SMTP username and password.";
            case NONE -> "";
        };
    }

    /**
     * Attempts a real connection against the station's persisted mail configuration.
     *
     * @param stationId the station ID, may be {@code null}
     * @return {@code null} on success, or the underlying error message on failure
     */
    public @Nullable String testStationMailConnection(@Nullable Integer stationId) {
        return testStationMailConnection(stationId, 0);
    }

    /**
     * Attempts a real connection against one provider of a station's list.
     *
     * <p>Every entry can be reached, not only the first: a provider further down is the one that
     * carries the post once those above it are spent, so being unable to try it means finding out
     * it was misconfigured only when it is needed.
     *
     * @param stationId the station, or null
     * @param position  which provider of its list
     * @return {@code null} on success, or the underlying error message on failure
     */
    public @Nullable String testStationMailConnection(@Nullable Integer stationId, int position) {
        if (stationId == null) return "No mail provider configured";
        var chain = chainService.ownForStation(stationId);
        var entry = chainService.at(chain, position);
        if (entry.isEmpty()) return "No mail provider configured";
        return testMailConnection(entry.get());
    }

    /**
     * Attempts a real connection against one entry of a list, without persisting anything.
     *
     * @return {@code null} on success, or the underlying error message on failure
     */
    public @Nullable String testMailConnection(MailChainEntry config) {
        return testMailConnection(
                config.provider(),
                config.smtpHost(),
                config.smtpPort(),
                config.smtpEncryption(),
                config.smtpUser(),
                config.smtpPassword(),
                config.apiKey(),
                config.senderAddress(),
                config.senderName());
    }

    /**
     * Returns the configured base URL for the application, used in email links.
     *
     * @return the base URL
     */
    public String getBaseUrl() {
        return api.baseUrl();
    }

    /**
     * Routes a per-station notification email through the station's own outbound mailbox, and after
     * it through the instance's providers where the station was granted them.
     *
     * <p><strong>Use only for high-volume aggregate notifications</strong> (event reminders,
     * digests, attendance summaries) - anything where missing one is acceptable and where the daily
     * limits of the station's providers, its share of the instance's providers and its own daily
     * limit there should apply. A station may have no provider at all, in which case nothing is
     * delivered; once every provider is spent for the day, the mail waits for the next one.
     *
     * <p><strong>Do not use for mandatory transactional mail</strong> (account verification,
     * password reset, invites, application status, waitlist confirmations, security notices).
     * Route those through {@link #enqueueGlobal} via one of the {@code send*} helpers so the
     * instance-wide mail relay carries them - guaranteeing delivery even when the station has not
     * configured its own outbound relay.
     */
    public void queueStationEmail(int stationId, String to, String subject, String htmlBody) {
        if (demoConfig.enabled()) {
            log.info("Demo mode: Suppressed station email to={} subject={}", to, subject);
            return;
        }
        queueRepository.enqueue(to, subject, htmlBody, stationId);
        log.debug("Station {} email queued to={} subject={}", stationId, to, subject);
    }

    /**
     * Queues a mail that belongs to no station, which goes out through the instance's own chain.
     *
     * <p>A cluster spans several stations and has no address of its own that a reader would
     * recognise, so what it sends carries the instance's.
     */
    public void queueInstanceEmail(String to, String subject, String htmlBody) {
        if (demoConfig.enabled()) {
            log.info("Demo mode: Suppressed instance email to={} subject={}", to, subject);
            return;
        }
        queueRepository.enqueue(to, subject, htmlBody, null);
        log.debug("Instance email queued to={} subject={}", to, subject);
    }

    /** Whether the instance's own chain has room to send today. */
    public boolean canInstanceSend() {
        return allowance.anyRoomToday(null, chainService.forInstance());
    }

    /**
     * Whether any provider in the station's chain still has room today.
     *
     * <p>The allowance belongs to the providers rather than to the station, so a list whose first
     * provider is spent can still send through the next one. The instance's providers at the end of
     * a granted station's chain count only while the stations' share of them and the station's own
     * daily limit there leave room.
     */
    public boolean canStationSend(int stationId) {
        return allowance.anyRoomToday(stationId, chainService.forStation(stationId));
    }

    /**
     * Sends an email verification link to a user.
     *
     * @param email the recipient email address
     * @param name  the recipient's display name
     * @param token the verification token
     */
    public void sendVerificationEmail(String email, String name, String token, String locale) {
        String url = api.baseUrl() + "/verify-email?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        enqueueGlobal(email, subject("verify-email", locale, null), loadTemplate("verify-email.html", locale, vars));
    }

    public void sendPasswordSetupEmail(String email, String name, String token, String locale) {
        String url = api.baseUrl() + "/set-password?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        enqueueGlobal(email, subject("set-password", locale, null), loadTemplate("set-password.html", locale, vars));
    }

    /**
     * Whether the instance-wide mail relay has a configured provider.
     */
    public boolean isGlobalMailConfigured() {
        return currentGlobalProvider() != null;
    }

    /**
     * Sends a test email so an administrator can verify mail delivery end to end. A station id
     * routes the mail through that station's own outbound mailbox, including its send caps;
     * {@code null} routes it through the instance-wide relay.
     */
    /**
     * Sends a test mail through one provider of a list, rather than queueing it for whichever
     * provider is in turn.
     *
     * <p>Queueing would prove only that the list as a whole delivers, which is the thing already
     * known to work when somebody comes here to check a provider further down. Handing it to the
     * chosen entry directly is what answers the question actually being asked.
     *
     * @param stationId the station, or null for the instance list
     * @param position  which provider of that list
     * @param to        where the mail goes, which need not be the address of whoever asked
     * @return {@code null} when the provider accepted it, or the reason it did not
     */
    public @Nullable String sendTestMailThrough(
            @Nullable Integer stationId, int position, String to, String name, String locale) {
        var chain = stationId == null ? chainService.forInstance() : chainService.ownForStation(stationId);
        var entry = chainService.at(chain, position);
        if (entry.isEmpty()) return "No mail provider configured";
        MailProvider provider = buildProvider(entry.get());
        if (provider == null) return "No mail provider configured";
        var vars = baseVars(name, stationId);
        String subjectLine = subject("test-mail", locale, null);
        String body = loadTemplate("test-mail.html", locale, vars);
        var result = provider.send(to, subjectLine, body, "test-" + position);
        return result == MailProvider.SendResult.SENT ? null : "The provider refused the message";
    }

    public void sendTestEmail(String to, String name, String locale, @Nullable Integer stationId) {
        var vars = baseVars(name, stationId);
        String subjectLine = subject("test-mail", locale, null);
        String body = loadTemplate("test-mail.html", locale, vars);
        if (stationId != null) {
            queueStationEmail(stationId, to, subjectLine, body);
        } else {
            enqueueGlobal(to, subjectLine, body);
        }
    }

    /**
     * Notifies an existing account holder that someone attempted to register a new account
     * using their email address. Sent in lieu of returning a duplicate-email error to the
     * registration caller, so the public registration endpoint cannot be used to enumerate
     * existing addresses.
     */
    public void sendDuplicateRegistrationNotice(String email, String name, String locale) {
        var vars = baseVars(name, null);
        vars.put("loginUrl", api.baseUrl() + "/login");
        enqueueGlobal(
                email,
                subject("duplicate-registration", locale, null),
                loadTemplate("duplicate-registration.html", locale, vars));
    }

    /**
     * Out-of-band confirmation that an account's password was just changed. Sent on every
     * successful password rotation (self-service change, reset via emailed token, or
     * admin-triggered reset). Includes a hint that the user should contact support if
     * they did not initiate the change.
     */
    public void sendPasswordChangedNotice(String email, String name, String locale) {
        var vars = baseVars(name, null);
        vars.put("loginUrl", api.baseUrl() + "/login");
        enqueueGlobal(
                email, subject("password-changed", locale, null), loadTemplate("password-changed.html", locale, vars));
    }

    /**
     * Sent to a user whose 2FA was reset by an administrator. The reset wiped every factor,
     * backup code, active session, and trusted device for the account; the user must enrol
     * fresh on next login. {@code actorLabel} is the admin's email (or a generic
     * "administrator" fallback when unknown).
     */
    /**
     * Sent when a new device was approved through the code handshake and a passkey was created
     * on it. Names the device, because with a passkey nobody notices a takeover the way they
     * notice a password that suddenly stops working.
     */
    public void sendPasskeyDeviceApprovedNotice(String email, String name, String device, String place, String locale) {
        var vars = baseVars(name, null);
        vars.put("device", device == null || device.isBlank() ? "?" : device);
        vars.put("place", place == null || place.isBlank() ? "?" : place);
        vars.put("securityUrl", api.baseUrl() + "/account/security");
        enqueueGlobal(
                email,
                subject("passkey-device-approved", locale, null),
                loadTemplate("passkey-device-approved.html", locale, vars));
    }

    /**
     * Sent when a device was signed in because another one vouched for it.
     *
     * <p>Its own mail rather than the passkey one: nothing was created and nothing was left behind,
     * and telling somebody a credential exists when none does would send them hunting for a passkey
     * to delete that is not there.
     */
    public void sendDeviceSignedInNotice(String email, String name, String device, String place, String locale) {
        var vars = baseVars(name, null);
        vars.put("device", device == null || device.isBlank() ? "?" : device);
        vars.put("place", place == null || place.isBlank() ? "?" : place);
        vars.put("securityUrl", api.baseUrl() + "/account/security");
        enqueueGlobal(
                email, subject("device-signed-in", locale, null), loadTemplate("device-signed-in.html", locale, vars));
    }

    /**
     * Sent to whoever mail about a managed member goes to when an enrolment code for their
     * account was put on a screen. Somebody has to be told, and it cannot be the member: they
     * have no mailbox, which is why the code exists.
     */
    public void sendPasskeyCodeIssuedNotice(String email, String memberName, String locale) {
        var vars = baseVars(memberName, null);
        vars.put("memberName", memberName);
        vars.put("securityUrl", api.baseUrl() + "/account/security");
        enqueueGlobal(
                email,
                subject("passkey-code-issued", locale, Map.of("name", memberName)),
                loadTemplate("passkey-code-issued.html", locale, vars));
    }

    public void sendTwoFactorResetNotice(
            String email, String name, @Nullable String actorLabel, Instant resetAt, String locale) {
        var vars = baseVars(name, null);
        vars.put("loginUrl", api.baseUrl() + "/login");
        String defaultActor = templateRenderer.body("twoFactorReset.defaultActor", locale);
        vars.put("actor", actorLabel != null && !actorLabel.isBlank() ? actorLabel : defaultActor);
        vars.put("resetAt", resetAt.toString());
        enqueueGlobal(
                email, subject("two-factor-reset", locale, null), loadTemplate("two-factor-reset.html", locale, vars));
    }

    /**
     * Sent to the user's existing email address when they request an email change.
     * Clicking the link authorises releasing the address; the change only commits
     * once the new address also confirms via {@link #sendEmailChangeClaimRequest}.
     */
    public void sendEmailChangeReleaseRequest(
            String oldEmail, String name, String newEmail, String token, String locale) {
        String url = api.baseUrl() + "/confirm-email-change?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        vars.put("newEmail", newEmail);
        enqueueGlobal(
                oldEmail,
                subject("email-change-release", locale, null),
                loadTemplate("email-change-release.html", locale, vars));
    }

    /**
     * Sent to the new email address when the user requests an email change. Clicking
     * the link confirms receipt; the change only commits once the existing address
     * also authorises via {@link #sendEmailChangeReleaseRequest}.
     */
    public void sendEmailChangeClaimRequest(
            String newEmail, String name, String oldEmail, String token, String locale) {
        String url = api.baseUrl() + "/confirm-email-change?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        vars.put("oldEmail", oldEmail);
        enqueueGlobal(
                newEmail,
                subject("email-change-claim", locale, null),
                loadTemplate("email-change-claim.html", locale, vars));
    }

    /**
     * Notifies both the old and the new email address that an email change just
     * committed. The recipient address is the destination of this individual mail;
     * the {@code oldEmail} and {@code newEmail} values are shown in the body for
     * transparency.
     */
    public void sendEmailChangedNotice(String recipient, String name, String oldEmail, String newEmail, String locale) {
        var vars = baseVars(name, null);
        vars.put("oldEmail", oldEmail);
        vars.put("newEmail", newEmail);
        enqueueGlobal(
                recipient, subject("email-changed", locale, null), loadTemplate("email-changed.html", locale, vars));
    }

    /**
     * Tells a member that the guardian who looks after them switched signing in on.
     *
     * <p>Sent only to somebody who already has a password, because an account still waiting to be
     * claimed is sent the setup link instead. The station is named: a member may belong to more than
     * one, and only one of them decided this.
     */
    public void sendManagedLoginGrantedNotice(String email, String name, String stationName, String locale) {
        var vars = baseVars(name, null);
        vars.put("stationName", stationName);
        vars.put("loginUrl", api.baseUrl() + "/login");
        enqueueGlobal(
                email,
                subject("managed-login-granted", locale, stationPlaceholders(stationName)),
                loadTemplate("managed-login-granted.html", locale, vars));
    }

    /**
     * Tells a member that the guardian who looks after them took signing in away again. There is
     * nothing to act on, so the mail carries no link.
     */
    public void sendManagedLoginRevokedNotice(String email, String name, String stationName, String locale) {
        var vars = baseVars(name, null);
        vars.put("stationName", stationName);
        enqueueGlobal(
                email,
                subject("managed-login-revoked", locale, stationPlaceholders(stationName)),
                loadTemplate("managed-login-revoked.html", locale, vars));
    }

    /**
     * Asks the owner of an account whether a station may link it to one of its members. The link only
     * opens the question once they are signed in to that account; answering happens there, never by
     * following the link alone.
     *
     * @param stationName the station that asks
     * @param memberName  the member the account would be linked to
     * @param token       the token the link carries
     */
    public void sendAccountLinkRequest(
            String email, String name, String stationName, String memberName, String token, String locale) {
        var vars = baseVars(name, null);
        vars.put("stationName", stationName);
        vars.put("memberName", memberName);
        vars.put("url", api.baseUrl() + "/account/link/" + token);
        enqueueGlobal(
                email,
                subject("account-link-request", locale, stationPlaceholders(stationName)),
                loadTemplate("account-link-request.html", locale, vars));
    }

    /**
     * Asks the owner of an account whether they take a role at an association. As with a station's
     * request, the link only opens the question once they are signed in to that account.
     *
     * @param associationName the association that asks
     * @param administrator   whether the role it offers runs the association
     * @param token           the token the link carries
     */
    public void sendAssociationLinkRequest(
            String email, String name, String associationName, boolean administrator, String token, String locale) {
        var vars = baseVars(name, null);
        vars.put("associationName", associationName);
        vars.put("administrator", administrator ? "yes" : "");
        vars.put("url", api.baseUrl() + "/account/link/" + token);
        enqueueGlobal(
                email,
                subject("association-link-request", locale, Map.of("associationName", associationName)),
                loadTemplate("association-link-request.html", locale, vars));
    }

    public void sendPasswordResetEmail(String email, String name, String token, String locale) {
        String url = api.baseUrl() + "/reset-password?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        enqueueGlobal(
                email, subject("reset-password", locale, null), loadTemplate("reset-password.html", locale, vars));
    }

    public void sendStationDeletionConfirmation(String email, String name, String token, String locale) {
        String url = api.baseUrl() + "/api/v1/public/confirm-station-delete?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        enqueueGlobal(
                email, subject("station-delete", locale, null), loadTemplate("station-delete.html", locale, vars));
    }

    public void sendApplicationVerifyEmail(
            String email, String name, String stationName, String token, String locale, @Nullable Integer stationId) {
        String url = api.baseUrl() + "/apply/verify?token=" + token;
        var vars = baseVars(name, stationId);
        vars.put("stationName", stationName);
        vars.put("url", url);
        enqueueGlobal(
                email,
                subject("application-verify", locale, stationPlaceholders(stationName)),
                loadTemplate("application-verify.html", locale, vars));
    }

    public void sendApplicationAcceptedEmail(
            String email, String name, String stationName, String token, String locale, @Nullable Integer stationId) {
        String url = api.baseUrl() + "/set-password?token=" + token;
        var vars = baseVars(name, stationId);
        vars.put("stationName", stationName);
        vars.put("url", url);
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
        }
        enqueueGlobal(
                email,
                subject("application-accepted", locale, stationPlaceholders(stationName)),
                loadTemplate("application-accepted.html", locale, vars));
    }

    public void sendApplicationDeniedEmail(
            String email, String name, String stationName, String reason, String locale, @Nullable Integer stationId) {
        var vars = baseVars(name, stationId);
        vars.put("stationName", stationName);
        vars.put("reason", reason != null ? reason : "");
        enqueueGlobal(
                email,
                subject("application-denied", locale, stationPlaceholders(stationName)),
                loadTemplate("application-denied.html", locale, vars));
    }

    public void sendApplicationReceivedEmail(
            String email, String name, String stationName, String locale, @Nullable Integer stationId) {
        var vars = baseVars(name, stationId);
        vars.put("stationName", stationName);
        enqueueGlobal(
                email,
                subject("application-received", locale, stationPlaceholders(stationName)),
                loadTemplate("application-received.html", locale, vars));
    }

    /**
     * Sends the transactional confirmation that a public waiting-list registration has been
     * recorded. Routed through the instance-wide mail relay rather than the station relay
     * because it is mandatory transactional mail, not aggregate notification traffic.
     */
    public void sendWaitlistRegistrationEmail(
            String email,
            String name,
            String accessToken,
            String stationName,
            String locale,
            @Nullable Integer stationId) {
        String url = api.baseUrl() + "/waiting-list/status?token=" + accessToken;
        var vars = baseVars(name, stationId);
        vars.put("url", url);
        vars.put("stationName", stationName != null ? stationName : "");
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
        }
        enqueueGlobal(
                email,
                subject("waitlist-registered", locale, waitlistPlaceholders(stationName)),
                loadTemplate("waitlist-registered.html", locale, vars));
    }

    /**
     * Sends the invitation to come and look, which is the first message a station writes to
     * somebody on its waiting list of its own accord.
     *
     * <p>It carries the appointment they are asked to come to and links to the page where the three
     * answers are given. The answers are not links in the body: a one-click answer in a mail is
     * followed by scanners, which would answer on the reader's behalf.
     */
    public void sendWaitlistInvitationEmail(
            String email,
            String name,
            String accessToken,
            String stationName,
            String locale,
            @Nullable Integer stationId,
            WaitlistInvitationDetails details) {
        var vars = baseVars(name, stationId);
        vars.put("url", api.baseUrl() + "/waiting-list/status?token=" + accessToken);
        vars.put("stationName", stationName != null ? stationName : "");
        vars.put("appointmentName", details.appointmentName());
        vars.put("appointmentDate", details.date());
        vars.put("appointmentTime", details.time());
        vars.put("arrivalTime", details.arrivalTime());
        vars.put("location", details.location());
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
        }
        enqueueGlobal(
                email,
                subject("waitlist-invitation", locale, waitlistPlaceholders(stationName)),
                loadTemplate("waitlist-invitation.html", locale, vars));
    }

    /**
     * Sends the transactional reminder to confirm an outstanding waiting-list spot. Routed
     * through the instance-wide mail relay because it is mandatory transactional mail.
     */
    public void sendWaitlistConfirmReminderEmail(
            String email,
            String name,
            String accessToken,
            String stationName,
            String locale,
            @Nullable Integer stationId) {
        String url = api.baseUrl() + "/waiting-list/status?token=" + accessToken;
        var vars = baseVars(name, stationId);
        vars.put("url", url);
        vars.put("stationName", stationName != null ? stationName : "");
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
        }
        enqueueGlobal(
                email,
                subject("waitlist-confirm-reminder", locale, waitlistPlaceholders(stationName)),
                loadTemplate("waitlist-confirm-reminder.html", locale, vars));
    }

    /**
     * Sends the transactional warning that a waiting-list entry will be removed shortly.
     * Routed through the instance-wide mail relay because it is mandatory transactional mail.
     */
    public void sendWaitlistRemovalWarningEmail(
            String email,
            String name,
            String accessToken,
            String stationName,
            String locale,
            @Nullable Integer stationId) {
        String url = api.baseUrl() + "/waiting-list/status?token=" + accessToken;
        var vars = baseVars(name, stationId);
        vars.put("url", url);
        vars.put("stationName", stationName != null ? stationName : "");
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
        }
        enqueueGlobal(
                email,
                subject("waitlist-removal-warning", locale, waitlistPlaceholders(stationName)),
                loadTemplate("waitlist-removal-warning.html", locale, vars));
    }

    /**
     * Sends the transactional verification email for a public waiting-list registration.
     * Routed through the instance-wide mail relay because it is mandatory transactional mail.
     */
    public void sendWaitlistVerifyEmail(
            String email, String name, String stationName, String token, String locale, @Nullable Integer stationId) {
        String url = api.baseUrl() + "/public/waitlist/verify/" + token;
        var vars = baseVars(name, stationId);
        vars.put("url", url);
        vars.put("stationName", stationName != null ? stationName : "");
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
        }
        enqueueGlobal(
                email,
                subject("waitlist-verify", locale, waitlistPlaceholders(stationName)),
                loadTemplate("waitlist-verify.html", locale, vars));
    }

    /**
     * Asks somebody to sign a document, or the member whose mail they read for, through the instance-wide
     * relay: a signature a station asks for is something to act on, not news that can wait for a digest.
     *
     * @param email      where the mail goes
     * @param name       the person it is about, for the greeting
     * @param guardian   whether it is read by somebody who looks after that person
     * @param invitation the document and where it is signed
     * @param locale     the language to write in
     */
    public void sendSignatureRequest(
            String email, String name, boolean guardian, SignatureInvitation invitation, String locale) {
        sendSignatureInvitation("signature-requested", email, name, guardian, invitation, locale);
    }

    /**
     * Reminds somebody of a signature a document still waits for, the same way as the request.
     *
     * @param email      where the mail goes
     * @param name       the person it is about, for the greeting
     * @param guardian   whether it is read by somebody who looks after that person
     * @param invitation the document and where it is signed
     * @param locale     the language to write in
     */
    public void sendSignatureReminder(
            String email, String name, boolean guardian, SignatureInvitation invitation, String locale) {
        sendSignatureInvitation("signature-reminder", email, name, guardian, invitation, locale);
    }

    /**
     * Sends a signer their own copy of what they signed through the instance-wide relay, with the PDF
     * attached where the copy carries it.
     *
     * @param email    where the mail goes
     * @param name     the person it is about, for the greeting
     * @param guardian whether it is read by somebody who looks after that person
     * @param copy     what was signed and sealed
     * @param locale   the language to write in
     */
    public void sendSignedCopy(String email, String name, boolean guardian, SignedCopy copy, String locale) {
        var vars = baseVars(name, null);
        vars.put("guardian", guardian ? "yes" : "");
        vars.put("stationName", copy.stationName());
        vars.put("documentTitle", copy.documentTitle());
        vars.put("memberName", copy.memberName());
        vars.put("signerName", copy.signerName());
        vars.put("signedAt", copy.signedAt());
        vars.put("sealedSha256", copy.sealedSha256());
        vars.put("url", copy.documentUrl());
        vars.put("recordUrl", copy.recordUrl());
        vars.put("verifyUrl", copy.verifyUrl());
        var attachment = copy.attachment();
        vars.put("attached", attachment == null ? "" : "yes");
        vars.put("tooLarge", attachment == null && copy.tooLarge() ? "yes" : "");
        String subject = subject("signed-copy", locale, Map.of("documentTitle", copy.documentTitle()));
        String body = loadTemplate("signed-copy.html", locale, vars);
        enqueueGlobal(email, subject, body, attachment == null ? List.of() : List.of(attachment));
    }

    private void sendSignatureInvitation(
            String template,
            String email,
            String name,
            boolean guardian,
            SignatureInvitation invitation,
            String locale) {
        var vars = baseVars(name, null);
        vars.put("guardian", guardian ? "yes" : "");
        vars.put("stationName", invitation.stationName());
        vars.put("documentTitle", invitation.documentTitle());
        vars.put("memberName", invitation.memberName());
        vars.put("url", invitation.url());
        enqueueGlobal(
                email,
                subject(template, locale, Map.of("documentTitle", invitation.documentTitle())),
                loadTemplate(template + ".html", locale, vars));
    }

    public String loadTemplate(String name, String locale, Map<String, String> variables) {
        return templateRenderer.render(name, locale, variables);
    }

    private String subject(String key, String locale, @Nullable Map<String, String> placeholders) {
        return templateRenderer.subject(key, locale, placeholders);
    }

    /**
     * The first provider of the instance list, or null when the list is empty.
     *
     * <p>Read from the list rather than from the fields a single provider used to live in. Those
     * fields are empty on an instance that has saved its list, so asking them said no provider was
     * configured while three were, and the queue then never fetched an instance mail at all.
     */
    private @Nullable MailProvider currentGlobalProvider() {
        return chainService.forInstance().stream()
                .findFirst()
                .map(EmailService::buildProvider)
                .orElse(null);
    }

    private String resolveProviderSenderName(@Nullable Integer stationId) {
        var provider = resolveStationProvider(stationId);
        if (provider.isPresent() && provider.get() instanceof SmtpMailProvider smtp) {
            return smtp.senderName();
        }
        return mailing.senderName();
    }

    /**
     * Routes a mandatory transactional email through the instance-wide mail relay.
     *
     * <p>Every {@code send*} helper on this service for account-, station-, application-, invite-,
     * waitlist-, and security-related mail delegates here. The instance relay carries these
     * regardless of whether the originating station has configured its own outbound mailbox. Only
     * the daily limits of the instance's providers apply, in full: the share stations may use of
     * them and a station's own daily limit there never hold this mail back.
     */
    private void enqueueGlobal(String to, String subject, String htmlBody) {
        enqueueGlobal(to, subject, htmlBody, List.of());
    }

    /** The same, for a mail that carries files, which are queued with it. */
    private void enqueueGlobal(String to, String subject, String htmlBody, List<MailAttachment> attachments) {
        if (demoConfig.enabled()) {
            log.info("Demo mode: Suppressed email to={} subject={}", to, subject);
            return;
        }
        if (currentGlobalProvider() == null) {
            log.warn(
                    "Mail is not configured; queueing email to={} subject={} until a mail provider is set up",
                    to,
                    subject);
        }
        if (attachments.isEmpty()) {
            queueRepository.enqueue(to, subject, htmlBody, null);
        } else {
            queueRepository.enqueueWithAttachments(to, subject, htmlBody, attachments);
        }
        log.debug("Email queued to={} subject={}", to, subject);
    }

    /**
     * The entry whose turn it actually is, having walked past any whose daily allowance is spent.
     *
     * <p>A free tier is sold by the day, so a provider that has sent its share is not merely
     * failing, it is finished until tomorrow. Walking past it puts the mail on the next provider
     * straight away instead of spending attempts on a refusal that is certain. For station mail on
     * one of the instance's providers, the stations' share of it and the station's own daily limit
     * there count as part of that allowance.
     *
     * @return the entry, or empty when nothing in the chain has room left today
     */
    private Optional<MailChainEntry> entryInTurn(List<MailChainEntry> chain, EmailQueueRepository.QueuedEmail email) {
        var blocked = blockRepository.blockedFor(email.stationId(), email.recipient());
        int position = email.providerPosition();
        while (position < chain.size()) {
            MailChainEntry entry = chain.get(position);
            if (blocked.contains(entry.provider())) {
                log.info(
                        "Provider {} is refused by the domain of {}; email {} moves to the next without trying",
                        position,
                        email.recipient(),
                        email.id());
            } else if (allowance.hasRoomToday(email.stationId(), entry)) {
                return Optional.of(entry);
            } else {
                log.info(
                        "Provider {} of the chain has no room left today; email {} moves to the next",
                        position,
                        email.id());
            }
            position++;
            queueRepository.advanceProvider(email.id());
        }
        return Optional.empty();
    }

    /**
     * Whether anything in this owner's list could still carry a mail to this address today.
     *
     * <p>What the overview needs to say that a message is not merely waiting but stuck: every
     * provider either refused by the receiving domain or out of allowance.
     */
    public boolean canReach(@Nullable Integer stationId, String recipient) {
        var chain = stationId == null ? chainService.forInstance() : chainService.forStation(stationId);
        var blocked = blockRepository.blockedFor(stationId, recipient);
        return chain.stream()
                .anyMatch(entry -> !blocked.contains(entry.provider()) && allowance.hasRoomToday(stationId, entry));
    }

    /**
     * What became of one mail in a round of the queue.
     */
    private enum Outcome {
        SENT,
        FAILED,
        REQUEUED
    }

    private void processQueue() {
        try {
            boolean globalConfigured = currentGlobalProvider() != null;
            var batch = queueRepository.fetchPending(20, globalConfigured);
            if (batch.isEmpty()) return;

            log.debug("Processing batch of {} pending emails", batch.size());

            int sent = 0;
            int failed = 0;
            int requeued = 0;
            for (var email : batch) {
                switch (process(email)) {
                    case SENT -> sent++;
                    case FAILED -> failed++;
                    case REQUEUED -> requeued++;
                }
            }

            if (sent > 0 || failed > 0 || requeued > 0) {
                log.info(
                        "Email batch processed: sent={} failed={} requeued={} pending={}",
                        sent,
                        failed,
                        requeued,
                        queueRepository.pendingCount());
            }
        } catch (Exception e) {
            log.error("Error processing email queue", e);
        }
    }

    /**
     * Takes one mail through its chain: holds it back where its owner cannot send now, and hands it
     * to the provider in turn otherwise.
     *
     * <p>A station's mail waits for the next day when nothing in its chain has room left today,
     * rather than failing: the allowances turn over at midnight, and a reminder that goes out a day
     * late is worth more than one that never does. A station with no provider at all has nothing to
     * wait for, so its mail fails.
     *
     * <p>The instance's own mail that has walked past every provider waits for the next day the same
     * way and starts again at the first provider then. Left where the walk ended, it would sit past
     * the end of its list and never be carried again. While the instance has no provider at all, its
     * mail stays queued until one is configured.
     */
    private Outcome process(EmailQueueRepository.QueuedEmail email) {
        Integer stationId = email.stationId();
        List<MailChainEntry> chain;
        if (stationId != null) {
            if (!readOnlyGuard.isWritable(stationId)) {
                log.debug("Email {} requeued: station {} is read-only", email.id(), stationId);
                queueRepository.requeue(email.id());
                return Outcome.REQUEUED;
            }
            chain = chainService.forStation(stationId);
            if (chain.isEmpty()) {
                log.warn("Email {} failed: station {} has no provider to send through", email.id(), stationId);
                queueRepository.markFailed(email.id());
                return Outcome.FAILED;
            }
            if (!allowance.anyRoomToday(stationId, chain)) {
                log.info(
                        "Email {} waits until tomorrow: no provider of station {} has room left today",
                        email.id(),
                        stationId);
                queueRepository.waitUntil(email.id(), LocalDate.now().plusDays(1));
                return Outcome.REQUEUED;
            }
        } else {
            chain = chainService.forInstance();
        }

        var inTurn = entryInTurn(chain, email);
        if (inTurn.isEmpty()) {
            if (stationId == null && chain.isEmpty()) {
                log.debug("Email {} deferred: no instance provider is configured", email.id());
                queueRepository.requeue(email.id());
                return Outcome.REQUEUED;
            }
            if (stationId == null) {
                log.info("Email {} waits until tomorrow: no instance provider has room left today", email.id());
                queueRepository.waitUntil(email.id(), LocalDate.now().plusDays(1));
                return Outcome.REQUEUED;
            }
            log.warn("Email {} failed: station {} has no provider left to try", email.id(), stationId);
            queueRepository.markFailed(email.id());
            return Outcome.FAILED;
        }
        return send(email, inTurn.get());
    }

    private Outcome send(EmailQueueRepository.QueuedEmail email, MailChainEntry entry) {
        MailProvider provider = buildProvider(entry);
        if (provider == null) {
            queueRepository.markFailed(email.id());
            return Outcome.FAILED;
        }
        queueRepository.renewClaim(email.id());
        var result = provider.send(
                email.recipient(),
                email.subject(),
                email.body(),
                String.valueOf(email.id()),
                queueRepository.attachmentsOf(email.id()));
        return switch (result) {
            case SENT -> {
                queueRepository.markSent(email.id(), entry.instancePosition());
                if (email.stationId() == null) queueRepository.incrementDailyCount(LocalDate.now());
                yield Outcome.SENT;
            }
            case TRANSIENT_FAILURE ->
                retryService.afterTransientFailure(email) == MailRetryPolicy.Step.GIVE_UP
                        ? Outcome.FAILED
                        : Outcome.REQUEUED;
            case PERMANENT_FAILURE -> {
                log.warn("Email {} delivery to {} failed permanently; marking failed", email.id(), email.recipient());
                queueRepository.markFailed(email.id());
                yield Outcome.FAILED;
            }
        };
    }

    private Map<String, String> baseVars(String name, @Nullable Integer stationId) {
        var vars = new HashMap<String, String>();
        vars.put("name", name);
        vars.put("baseUrl", api.baseUrl());
        vars.put("senderName", resolveProviderSenderName(stationId));
        return vars;
    }

    private static Map<String, String> stationPlaceholders(String stationName) {
        return Map.of("stationName", stationName != null ? stationName : "");
    }

    private static Map<String, String> waitlistPlaceholders(String stationName) {
        String suffix = stationName != null && !stationName.isEmpty() ? " - " + stationName : "";
        return Map.of("stationName", stationName != null ? stationName : "", "stationSuffix", suffix);
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(
                new ScheduledTask(
                        "email-queue",
                        Schedule.fixedDelay(Duration.ofSeconds(10), Duration.ofSeconds(10)),
                        this::processQueue),
                new ScheduledTask(
                        "email-queue-cleanup",
                        Schedule.fixedRate(Duration.ofHours(1), Duration.ofHours(24)),
                        this::runCleanup));
    }
}
