/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service.mail;

import dev.chojo.ember.feature.mail.entity.MailAttachment;
import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import jakarta.activation.DataHandler;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * SMTP-based implementation of {@link MailProvider}. Supports implicit TLS, required STARTTLS and,
 * when chosen on purpose, unencrypted connections. Used for sending emails through various providers
 * (direct SMTP, Rapidmail, Twilio SendGrid, Sweego, Brevo).
 */
public class SmtpMailProvider implements MailProvider {
    private static final Logger log = LoggerFactory.getLogger(SmtpMailProvider.class);

    private static final String HTML = "text/html; charset=UTF-8";

    /** How long to wait for the relay to accept the connection. */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    /** How long to wait on the relay for any one read or write once connected. */
    static final Duration IO_TIMEOUT = Duration.ofSeconds(60);

    private final String host;
    private final int port;
    private final SmtpEncryption encryption;
    private final String user;
    private final String password;
    private final String senderAddress;
    private final String senderName;
    private final @Nullable String correlationHeader;
    private final String correlationFormat;

    /**
     * Creates a new SMTP mail provider.
     *
     * @param host          the SMTP server hostname
     * @param port          the SMTP server port
     * @param encryption    how the connection is secured
     * @param user          the authentication username
     * @param password      the authentication password
     * @param senderAddress the sender email address (From header)
     * @param senderName    the sender display name
     */
    public SmtpMailProvider(
            String host,
            int port,
            SmtpEncryption encryption,
            String user,
            String password,
            String senderAddress,
            String senderName) {
        this(host, port, encryption, user, password, senderAddress, senderName, null, null);
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
            SmtpEncryption encryption,
            String user,
            String password,
            String senderAddress,
            String senderName,
            @Nullable String correlationHeader,
            @Nullable String correlationFormat) {
        this.host = host;
        this.port = port;
        this.encryption = encryption;
        this.user = user;
        this.password = password;
        this.senderAddress = senderAddress;
        this.senderName = senderName;
        this.correlationHeader = correlationHeader;
        this.correlationFormat = correlationFormat == null ? "%s" : correlationFormat;
    }

    @Override
    public SendResult send(
            String to,
            String subject,
            String htmlBody,
            @Nullable String correlationId,
            List<MailAttachment> attachments) {
        Session session = createSession();
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderAddress, senderName));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);
            if (attachments.isEmpty()) {
                message.setContent(htmlBody, HTML);
            } else {
                message.setContent(withAttachments(htmlBody, attachments));
            }
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

    /**
     * The text and the files of a mail as one mixed multipart, the text first, so a reader shows the
     * text and lists the files below it.
     */
    static MimeMultipart withAttachments(String htmlBody, List<MailAttachment> attachments) throws MessagingException {
        var multipart = new MimeMultipart("mixed");
        var text = new MimeBodyPart();
        text.setContent(htmlBody, HTML);
        multipart.addBodyPart(text);
        for (var attachment : attachments) {
            var file = new MimeBodyPart();
            file.setDataHandler(
                    new DataHandler(new ByteArrayDataSource(attachment.content(), attachment.contentType())));
            file.setFileName(attachment.fileName());
            file.setDisposition(Part.ATTACHMENT);
            multipart.addBodyPart(file);
        }
        return multipart;
    }

    /**
     * The session settings for one relay.
     *
     * <p>The timeouts are load bearing rather than housekeeping. Jakarta Mail waits forever by
     * default, and one worker thread sends every queued mail in turn, so a relay that accepts the
     * connection and then goes quiet would hold all outgoing mail for as long as the process runs.
     *
     * <p>STARTTLS is required, not merely offered to use: without that, anyone between us and the
     * relay can strip the relay's offer of encryption and read the login in the clear. An
     * unencrypted connection is only ever made when it was chosen as such.
     *
     * @param host       the SMTP server hostname
     * @param port       the SMTP server port
     * @param encryption how the connection is secured
     */
    static Properties sessionProperties(String host, int port, SmtpEncryption encryption) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", String.valueOf(CONNECT_TIMEOUT.toMillis()));
        props.put("mail.smtp.timeout", String.valueOf(IO_TIMEOUT.toMillis()));
        props.put("mail.smtp.writetimeout", String.valueOf(IO_TIMEOUT.toMillis()));
        switch (encryption) {
            case IMPLICIT_TLS -> props.put("mail.smtp.ssl.enable", "true");
            case STARTTLS -> {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            case NONE -> props.put("mail.smtp.starttls.enable", "false");
        }
        return props;
    }

    private Session createSession() {
        return Session.getInstance(sessionProperties(host, port, encryption), new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        });
    }
}
