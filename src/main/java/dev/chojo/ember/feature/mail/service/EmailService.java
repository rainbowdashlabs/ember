/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.service.mail.MailProvider;
import dev.chojo.ember.feature.mail.service.mail.SmtpMailProvider;
import dev.chojo.ember.feature.station.repository.StationMailConfigRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
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
    private static final Path TEMPLATE_DIR = Path.of("templates");

    private final Mailing mailing;
    private final Api api;
    private final Demo demoConfig;
    private final EmailQueueRepository queueRepository;
    private final StationMailConfigRepository mailConfigRepository;
    private final MailProvider globalProvider;

    @Inject
    public EmailService(
            Mailing mailing,
            Api api,
            Demo demoConfig,
            EmailQueueRepository queueRepository,
            StationMailConfigRepository mailConfigRepository) {
        this.mailing = mailing;
        this.api = api;
        this.demoConfig = demoConfig;
        this.queueRepository = queueRepository;
        this.mailConfigRepository = mailConfigRepository;
        this.globalProvider = createGlobalProvider();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "email-worker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::processQueue, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(
                () -> {
                    queueRepository.cleanupOldEntries(30);
                    mailConfigRepository.cleanupOldCounts(60);
                },
                1,
                24,
                TimeUnit.HOURS);
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
        if (config.isPresent() && config.get().isConfigured()) {
            var c = config.get();
            return Optional.of(
                    switch (c.provider()) {
                        case SMTP ->
                            new SmtpMailProvider(
                                    c.smtpHost(),
                                    c.smtpPort(),
                                    c.smtpSsl(),
                                    c.smtpUser(),
                                    c.smtpPassword(),
                                    c.senderAddress(),
                                    c.senderName());
                        case RAPIDMAIL ->
                            new SmtpMailProvider(
                                    "smtp.rapidmail.de",
                                    587,
                                    false,
                                    c.smtpUser(),
                                    c.apiKey(),
                                    c.senderAddress(),
                                    c.senderName());
                        case TWILIO ->
                            new SmtpMailProvider(
                                    "smtp.sendgrid.net",
                                    587,
                                    false,
                                    "apikey",
                                    c.apiKey(),
                                    c.senderAddress(),
                                    c.senderName());
                        case SWEEGO ->
                            new SmtpMailProvider(
                                    "smtp.sweego.io",
                                    587,
                                    false,
                                    c.smtpUser(),
                                    c.apiKey(),
                                    c.senderAddress(),
                                    c.senderName());
                        case BREVO ->
                            new SmtpMailProvider(
                                    "smtp-relay.brevo.com",
                                    587,
                                    false,
                                    c.smtpUser(),
                                    c.apiKey(),
                                    c.senderAddress(),
                                    c.senderName());
                        case NONE -> throw new IllegalStateException("NONE should not reach here");
                    });
        }
        return Optional.empty();
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
     * Queue a station notification email. Limit checks happen at send time.
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
    public void sendVerificationEmail(String email, String name, String token) {
        String url = api.baseUrl() + "/verify-email?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        enqueueGlobal(email, "Verify your email address", loadTemplate("verify-email.html", "en", vars));
    }

    public void sendPasswordSetupEmail(String email, String name, String token) {
        String url = api.baseUrl() + "/set-password?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        enqueueGlobal(email, "Set up your password", loadTemplate("set-password.html", "en", vars));
    }

    /**
     * Notifies an existing account holder that someone attempted to register a new account
     * using their email address. Sent in lieu of returning a duplicate-email error to the
     * registration caller, so the public registration endpoint cannot be used to enumerate
     * existing addresses.
     */
    public void sendDuplicateRegistrationNotice(String email, String name) {
        var vars = baseVars(name, null);
        vars.put("loginUrl", api.baseUrl() + "/login");
        enqueueGlobal(email, "Account already exists", loadTemplate("duplicate-registration.html", "en", vars));
    }

    // -- Public send methods (system, via global provider queue) --

    public void sendPasswordResetEmail(String email, String name, String token) {
        String url = api.baseUrl() + "/reset-password?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        enqueueGlobal(email, "Reset your password", loadTemplate("reset-password.html", "en", vars));
    }

    public void sendEmailChangeConfirmation(String newEmail, String name, String token) {
        String url = api.baseUrl() + "/confirm-email-change?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        vars.put("newEmail", newEmail);
        enqueueGlobal(newEmail, "Confirm your new email address", loadTemplate("email-change.html", "en", vars));
    }

    public void sendStationDeletionConfirmation(String email, String name, String token) {
        String url = api.baseUrl() + "/api/v1/public/confirm-station-delete?token=" + token;
        var vars = baseVars(name, null);
        vars.put("url", url);
        enqueueGlobal(email, "Confirm station deletion", loadTemplate("station-delete.html", "en", vars));
    }

    public void sendApplicationVerifyEmail(
            String email, String name, String stationName, String token, String locale, Integer stationId) {
        String url = api.baseUrl() + "/apply/verify?token=" + token;
        var vars = baseVars(name, stationId);
        vars.put("stationName", stationName);
        vars.put("url", url);
        enqueueGlobal(
                email,
                resolveSubject(locale, "application-verify", stationName),
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
                resolveSubject(locale, "application-accepted", stationName),
                loadTemplate("application-accepted.html", locale, vars));
    }

    public void sendApplicationDeniedEmail(
            String email, String name, String stationName, String reason, String locale, Integer stationId) {
        var vars = baseVars(name, stationId);
        vars.put("stationName", stationName);
        vars.put("reason", reason != null ? reason : "");
        enqueueGlobal(
                email,
                resolveSubject(locale, "application-denied", stationName),
                loadTemplate("application-denied.html", locale, vars));
    }

    public void sendApplicationReceivedEmail(
            String email, String name, String stationName, String locale, Integer stationId) {
        var vars = baseVars(name, stationId);
        vars.put("stationName", stationName);
        enqueueGlobal(
                email,
                resolveSubject(locale, "application-received", stationName),
                loadTemplate("application-received.html", locale, vars));
    }

    public void sendWaitlistRegistrationEmail(
            String email, String name, String accessToken, String stationName, String locale, Integer stationId) {
        String url = api.baseUrl() + "/waiting-list/status?token=" + accessToken;
        var vars = baseVars(name, stationId);
        vars.put("url", url);
        vars.put("stationName", stationName != null ? stationName : "");
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
            queueStationEmail(
                    stationId,
                    email,
                    resolveSubject(locale, "waitlist-registered", stationName),
                    loadTemplate("waitlist-registered.html", locale, vars));
        } else {
            enqueueGlobal(
                    email,
                    resolveSubject(locale, "waitlist-registered", stationName),
                    loadTemplate("waitlist-registered.html", locale, vars));
        }
    }

    public void sendWaitlistConfirmReminderEmail(
            String email, String name, String accessToken, String stationName, String locale, Integer stationId) {
        String url = api.baseUrl() + "/waiting-list/status?token=" + accessToken;
        var vars = baseVars(name, stationId);
        vars.put("url", url);
        vars.put("stationName", stationName != null ? stationName : "");
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
            queueStationEmail(
                    stationId,
                    email,
                    resolveSubject(locale, "waitlist-confirm-reminder", stationName),
                    loadTemplate("waitlist-confirm-reminder.html", locale, vars));
        } else {
            enqueueGlobal(
                    email,
                    resolveSubject(locale, "waitlist-confirm-reminder", stationName),
                    loadTemplate("waitlist-confirm-reminder.html", locale, vars));
        }
    }

    public void sendWaitlistRemovalWarningEmail(
            String email, String name, String accessToken, String stationName, String locale, Integer stationId) {
        String url = api.baseUrl() + "/waiting-list/status?token=" + accessToken;
        var vars = baseVars(name, stationId);
        vars.put("url", url);
        vars.put("stationName", stationName != null ? stationName : "");
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
            queueStationEmail(
                    stationId,
                    email,
                    resolveSubject(locale, "waitlist-removal-warning", stationName),
                    loadTemplate("waitlist-removal-warning.html", locale, vars));
        } else {
            enqueueGlobal(
                    email,
                    resolveSubject(locale, "waitlist-removal-warning", stationName),
                    loadTemplate("waitlist-removal-warning.html", locale, vars));
        }
    }

    public void sendWaitlistVerifyEmail(
            String email, String name, String stationName, String token, String locale, Integer stationId) {
        String url = api.baseUrl() + "/public/waitlist/verify?token=" + token;
        var vars = baseVars(name, stationId);
        vars.put("url", url);
        vars.put("stationName", stationName != null ? stationName : "");
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
            queueStationEmail(
                    stationId,
                    email,
                    resolveSubject(locale, "waitlist-verify", stationName),
                    loadTemplate("waitlist-verify.html", locale, vars));
        } else {
            enqueueGlobal(
                    email,
                    resolveSubject(locale, "waitlist-verify", stationName),
                    loadTemplate("waitlist-verify.html", locale, vars));
        }
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
        String template = readTemplate(name, locale);
        for (var entry : variables.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }

    private MailProvider createGlobalProvider() {
        if (mailing.senderAddress().isBlank()) {
            return null;
        }

        return switch (mailing.provider()) {
            case SMTP ->
                new SmtpMailProvider(
                        mailing.smtp().host(),
                        mailing.smtp().port(),
                        mailing.smtp().ssl(),
                        mailing.user(),
                        mailing.password(),
                        mailing.senderAddress(),
                        mailing.senderName());
            case RAPIDMAIL ->
                new SmtpMailProvider(
                        "smtp.rapidmail.de",
                        587,
                        false,
                        mailing.user(),
                        mailing.apiKey(),
                        mailing.senderAddress(),
                        mailing.senderName());
            case TWILIO ->
                new SmtpMailProvider(
                        "smtp.sendgrid.net",
                        587,
                        false,
                        "apikey",
                        mailing.apiKey(),
                        mailing.senderAddress(),
                        mailing.senderName());
            case SWEEGO ->
                new SmtpMailProvider(
                        "smtp.sweego.io",
                        587,
                        false,
                        mailing.user(),
                        mailing.apiKey(),
                        mailing.senderAddress(),
                        mailing.senderName());
            case BREVO ->
                new SmtpMailProvider(
                        "smtp-relay.brevo.com",
                        587,
                        false,
                        mailing.user(),
                        mailing.apiKey(),
                        mailing.senderAddress(),
                        mailing.senderName());
            case NONE -> null;
        };
    }

    // -- Queue --

    private String resolveProviderSenderName(Integer stationId) {
        var provider = resolveStationProvider(stationId);
        if (provider.isPresent() && provider.get() instanceof SmtpMailProvider smtp) {
            return smtp.senderName();
        }
        return mailing.senderName();
    }

    private void enqueueGlobal(String to, String subject, String htmlBody) {
        if (demoConfig.enabled()) {
            log.info("Demo mode: Suppressed email to={} subject={}", to, subject);
            return;
        }
        if (globalProvider == null) {
            log.warn("Mail not configured. Would send to={} subject={}", to, subject);
            return;
        }
        queueRepository.enqueue(to, subject, htmlBody, null);
        log.debug("Email queued to={} subject={}", to, subject);
    }

    // -- Template & helpers --

    private void processQueue() {
        try {
            var batch = queueRepository.fetchPending(20);
            if (batch.isEmpty()) return;

            int sent = 0;
            for (var email : batch) {
                MailProvider provider;
                if (email.stationId() != null) {
                    // Station email — check limits and use station provider
                    if (!canStationSend(email.stationId())) {
                        queueRepository.markFailed(email.id());
                        continue;
                    }
                    var stationProvider = resolveStationProvider(email.stationId());
                    if (stationProvider.isEmpty()) {
                        queueRepository.markFailed(email.id());
                        continue;
                    }
                    provider = stationProvider.get();
                } else {
                    // Global system email
                    if (globalProvider == null) {
                        queueRepository.markFailed(email.id());
                        continue;
                    }
                    int remaining = remainingToday();
                    if (remaining <= 0) break;
                    provider = globalProvider;
                }

                if (provider.send(email.recipient(), email.subject(), email.body())) {
                    queueRepository.markSent(email.id());
                    if (email.stationId() != null) {
                        mailConfigRepository.incrementDailyCount(email.stationId(), LocalDate.now());
                    } else {
                        queueRepository.incrementDailyCount(LocalDate.now());
                    }
                    sent++;
                } else {
                    queueRepository.markFailed(email.id());
                }
            }

            if (sent > 0) {
                log.info("Sent {} emails ({} pending)", sent, queueRepository.pendingCount());
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

    private String resolveSubject(String locale, String template, String stationName) {
        final String string = stationName != null && !stationName.isEmpty() ? " — " + stationName : "";
        return switch (template) {
            case "application-verify" ->
                "de".equals(locale)
                        ? "Bestätige deinen Antrag für " + stationName
                        : "Confirm your application for " + stationName;
            case "application-accepted" ->
                "de".equals(locale)
                        ? "Dein Antrag für " + stationName + " wurde angenommen"
                        : "Your application for " + stationName + " has been accepted";
            case "application-denied" ->
                "de".equals(locale) ? "Dein Antrag für " + stationName : "Your application for " + stationName;
            case "application-received" ->
                "de".equals(locale)
                        ? "Antrag für " + stationName + " eingegangen"
                        : "Application for " + stationName + " received";
            case "waitlist-verify" ->
                "de".equals(locale)
                        ? "Wartelisten-Anmeldung bestätigen" + string
                        : "Confirm waiting list registration" + string;
            case "waitlist-registered" ->
                "de".equals(locale) ? "Wartelisten-Anmeldung" + string : "Waiting list registration" + string;
            case "waitlist-confirm-reminder" ->
                "de".equals(locale)
                        ? "Bitte bestätige dein Interesse" + string
                        : "Please confirm your interest" + string;
            case "waitlist-removal-warning" ->
                "de".equals(locale)
                        ? "Wartelisten-Entfernung in 2 Wochen" + string
                        : "Waiting list removal in 2 weeks" + string;
            default -> template;
        };
    }

    private String readTemplate(String name, String locale) {
        Path localeFile = TEMPLATE_DIR.resolve("mail").resolve(locale).resolve(name);
        if (Files.exists(localeFile)) {
            try {
                return Files.readString(localeFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Failed to read template {}", localeFile, e);
            }
        }
        Path fallback = TEMPLATE_DIR.resolve("mail").resolve("en").resolve(name);
        try {
            return Files.readString(fallback, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Template not found: " + fallback, e);
        }
    }
}
