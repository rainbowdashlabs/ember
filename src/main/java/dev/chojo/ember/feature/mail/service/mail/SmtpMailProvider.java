/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service.mail;

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
        this.host = host;
        this.port = port;
        this.ssl = ssl;
        this.user = user;
        this.password = password;
        this.senderAddress = senderAddress;
        this.senderName = senderName;
    }

    @Override
    public boolean send(String to, String subject, String htmlBody) {
        Session session = createSession();
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderAddress, senderName));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=UTF-8");
            message.setSentDate(new Date());
            Transport.send(message, user, password);
            log.info("SMTP email sent to {}: {}", to, subject);
            return true;
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send SMTP email to {}", to, e);
            return false;
        }
    }

    @Override
    public String testConnection() {
        try {
            Session session = createSession();
            Transport transport = session.getTransport("smtp");
            transport.connect(host, port, user, password);
            transport.close();
            return null;
        } catch (Exception e) {
            return e.getMessage();
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
