/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import jakarta.mail.Authenticator;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Part;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Reading a mailbox, as far as the envelope and the attachments and no further.
 *
 * <p>Nothing here looks at a message body. What is read is who sent it, what it is called, and which
 * files hang off it, and everything else goes with the message.
 *
 * <p><b>The structure is read before anything is downloaded.</b> A part whose declared type and size are
 * already out of bounds is never fetched, so a 40 MB video attached to a mail nobody wants does not
 * cross the network to be refused. What survives that is fetched and then sniffed, with the bytes as the
 * authority: a part that lied about its type is caught after the download and refused, and one that told
 * the truth about being enormous is never downloaded at all.
 *
 * <p>The timeouts are load bearing rather than housekeeping. One thread walks every station's mailboxes
 * in turn, so a host that neither fails nor answers would hold all of them for as long as the socket took
 * to give up. Backoff cannot help there, because backoff answers a mailbox that has already failed.
 */
public class MailboxReader implements AutoCloseable {

    /**
     * One file hanging off a message, with its bytes where they were worth fetching.
     *
     * @param fileName     what the file is called in the mail
     * @param declaredType what the mail claims it is, which is never trusted for anything but the first cut
     * @param declaredSize how big the mail claims it is, or -1 where it does not say
     * @param inline       whether it is embedded rather than attached
     * @param data         the bytes, or null where the part was refused before being fetched
     */
    public record Attachment(String fileName, String declaredType, long declaredSize, boolean inline, byte[] data) {}

    /**
     * One message, as much of it as this feature is allowed to know.
     *
     * @param messageId   its own identifier, or null where it carried none
     * @param sender      the bare address it came from
     * @param subject     what it is called
     * @param receivedAt  when it arrived
     * @param authResult  the receiving server's verdict on the sending domain, or null where it wrote none
     * @param attachments the files hanging off it
     */
    public record Envelope(
            String messageId,
            String sender,
            String subject,
            Instant receivedAt,
            String authResult,
            List<Attachment> attachments) {}

    private static final String AUTHENTICATION_RESULTS = "Authentication-Results";

    private final Store store;
    private Folder folder;

    /**
     * Opens a connection to a mailbox.
     *
     * @param timeoutSeconds how long to wait on the host, for connecting and for reading alike
     * @throws MessagingException where the host, the credentials or the encryption do not work out
     */
    public MailboxReader(
            String host, int port, MailSecurity security, String username, String password, int timeoutSeconds)
            throws MessagingException {
        this.store = openStore(host, port, security, username, password, timeoutSeconds);
    }

    private static Store openStore(
            String host, int port, MailSecurity security, String username, String password, int timeoutSeconds)
            throws MessagingException {
        String millis = String.valueOf(timeoutSeconds * 1000L);
        Properties props = new Properties();
        props.put("mail.store.protocol", "imap");
        props.put("mail.imap.host", host);
        props.put("mail.imap.port", String.valueOf(port));
        props.put("mail.imap.connectiontimeout", millis);
        props.put("mail.imap.timeout", millis);
        props.put("mail.imap.writetimeout", millis);
        props.put("mail.imap.partialfetch", "false");
        if (security == MailSecurity.SSL) {
            props.put("mail.imap.ssl.enable", "true");
        } else if (security == MailSecurity.STARTTLS) {
            props.put("mail.imap.starttls.enable", "true");
            props.put("mail.imap.starttls.required", "true");
        }
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        Store store = store(session);
        store.connect(host, port, username, password);
        return store;
    }

    private static Store store(Session session) throws NoSuchProviderException {
        return session.getStore("imap");
    }

    /**
     * The folders this account can see, which is what a connection test reports.
     *
     * <p>A test that only said "connected" would not catch the commonest mistake there is, which is a
     * folder spelled the way the person says it rather than the way the provider does.
     */
    public List<String> folders() throws MessagingException {
        List<String> names = new ArrayList<>();
        for (Folder candidate : store.getDefaultFolder().list("*")) {
            names.add(candidate.getFullName());
        }
        return names;
    }

    /** Opens the folder to be read, writably where the rule will need to mark or move anything. */
    public void open(String name, boolean writable) throws MessagingException {
        folder = store.getFolder(name);
        if (!folder.exists()) throw new MessagingException("No folder called " + name);
        folder.open(writable ? Folder.READ_WRITE : Folder.READ_ONLY);
    }

    /**
     * The messages that arrived on or after a moment, envelopes first.
     *
     * <p>Asking the server to do the narrowing is the difference between a first cycle that reads a
     * decade and one that reads a week. Where a server answers the search badly the date is checked again
     * here, since a mailbox importing ten years of attachments is not a failure anybody wants twice.
     *
     * @param since the moment the mailbox starts from
     * @param limit how many messages to look at this cycle
     */
    public List<Message> since(Instant since, int limit) throws MessagingException {
        Message[] found = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, Date.from(since)));
        List<Message> kept = new ArrayList<>();
        for (Message message : found) {
            if (kept.size() >= limit) break;
            Date received = message.getReceivedDate();
            if (received != null && received.toInstant().isBefore(since)) continue;
            kept.add(message);
        }
        return kept;
    }

    /**
     * Reads one message down to its attachments, fetching only the parts worth fetching.
     *
     * @param message       the message to read
     * @param includeInline whether embedded pictures count as files at all
     * @param acceptedTypes the types the rule accepts, used for the first cut on what the mail declares
     * @param maxBytes      the largest file the station allows, above which nothing is downloaded
     */
    public Envelope read(Message message, boolean includeInline, List<String> acceptedTypes, long maxBytes)
            throws MessagingException, IOException {
        String messageId = message instanceof MimeMessage mime ? mime.getMessageID() : null;
        String[] authResults = message.getHeader(AUTHENTICATION_RESULTS);
        Date received = message.getReceivedDate();
        List<Attachment> attachments = new ArrayList<>();
        collect(message, includeInline, acceptedTypes, maxBytes, attachments);
        return new Envelope(
                blankToNull(messageId),
                senderOf(message),
                blankToNull(message.getSubject()),
                received != null ? received.toInstant() : Instant.now(),
                authResults != null && authResults.length > 0 ? authResults[0] : null,
                attachments);
    }

    private void collect(
            Part part, boolean includeInline, List<String> acceptedTypes, long maxBytes, List<Attachment> into)
            throws MessagingException, IOException {
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i), includeInline, acceptedTypes, maxBytes, into);
            }
            return;
        }
        boolean attached = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition());
        boolean inline = !attached && part.getFileName() != null;
        if (!attached && !(inline && includeInline)) return;

        String fileName = part.getFileName();
        String declaredType = baseType(part.getContentType());
        long declaredSize = part.getSize();
        if (declaredSize > maxBytes) {
            into.add(new Attachment(fileName, declaredType, declaredSize, inline, null));
            return;
        }
        if (declaredType != null && !acceptedTypes.contains(declaredType) && !looksWorthLooking(fileName)) {
            into.add(new Attachment(fileName, declaredType, declaredSize, inline, null));
            return;
        }
        into.add(new Attachment(fileName, declaredType, declaredSize, inline, bytesOf(part, maxBytes)));
    }

    /**
     * Whether a part whose declared type is not one the rule wants is still worth downloading.
     *
     * <p>Mail servers and scanners declare PDFs as {@code application/octet-stream} often enough that
     * refusing on the declared type alone would drop the very files this feature exists to collect. So a
     * name that looks like something we accept buys the part a download and then a sniff, which is the
     * authority either way.
     */
    private static boolean looksWorthLooking(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private static byte[] bytesOf(Part part, long maxBytes) throws MessagingException, IOException {
        try (InputStream in = part.getInputStream()) {
            return in.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
        }
    }

    private static String baseType(String contentType) {
        if (contentType == null) return null;
        int semicolon = contentType.indexOf(';');
        String base = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private static String senderOf(Message message) throws MessagingException {
        var from = message.getFrom();
        if (from == null || from.length == 0) return null;
        if (from[0] instanceof InternetAddress address) return address.getAddress();
        return from[0].toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Marks a message read, which is the default answer to having dealt with one. */
    public void markSeen(Message message) throws MessagingException {
        message.setFlag(Flags.Flag.SEEN, true);
    }

    /** Flags a message, for a station that reads its own mail and wants to see what was taken. */
    public void flag(Message message) throws MessagingException {
        message.setFlag(Flags.Flag.FLAGGED, true);
    }

    /**
     * Moves a message to another folder, creating it where it is not there yet.
     *
     * <p>This is what a station with a busy mailbox wants, and it is offered where deleting is not:
     * moving does everything deleting does and can be undone by whoever finds out.
     */
    public void moveTo(Message message, String folderName) throws MessagingException {
        Folder destination = store.getFolder(folderName);
        if (!destination.exists() && !destination.create(Folder.HOLDS_MESSAGES)) {
            throw new MessagingException("Could not make a folder called " + folderName);
        }
        folder.copyMessages(new Message[] {message}, destination);
        message.setFlag(Flags.Flag.DELETED, true);
    }

    @Override
    public void close() {
        try {
            if (folder != null && folder.isOpen()) folder.close(true);
        } catch (MessagingException ignored) {
            // A folder that will not close cleanly has nothing left to tell us.
        }
        try {
            store.close();
        } catch (MessagingException ignored) {
            // Nor has a store.
        }
    }
}
