/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.service.mail.MailProvider;
import dev.chojo.ember.feature.mail.service.mail.SmtpMailProvider;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.StationMailConfig;
import dev.chojo.ember.feature.station.repository.StationMailConfigRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Central email service handling both global system emails and per-station notification emails.
 * Uses a queued architecture with a background worker that processes pending emails every 10 seconds.
 * Supports multiple mail providers (SMTP, Rapidmail, Twilio SendGrid, Sweego, Brevo) and enforces
 * daily send limits at both the global and per-station level.
 */
@Singleton
public class EmailService {
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
    private final StationMailConfigRepository mailConfigRepository;
    private final MailTemplateRenderer templateRenderer;
    private final StationReadOnlyGuard readOnlyGuard;
    private final MailChainService chainService;

    @Inject
    public EmailService(
            Mailing mailing,
            Api api,
            Demo demoConfig,
            EmailQueueRepository queueRepository,
            StationMailConfigRepository mailConfigRepository,
            MailTemplateRenderer templateRenderer,
            StationReadOnlyGuard readOnlyGuard,
            MailChainService chainService) {
        this.chainService = chainService;
        this.mailing = mailing;
        this.api = api;
        this.demoConfig = demoConfig;
        this.queueRepository = queueRepository;
        this.mailConfigRepository = mailConfigRepository;
        this.templateRenderer = templateRenderer;
        this.readOnlyGuard = readOnlyGuard;
        if (currentGlobalProvider() == null) {
            log.warn(
                    "Mail service starting without a global mail provider; transactional emails will not be delivered until one is configured");
        } else {
            log.info(
                    "Mail service initialized: provider={} sender={} dailyLimit={}",
                    mailing.provider(),
                    mailing.senderAddress(),
                    mailing.dailySendLimit());
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "email-worker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::processQueue, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::runCleanup, 1, 24, TimeUnit.HOURS);
    }

    private void runCleanup() {
        try {
            queueRepository.cleanupOldEntries(30);
            mailConfigRepository.cleanupOldCounts(60);
            log.debug("Email queue cleanup completed");
        } catch (Exception e) {
            log.error("Email queue cleanup failed", e);
        }
    }

    // -- Provider resolution --

    /**
     * Resolves the station-specific mail provider based on the station's mail configuration.
     * Does not fall back to the global provider; returns empty if no station config exists.
     *
     * @param stationId the station ID to resolve a provider for
     * @return the configured mail provider, or empty if not configured
     */
    public Optional<MailProvider> resolveStationProvider(Integer stationId) {
        if (stationId == null) return Optional.empty();
        var config = mailConfigRepository.findByStation(stationId);
        if (config.isEmpty() || !config.get().isConfigured()) return Optional.empty();
        var c = config.get();
        return Optional.ofNullable(buildProvider(
                c.provider(),
                c.smtpHost(),
                c.smtpPort(),
                c.smtpSsl(),
                c.smtpUser(),
                c.smtpPassword(),
                c.apiKey(),
                c.senderAddress(),
                c.senderName()));
    }

    /**
     * Builds a {@link MailProvider} from raw config values without persisting anything. Returns
     * {@code null} when the provider is {@link MailProviderType#NONE}.
     */
    private static MailProvider buildProvider(
            MailProviderType provider,
            String smtpHost,
            int smtpPort,
            boolean smtpSsl,
            String user,
            String password,
            String apiKey,
            String senderAddress,
            String senderName) {
        return switch (provider) {
            case SMTP -> new SmtpMailProvider(smtpHost, smtpPort, smtpSsl, user, password, senderAddress, senderName);
            case RAPIDMAIL ->
                new SmtpMailProvider("smtp.rapidmail.de", 587, false, user, apiKey, senderAddress, senderName);
            case TWILIO ->
                new SmtpMailProvider(
                        "smtp.sendgrid.net",
                        587,
                        false,
                        "apikey",
                        apiKey,
                        senderAddress,
                        senderName,
                        SENDGRID_CORRELATION_HEADER,
                        SENDGRID_CORRELATION_FORMAT);
            // Sweego gives every account its own relay host and port, so both come from the
            // configuration rather than from a constant that would be right for nobody.
            case SWEEGO ->
                new SmtpMailProvider(
                        smtpHost,
                        smtpPort,
                        smtpSsl,
                        user,
                        apiKey,
                        senderAddress,
                        senderName,
                        SWEEGO_CORRELATION_HEADER,
                        null);
            // Brevo carries this header through to its delivery events, which is what lets one
            // of those events be traced back to the mail it belongs to.
            case BREVO ->
                new SmtpMailProvider(
                        "smtp-relay.brevo.com",
                        587,
                        false,
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
    public String testMailConnection(
            MailProviderType provider,
            String smtpHost,
            int smtpPort,
            boolean smtpSsl,
            String user,
            String password,
            String apiKey,
            String senderAddress,
            String senderName) {
        MailProvider mailProvider =
                buildProvider(provider, smtpHost, smtpPort, smtpSsl, user, password, apiKey, senderAddress, senderName);
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
    public String testStationMailConnection(Integer stationId) {
        if (stationId == null) return "No mail provider configured";
        var config = mailConfigRepository.findByStation(stationId);
        if (config.isEmpty() || !config.get().isConfigured()) return "No mail provider configured";
        return testMailConnection(config.get());
    }

    /**
     * Attempts a real connection against a {@link StationMailConfig} without persisting anything.
     *
     * @return {@code null} on success, or the underlying error message on failure
     */
    public String testMailConnection(StationMailConfig config) {
        return testMailConnection(
                config.provider(),
                config.smtpHost(),
                config.smtpPort(),
                config.smtpSsl(),
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
     * Routes a per-station notification email through the station's own outbound mailbox.
     *
     * <p><strong>Use only for high-volume aggregate notifications</strong> (event reminders,
     * digests, attendance summaries) - anything where missing one is acceptable and where the
     * station's daily/monthly send caps should apply. Per-station relays may be unconfigured or
     * temporarily over their cap, in which case nothing is delivered.
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
     * Check if the station can still send emails today.
     */
    public boolean canStationSend(int stationId) {
        var config = mailConfigRepository.findByStation(stationId);
        if (config.isEmpty() || !config.get().isConfigured()) return false;
        var c = config.get();
        LocalDate today = LocalDate.now();
        return mailConfigRepository.getDailyCount(stationId, today) < c.dailyLimit()
                && mailConfigRepository.getMonthlyCount(stationId, today) < c.monthlyLimit();
    }

    // -- Station email (queued, with per-station limits checked on send) --

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
    public void sendTestEmail(String to, String name, String locale, Integer stationId) {
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
    public void sendTwoFactorResetNotice(String email, String name, String actorLabel, Instant resetAt, String locale) {
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

    // -- Public send methods (system, via global provider queue) --

    public void sendPasswordResetEmail(String email, String name, String token, String locale) {
        String url = api.baseUrl() + "/reset-password?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        enqueueGlobal(
                email, subject("reset-password", locale, null), loadTemplate("reset-password.html", locale, vars));
    }

    public void sendEmailChangeConfirmation(String newEmail, String name, String token, String locale) {
        String url = api.baseUrl() + "/confirm-email-change?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        vars.put("newEmail", newEmail);
        enqueueGlobal(newEmail, subject("email-change", locale, null), loadTemplate("email-change.html", locale, vars));
    }

    public void sendStationDeletionConfirmation(String email, String name, String token, String locale) {
        String url = api.baseUrl() + "/api/v1/public/confirm-station-delete?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        enqueueGlobal(
                email, subject("station-delete", locale, null), loadTemplate("station-delete.html", locale, vars));
    }

    public void sendApplicationVerifyEmail(
            String email, String name, String stationName, String token, String locale, Integer stationId) {
        String url = api.baseUrl() + "/apply/verify?token=" + token;
        var vars = baseVars(name, stationId);
        vars.put("stationName", stationName);
        vars.put("url", url);
        enqueueGlobal(
                email,
                subject("application-verify", locale, applicationPlaceholders(stationName)),
                loadTemplate("application-verify.html", locale, vars));
    }

    public void sendApplicationAcceptedEmail(
            String email, String name, String stationName, String token, String locale, Integer stationId) {
        String url = api.baseUrl() + "/set-password?token=" + token;
        var vars = baseVars(name, stationId);
        vars.put("stationName", stationName);
        vars.put("url", url);
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
        }
        enqueueGlobal(
                email,
                subject("application-accepted", locale, applicationPlaceholders(stationName)),
                loadTemplate("application-accepted.html", locale, vars));
    }

    public void sendApplicationDeniedEmail(
            String email, String name, String stationName, String reason, String locale, Integer stationId) {
        var vars = baseVars(name, stationId);
        vars.put("stationName", stationName);
        vars.put("reason", reason != null ? reason : "");
        enqueueGlobal(
                email,
                subject("application-denied", locale, applicationPlaceholders(stationName)),
                loadTemplate("application-denied.html", locale, vars));
    }

    public void sendApplicationReceivedEmail(
            String email, String name, String stationName, String locale, Integer stationId) {
        var vars = baseVars(name, stationId);
        vars.put("stationName", stationName);
        enqueueGlobal(
                email,
                subject("application-received", locale, applicationPlaceholders(stationName)),
                loadTemplate("application-received.html", locale, vars));
    }

    /**
     * Sends the transactional confirmation that a public waiting-list registration has been
     * recorded. Routed through the instance-wide mail relay rather than the station relay
     * because it is mandatory transactional mail, not aggregate notification traffic.
     */
    public void sendWaitlistRegistrationEmail(
            String email, String name, String accessToken, String stationName, String locale, Integer stationId) {
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
     * Sends the transactional reminder to confirm an outstanding waiting-list spot. Routed
     * through the instance-wide mail relay because it is mandatory transactional mail.
     */
    public void sendWaitlistConfirmReminderEmail(
            String email, String name, String accessToken, String stationName, String locale, Integer stationId) {
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
            String email, String name, String accessToken, String stationName, String locale, Integer stationId) {
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
            String email, String name, String stationName, String token, String locale, Integer stationId) {
        String url = api.baseUrl() + "/public/waitlist/verify?token=" + token;
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
     * Build and queue a station notification email.
     */
    public void sendStationNotification(
            int stationId,
            String recipientEmail,
            String recipientName,
            String stationName,
            String logoUrl,
            String locale,
            String category,
            String message) {
        var vars = new HashMap<String, String>();
        vars.put("name", recipientName);
        vars.put("baseUrl", api.baseUrl());
        vars.put("stationName", stationName);
        vars.put("category", category);
        vars.put("message", message);
        vars.put("actionUrl", api.baseUrl() + "/station/dashboard/overview");
        vars.put(
                "logoHtml",
                logoUrl != null && !logoUrl.isBlank()
                        ? "<img src=\"" + logoUrl + "\" alt=\"\" style=\"height:40px;border-radius:4px\">"
                        : "");

        String subject = stationName + ": " + category;
        String body = loadTemplate("station-notification.html", locale, vars);
        queueStationEmail(stationId, recipientEmail, subject, body);
    }

    public int queueSize() {
        return queueRepository.pendingCount();
    }

    // -- Station notification email builder --

    public int sentTodayCount() {
        return queueRepository.getDailyCount(LocalDate.now());
    }

    // -- Status --

    public int remainingToday() {
        return Math.max(0, mailing.dailySendLimit() - sentTodayCount());
    }

    public String loadTemplate(String name, String locale, Map<String, String> variables) {
        return templateRenderer.render(name, locale, variables);
    }

    private String subject(String key, String locale, Map<String, String> placeholders) {
        return templateRenderer.subject(key, locale, placeholders);
    }

    /**
     * Builds a {@link MailProvider} from the live instance-wide mail settings. Re-evaluated on every
     * call so runtime updates to the mailing config take effect without a restart.
     */
    private MailProvider currentGlobalProvider() {
        if (mailing.senderAddress().isBlank()) {
            return null;
        }
        return buildProvider(
                mailing.provider(),
                mailing.smtp().host(),
                mailing.smtp().port(),
                mailing.smtp().ssl(),
                mailing.user(),
                mailing.password(),
                mailing.apiKey(),
                mailing.senderAddress(),
                mailing.senderName());
    }

    // -- Queue --

    private String resolveProviderSenderName(Integer stationId) {
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
     * regardless of whether the originating station has configured its own outbound mailbox, and
     * the per-station daily/monthly send caps do not apply.
     */
    private void enqueueGlobal(String to, String subject, String htmlBody) {
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
        queueRepository.enqueue(to, subject, htmlBody, null);
        log.debug("Email queued to={} subject={}", to, subject);
    }

    // -- Template & helpers --

    /**
     * The provider whose turn it is for this mail, built from the chain it belongs to.
     *
     * @return the provider, or empty when the chain holds nothing or has been used up
     */
    private Optional<MailProvider> providerInTurn(List<MailChainEntry> chain, EmailQueueRepository.QueuedEmail email) {
        return chainService
                .at(chain, email.providerPosition())
                .map(entry -> buildProvider(
                        entry.provider(),
                        entry.smtpHost(),
                        entry.smtpPort(),
                        entry.smtpSsl(),
                        entry.smtpUser(),
                        entry.smtpPassword(),
                        entry.apiKey(),
                        entry.senderAddress(),
                        entry.senderName()));
    }

    /**
     * Counts a used-up attempt and, when the provider in turn has had all of its, hands the mail to
     * the next one.
     *
     * <p>This is what makes a relay that has stopped working survivable: the mail does not sit in
     * the queue being refused by the same route forever, it moves on to another.
     */
    private void countAttemptAndMaybeAdvance(EmailQueueRepository.QueuedEmail email) {
        var chain = email.stationId() == null ? chainService.forInstance() : chainService.forStation(email.stationId());
        int allowed = chainService
                .at(chain, email.providerPosition())
                .map(MailChainEntry::attempts)
                .orElse(1);
        queueRepository.countAttempt(email.id());
        if (email.attempts() + 1 < allowed) {
            log.warn(
                    "Email {} to {} failed on provider {}; {} attempt(s) left before the next one",
                    email.id(),
                    email.recipient(),
                    email.providerPosition(),
                    allowed - email.attempts() - 1);
            return;
        }
        if (email.providerPosition() + 1 >= chain.size()) {
            log.warn("Email {} to {} has exhausted every provider", email.id(), email.recipient());
            return;
        }
        queueRepository.advanceProvider(email.id());
        log.warn(
                "Email {} to {} moves from provider {} to {}",
                email.id(),
                email.recipient(),
                email.providerPosition(),
                email.providerPosition() + 1);
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
                MailProvider provider;
                if (email.stationId() != null) {
                    if (!readOnlyGuard.isWritable(email.stationId())) {
                        log.debug("Email {} requeued: station {} is read-only", email.id(), email.stationId());
                        queueRepository.requeue(email.id());
                        requeued++;
                        continue;
                    }
                    if (!canStationSend(email.stationId())) {
                        log.warn(
                                "Email {} failed: station {} has reached its daily or monthly send limit",
                                email.id(),
                                email.stationId());
                        queueRepository.markFailed(email.id());
                        failed++;
                        continue;
                    }
                    var inTurn = providerInTurn(chainService.forStation(email.stationId()), email);
                    if (inTurn.isEmpty()) {
                        log.warn(
                                "Email {} failed: station {} has no provider left to try",
                                email.id(),
                                email.stationId());
                        queueRepository.markFailed(email.id());
                        failed++;
                        continue;
                    }
                    provider = inTurn.get();
                } else {
                    var inTurn = providerInTurn(chainService.forInstance(), email);
                    MailProvider current = inTurn.orElse(null);
                    if (current == null) {
                        log.debug(
                                "Email {} deferred: global mail provider not configured; keeping it queued",
                                email.id());
                        queueRepository.requeue(email.id());
                        requeued++;
                        continue;
                    }
                    int remaining = remainingToday();
                    if (remaining <= 0) {
                        log.debug(
                                "Global daily send limit ({}) reached; deferring remaining emails",
                                mailing.dailySendLimit());
                        break;
                    }
                    provider = current;
                }

                var result =
                        provider.send(email.recipient(), email.subject(), email.body(), String.valueOf(email.id()));
                switch (result) {
                    case SENT -> {
                        queueRepository.markSent(email.id());
                        if (email.stationId() != null) {
                            mailConfigRepository.incrementDailyCount(email.stationId(), LocalDate.now());
                        } else {
                            queueRepository.incrementDailyCount(LocalDate.now());
                        }
                        sent++;
                    }
                    case TRANSIENT_FAILURE -> {
                        countAttemptAndMaybeAdvance(email);
                        queueRepository.requeue(email.id());
                        requeued++;
                    }
                    case PERMANENT_FAILURE -> {
                        log.warn(
                                "Email {} delivery to {} failed permanently; marking failed",
                                email.id(),
                                email.recipient());
                        queueRepository.markFailed(email.id());
                        failed++;
                    }
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

    private Map<String, String> baseVars(String name, Integer stationId) {
        var vars = new HashMap<String, String>();
        vars.put("name", name);
        vars.put("baseUrl", api.baseUrl());
        vars.put("senderName", resolveProviderSenderName(stationId));
        return vars;
    }

    private static Map<String, String> applicationPlaceholders(String stationName) {
        return Map.of("stationName", stationName != null ? stationName : "");
    }

    private static Map<String, String> waitlistPlaceholders(String stationName) {
        String suffix = stationName != null && !stationName.isEmpty() ? " - " + stationName : "";
        return Map.of("stationName", stationName != null ? stationName : "", "stationSuffix", suffix);
    }
}
