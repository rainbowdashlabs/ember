/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.mail.entity.MailAttachment;
import dev.chojo.ember.feature.mail.entity.SignedCopy;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.notifications.entity.LinkHome;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequestView;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

/**
 * Mails each signer their own copy of what they signed, once a sealed version carries their signature.
 *
 * <p>The copy names the document, the member it is about, who signed and when, and the SHA-256 of the
 * first sealed version that carries the signature. That hash is the point of the copy: a sealed file cannot
 * print its own hash, and a hash kept in the signer's mailbox, outside the installation, is what makes a
 * version produced later and claimed to be that one tell itself apart. The mail links to the document in
 * Ember, to the readable record of that version beside it, and to the verification page.
 *
 * <p>The PDF itself goes along only where the request copied that from its template, since a consent form
 * may hold health data and a mailbox is no place for it, and only up to {@link #MAX_ATTACHMENT_BYTES}, which
 * most mailboxes still take; a larger one is left out and the mail says why.
 *
 * <p>The copy goes to the account whose step-up confirmed the act, through {@link MailRecipientService}, so
 * the guardians read it where that account has no address of its own. It is written in the station's
 * language and goes through the instance-wide relay. It is queued in the transaction that files the version,
 * so a copy exists exactly when its version does. A copy that cannot be put together, such as for a template
 * that does not render, is logged and left out, and the version stays filed; a database failure while queueing
 * it fails the filing with it, and the sweep files both again.
 */
@Singleton
public class SignedCopies {
    private static final Logger log = LoggerFactory.getLogger(SignedCopies.class);

    /** The largest sealed PDF that goes along with a copy. */
    static final int MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

    private static final String VERIFY_PATH = "/verify";
    private static final int MAX_CAUSE_DEPTH = 12;

    private final EmailService email;
    private final MailRecipientService mailRecipients;
    private final StationRepository stations;
    private final NotificationText text;

    @Inject
    public SignedCopies(
            EmailService email,
            MailRecipientService mailRecipients,
            StationRepository stations,
            NotificationText text) {
        this.email = email;
        this.mailRecipients = mailRecipients;
        this.stations = stations;
        this.text = text;
    }

    /**
     * Queues the copies of the acts a sealed version carries for the first time.
     *
     * @param view         the request as it was sealed, with its fields and evidence
     * @param document     the member document the version was filed to
     * @param version      the version's number within the document
     * @param sealedPdf    the sealed version
     * @param sealedSha256 its SHA-256, lower-case hexadecimal
     * @param carried      the evidence ids of the acts it carries for the first time
     */
    public void queue(
            SignatureRequestView view,
            Document document,
            int version,
            byte[] sealedPdf,
            String sealedSha256,
            List<Integer> carried) {
        if (carried.isEmpty()) return;
        try {
            var station = stations.findById(view.request().stationId()).orElse(null);
            if (station == null) return;
            var attachment = attachmentOf(view, document, sealedPdf);
            boolean tooLarge = view.request().copyAttached() && attachment == null;
            for (var act : view.evidence()) {
                if (!carried.contains(act.id())) continue;
                send(view, station, document, version, act, sealedSha256, attachment, tooLarge);
            }
        } catch (RuntimeException e) {
            if (fromTheDatabase(e)) throw e;
            log.warn(
                    "Could not queue the signed copies of signing request {}",
                    view.request().uid(),
                    e);
        }
    }

    /**
     * Whether a failure came from the database. Such a failure has already spoilt the transaction the
     * version is filed in, so swallowing it would let the filing look done while nothing of it is kept.
     */
    private static boolean fromTheDatabase(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SQLException) return true;
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    private void send(
            SignatureRequestView view,
            Station station,
            Document document,
            int version,
            StoredEvidence evidence,
            String sealedSha256,
            @Nullable MailAttachment attachment,
            boolean tooLarge) {
        var act = evidence.evidence().act();
        var zone = StationFormat.timezoneOf(station);
        var format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(StationFormat.localeOf(station))
                .withZone(zone);
        String baseUrl = email.getBaseUrl();
        var copy = new SignedCopy(
                station.name(),
                document.title(),
                view.request().memberName(),
                act.signerName(),
                format.format(act.signedAt()),
                sealedSha256,
                linkOf(view, evidence, station, baseUrl, NotificationLinks.ownDocument(document.id())),
                linkOf(view, evidence, station, baseUrl, NotificationLinks.ownDocumentRecord(document.id(), version)),
                baseUrl + VERIFY_PATH,
                attachment,
                tooLarge);
        String locale = StationFormat.languageOf(station);
        for (var recipient : mailRecipients.forAccount(act.signer().accountId())) {
            email.sendSignedCopy(recipient.email(), recipient.name(), recipient.guardian(), copy, locale);
        }
    }

    /**
     * Where the copy leads in Ember: the document, or its record, among the reader's own documents, which
     * list the documents of the members in their care as well; or the member's page for the issuer, whose
     * document is somebody else's.
     */
    private String linkOf(
            SignatureRequestView view,
            StoredEvidence evidence,
            Station station,
            String baseUrl,
            NotificationLink ownDocuments) {
        Integer memberId = view.request().memberId();
        boolean issuer = view.fields().stream()
                .filter(field -> field.id() == evidence.fieldId())
                .map(RequestedSignature::role)
                .anyMatch(FieldRole.ISSUER::equals);
        var link = issuer && memberId != null ? NotificationLinks.member(memberId) : ownDocuments;
        return text.resolveLinkUrl(baseUrl, LinkHome.station(station.uid()), link);
    }

    private static @Nullable MailAttachment attachmentOf(
            SignatureRequestView view, Document document, byte[] sealedPdf) {
        if (!view.request().copyAttached() || sealedPdf.length > MAX_ATTACHMENT_BYTES) return null;
        return new MailAttachment(fileNameOf(document), MailAttachment.PDF, sealedPdf);
    }

    private static String fileNameOf(Document document) {
        String name = document.fileName();
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? name : name + ".pdf";
    }
}
