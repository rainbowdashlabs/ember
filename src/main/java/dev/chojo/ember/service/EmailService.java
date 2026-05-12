/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.repository.EmailQueueRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Path TEMPLATE_DIR = Path.of("templates");

    private final Mailing mailing;
    private final Api api;
    private final Demo demoConfig;
    private final EmailQueueRepository queueRepository;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "email-worker");
        t.setDaemon(true);
        return t;
    });

    @Inject
    public EmailService(Mailing mailing, Api api, Demo demoConfig, EmailQueueRepository queueRepository) {
        this.mailing = mailing;
        this.api = api;
        this.demoConfig = demoConfig;
        this.queueRepository = queueRepository;
        scheduler.scheduleWithFixedDelay(this::processQueue, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> queueRepository.cleanupOldEntries(30), 1, 24, TimeUnit.HOURS);
    }

    // -- Public send methods --

    public void sendVerificationEmail(String email, String name, String token) {
        String url = api.baseUrl() + "/verify-email?token=" + token;
        var vars = baseVars(name);
        vars.put("url", url);
        enqueue(email, "Verify your email address", loadTemplate("verify-email.html", "en", vars));
    }

    public void sendPasswordSetupEmail(String email, String name, String token) {
        String url = api.baseUrl() + "/set-password?token=" + token;
        var vars = baseVars(name);
        vars.put("url", url);
        enqueue(email, "Set up your password", loadTemplate("set-password.html", "en", vars));
    }

    public void sendPasswordResetEmail(String email, String name, String token) {
        String url = api.baseUrl() + "/reset-password?token=" + token;
        var vars = baseVars(name);
        vars.put("url", url);
        enqueue(email, "Reset your password", loadTemplate("reset-password.html", "en", vars));
    }

    public void sendApplicationVerifyEmail(String email, String name, String stationName, String token, String locale) {
        String url = api.baseUrl() + "/apply/verify?token=" + token;
        var vars = baseVars(name);
        vars.put("stationName", stationName);
        vars.put("url", url);
        enqueue(
                email,
                resolveSubject(locale, "application-verify", stationName),
                loadTemplate("application-verify.html", locale, vars));
    }

    public void sendApplicationAcceptedEmail(
            String email, String name, String stationName, String token, String locale, Integer stationId) {
        String url = api.baseUrl() + "/set-password?token=" + token;
        var vars = baseVars(name);
        vars.put("stationName", stationName);
        vars.put("url", url);
        if (stationId != null) {
            vars.put("logoUrl", api.baseUrl() + "/api/v1/stations/" + stationId + "/logo");
        }
        enqueue(
                email,
                resolveSubject(locale, "application-accepted", stationName),
                loadTemplate("application-accepted.html", locale, vars));
    }

    public void sendApplicationDeniedEmail(
            String email, String name, String stationName, String reason, String locale) {
        var vars = baseVars(name);
        vars.put("stationName", stationName);
        vars.put("reason", reason != null ? reason : "");
        enqueue(
                email,
                resolveSubject(locale, "application-denied", stationName),
                loadTemplate("application-denied.html", locale, vars));
    }

    public void sendApplicationReceivedEmail(String email, String name, String stationName, String locale) {
        var vars = baseVars(name);
        vars.put("stationName", stationName);
        enqueue(
                email,
                resolveSubject(locale, "application-received", stationName),
                loadTemplate("application-received.html", locale, vars));
    }

    // -- Status --

    public int queueSize() {
        return queueRepository.pendingCount();
    }

    public int sentTodayCount() {
        return queueRepository.getDailyCount(LocalDate.now());
    }

    public int remainingToday() {
        return Math.max(0, mailing.dailySendLimit() - sentTodayCount());
    }

    // -- Queue --

    private void enqueue(String to, String subject, String htmlBody) {
        if (demoConfig.enabled()) {
            log.info("Demo mode: Suppressed email to={} subject={}", to, subject);
            return;
        }
        if (mailing.senderAddress().isBlank()) {
            log.warn("Mail not configured. Would send to={} subject={}", to, subject);
            return;
        }
        queueRepository.enqueue(to, subject, htmlBody);
        log.debug("Email queued to={} subject={}", to, subject);
    }

    private void processQueue() {
        try {
            int remaining = remainingToday();
            if (remaining <= 0) return;

            var batch = queueRepository.fetchPending(Math.min(remaining, 10));
            if (batch.isEmpty()) return;

            int sent = 0;
            for (var email : batch) {
                if (send(email.recipient(), email.subject(), email.body())) {
                    queueRepository.markSent(email.id());
                    queueRepository.incrementDailyCount(LocalDate.now());
                    sent++;
                } else {
                    queueRepository.markFailed(email.id());
                }
            }

            if (sent > 0) {
                log.info(
                        "Sent {} emails ({} pending, {}/{} daily)",
                        sent,
                        queueRepository.pendingCount(),
                        sentTodayCount(),
                        mailing.dailySendLimit());
            }
        } catch (Exception e) {
            log.error("Error processing email queue", e);
        }
    }

    // -- Sending --

    private boolean send(String to, String subject, String htmlBody) {
        Session session = createSession();
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(mailing.senderAddress(), mailing.senderName()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=UTF-8");
            message.setSentDate(new Date());
            Transport.send(message, mailing.user(), mailing.password());
            log.info("Email sent to {}: {}", to, subject);
            return true;
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send email to {}", to, e);
            return false;
        }
    }

    // -- Template & helpers --

    private Map<String, String> baseVars(String name) {
        var vars = new HashMap<String, String>();
        vars.put("name", name);
        vars.put("baseUrl", api.baseUrl());
        vars.put("senderName", mailing.senderName());
        return vars;
    }

    private String resolveSubject(String locale, String template, String stationName) {
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
            default -> template;
        };
    }

    private String loadTemplate(String name, String locale, Map<String, String> variables) {
        String template = readTemplate(name, locale);
        for (var entry : variables.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
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

    private Session createSession() {
        return Session.getInstance(mailing.properties(), new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(mailing.user(), mailing.password());
            }
        });
    }
}
