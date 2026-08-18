/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service.mail;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;

/**
 * SMTP-based implementation of {@link MailProvider}. Supports both direct SSL and STARTTLS connections.
 * Used for sending emails through various providers (direct SMTP, Rapidmail, Twilio SendGrid, Sweego, Brevo).
 */
public class SmtpMailProvider implements MailProvider {
    private static final Logger log = LoggerFactory.getLogger(SmtpMailProvider.class);

    private final String host;
    private final int port;
    private final boolean ssl;
    private final String user;
    private final String password;
    private final String senderAddress;
    private final String senderName;
    private final String correlationHeader;
    private final String correlationFormat;

    /**
     * Creates a new SMTP mail provider.
     *
     * @param host          the SMTP server hostname
     * @param port          the SMTP server port
     * @param ssl           true for direct SSL, false for STARTTLS
     * @param user          the authentication username
     * @param password      the authentication password
     * @param senderAddress the sender email address (From header)
     * @param senderName    the sender display name
     */
    public SmtpMailProvider(
            String host, int port, boolean ssl, String user, String password, String senderAddress, String senderName) {
        this(host, port, ssl, user, password, senderAddress, senderName, null, null);
    }

    /**
     * Creates a new SMTP mail provider that tags its messages for delivery tracking.
     *
     * @param correlationHeader the header this relay carries through to its delivery events, or
     *                          null for a relay that reports nothing back
     * @param correlationFormat how the token has to be written into that header, as a format string
     *                          taking the token. Relays differ: one carries a plain value through,
     *                          another expects a small JSON document.
     */
    public SmtpMailProvider(
            String host,
            int port,
            boolean ssl,
            String user,
            String password,
            String senderAddress,
            String senderName,
            String correlationHeader,
            String correlationFormat) {
        this.host = host;
        this.port = port;
        this.ssl = ssl;
        this.user = user;
        this.password = password;
        this.senderAddress = senderAddress;
        this.senderName = senderName;
        this.correlationHeader = correlationHeader;
        this.correlationFormat = correlationFormat == null ? "%s" : correlationFormat;
    }

    @Override
    public SendResult send(String to, String subject, String htmlBody, String correlationId) {
        Session session = createSession();
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderAddress, senderName));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=UTF-8");
            message.setSentDate(new Date());
            if (correlationHeader != null && correlationId != null) {
                message.setHeader(correlationHeader, correlationFormat.formatted(correlationId));
            }
            Transport.send(message, user, password);
            log.info("SMTP email sent to {}: {}", to, subject);
            return SendResult.SENT;
        } catch (AuthenticationFailedException | AddressException | UnsupportedEncodingException e) {
            log.error("Permanent SMTP failure delivering to {}: {}", to, e.getMessage());
            return SendResult.PERMANENT_FAILURE;
        } catch (SendFailedException e) {
            log.error("Recipient {} rejected by SMTP server: {}", to, e.getMessage());
            return SendResult.PERMANENT_FAILURE;
        } catch (MessagingException e) {
            if (e.getCause() instanceof IOException) {
                log.warn("Transient SMTP failure delivering to {} (will retry): {}", to, e.getMessage());
                return SendResult.TRANSIENT_FAILURE;
            }
            log.warn("SMTP failure delivering to {} (treating as transient, will retry): {}", to, e.getMessage());
            return SendResult.TRANSIENT_FAILURE;
        }
    }

    @Override
    public TestResult testConnection() {
        try {
            Session session = createSession();
            Transport transport = session.getTransport("smtp");
            transport.connect(host, port, user, password);
            transport.close();
            return TestResult.ok();
        } catch (AuthenticationFailedException e) {
            return new TestResult("The mail server rejected the login credentials (" + e.getMessage() + ").", true);
        } catch (Exception e) {
            return new TestResult(e.getMessage(), false);
        }
    }

    public String senderName() {
        return senderName;
    }

    private Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        });
    }
}
