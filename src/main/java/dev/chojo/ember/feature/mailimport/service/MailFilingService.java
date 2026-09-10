/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.mailimport.entity.MailImportOutcome;
import dev.chojo.ember.feature.mailimport.entity.MailMailbox;
import dev.chojo.ember.feature.mailimport.entity.MailRule;
import dev.chojo.ember.feature.mailimport.entity.MailTitleSource;
import dev.chojo.ember.feature.mailimport.repository.MailImportLogRepository;
import dev.chojo.ember.feature.mailimport.repository.MailOriginRepository;
import dev.chojo.ember.feature.mailimport.service.MailboxReader.Attachment;
import dev.chojo.ember.feature.mailimport.service.MailboxReader.Envelope;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * Turning one attachment into a document, or saying why it did not become one.
 *
 * <p>Every path through here ends in a log entry, including the ones that file nothing. A rule that
 * quietly imports nothing is the likeliest complaint this feature will produce, and the log is the only
 * thing that can answer it, so an attachment that is refused is refused out loud.
 */
@Singleton
public class MailFilingService {
    private static final Logger log = LoggerFactory.getLogger(MailFilingService.class);

    private final DocumentService documentService;
    private final MailImportLogRepository logRepository;
    private final MailOriginRepository originRepository;
    private final StorageQuotaService quotaService;
    private final MemberNaming memberNaming;

    /**
     * How the members of a station are looked up for the subject guess.
     *
     * <p>An interface rather than a repository call so that the guessing can be tested without a station
     * having to exist, since what is worth testing about it is the ambiguity and not the query.
     */
    public interface MemberNaming {
        /**
         * The station's members as candidates for a name in a subject.
         *
         * @param stationId the station
         * @return every member who could be named
         */
        List<SubjectMemberMatch.Candidate> candidates(int stationId);
    }

    @Inject
    public MailFilingService(
            DocumentService documentService,
            MailImportLogRepository logRepository,
            MailOriginRepository originRepository,
            StorageQuotaService quotaService,
            MemberNaming memberNaming) {
        this.documentService = documentService;
        this.logRepository = logRepository;
        this.originRepository = originRepository;
        this.quotaService = quotaService;
        this.memberNaming = memberNaming;
    }

    /**
     * Files one attachment, or records why it was not filed.
     *
     * @param mailbox    the mailbox it arrived in
     * @param rule       the rule that took the message
     * @param envelope   the message it hung off
     * @param attachment the file itself
     * @param messageId  the identity the message was recorded under
     * @return what became of it
     */
    public MailImportOutcome file(
            MailMailbox mailbox, MailRule rule, Envelope envelope, Attachment attachment, String messageId) {
        var recorded = new Recorded(mailbox, rule, envelope, messageId);
        if (attachment.data() == null) {
            return record(
                    recorded,
                    attachment,
                    null,
                    MailImportOutcome.TOO_LARGE,
                    "Refused before it was fetched, on the size or type the mail declared",
                    null);
        }

        long size = attachment.data().length;
        long ceiling = quotaService.perFileLimitBytes(mailbox.stationId());
        if (size > ceiling) {
            return record(
                    recorded,
                    attachment,
                    null,
                    MailImportOutcome.TOO_LARGE,
                    "%d bytes, over the station's limit of %d".formatted(size, ceiling),
                    null);
        }
        if (size < rule.minSizeBytes()) {
            return record(
                    recorded,
                    attachment,
                    null,
                    MailImportOutcome.TOO_SMALL,
                    "%d bytes, under the %d this rule counts as a document".formatted(size, rule.minSizeBytes()),
                    null);
        }

        String sniffed = ContentSniffer.sniff(attachment.data());
        if (sniffed == null) {
            return record(
                    recorded,
                    attachment,
                    null,
                    MailImportOutcome.TYPE_NOT_ALLOWED,
                    "The bytes are not a kind of file this can recognise",
                    null);
        }
        if (!ContentSniffer.nameAgrees(attachment.fileName(), sniffed)) {
            return record(
                    recorded,
                    attachment,
                    null,
                    MailImportOutcome.TYPE_NOT_ALLOWED,
                    "Named as one kind of file and made of another (%s)".formatted(sniffed),
                    null);
        }
        if (!rule.acceptedTypes().contains(sniffed)) {
            return record(
                    recorded,
                    attachment,
                    null,
                    MailImportOutcome.TYPE_NOT_ALLOWED,
                    "%s, which this rule does not accept".formatted(sniffed),
                    null);
        }

        String hash = MessageIdentity.hashOf(attachment.data());
        if (logRepository.hasImportedContent(mailbox.stationId(), hash)) {
            return record(
                    recorded,
                    attachment,
                    hash,
                    MailImportOutcome.DUPLICATE,
                    "These same bytes are already in this station's store",
                    null);
        }

        try {
            quotaService.checkQuota(mailbox.stationId(), StorageCategory.MEMBER_DOCUMENTS, size);
        } catch (StorageQuotaService.StorageQuotaExceededException e) {
            return record(
                    recorded,
                    attachment,
                    hash,
                    MailImportOutcome.QUOTA_EXCEEDED,
                    "The station has no room left for it",
                    null);
        }

        var document = documentService.store(
                mailbox.stationId(),
                membersFor(rule, envelope, mailbox.stationId()),
                titleFor(rule, envelope, attachment),
                fileNameFor(attachment, sniffed),
                sniffed,
                attachment.data(),
                rule.hidden(),
                rule.keepOnArchive(),
                null,
                rule.tags());
        originRepository.create(
                document.id(), mailbox.id(), envelope.sender(), envelope.subject(), envelope.receivedAt());
        log.info(
                "Filed a document from mail: station={} mailbox={} rule={} document={}",
                mailbox.stationId(),
                mailbox.id(),
                rule.id(),
                document.id());
        return record(recorded, attachment, hash, MailImportOutcome.IMPORTED, null, document.id());
    }

    /**
     * Records that a message was looked at and nothing in it could be taken.
     *
     * @param outcome why nothing was taken
     */
    public void recordRefusal(
            MailMailbox mailbox,
            MailRule rule,
            Envelope envelope,
            String messageId,
            MailImportOutcome outcome,
            String reason) {
        logRepository.record(
                mailbox.id(),
                mailbox.stationId(),
                rule == null ? null : rule.id(),
                messageId,
                envelope.sender(),
                envelope.subject(),
                null,
                null,
                outcome,
                reason,
                null);
    }

    /**
     * Which members the document is bound to.
     *
     * <p>Filing under nobody is the ordinary outcome rather than a failure, so an empty answer here is
     * the normal one: the mail comes from the office or the scanner and says nothing about whom it
     * concerns. A rule may name members outright, and may ask for the subject to be read, which is a
     * guess about a line a human typed and binds nobody where it is ambiguous.
     */
    private List<Integer> membersFor(MailRule rule, Envelope envelope, int stationId) {
        if (!rule.memberIds().isEmpty()) return rule.memberIds();
        if (!rule.readSubjectForMember()) return List.of();
        return SubjectMemberMatch.soleMatch(envelope.subject(), memberNaming.candidates(stationId))
                .map(candidate -> List.of(candidate.memberId()))
                .orElse(List.of());
    }

    private static String titleFor(MailRule rule, Envelope envelope, Attachment attachment) {
        if (rule.titleSource() == MailTitleSource.SUBJECT && envelope.subject() != null) {
            return envelope.subject();
        }
        String name = attachment.fileName();
        if (name == null || name.isBlank()) {
            return envelope.subject() != null ? envelope.subject() : "Ohne Titel";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * The name the file is kept under, which is the one it arrived with unless it arrived without one.
     *
     * <p>An embedded picture from a phone often has no name at all, and a document store where every
     * third file is called nothing is not usable, so one is made from what the bytes turned out to be.
     */
    private static String fileNameFor(Attachment attachment, String sniffed) {
        if (attachment.fileName() != null && !attachment.fileName().isBlank()) {
            return attachment.fileName().trim();
        }
        return "anhang" + extensionFor(sniffed);
    }

    private static String extensionFor(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> "";
        };
    }

    /**
     * What every entry of one attachment's log line shares, so the identity the message was recorded
     * under cannot drift from the one the mailbox remembers having handled.
     */
    private record Recorded(MailMailbox mailbox, MailRule rule, Envelope envelope, String messageId) {}

    private MailImportOutcome record(
            Recorded recorded,
            Attachment attachment,
            String hash,
            MailImportOutcome outcome,
            String reason,
            Integer documentId) {
        logRepository.record(
                recorded.mailbox().id(),
                recorded.mailbox().stationId(),
                recorded.rule().id(),
                recorded.messageId(),
                recorded.envelope().sender(),
                recorded.envelope().subject(),
                attachment.fileName(),
                hash,
                outcome,
                reason,
                documentId);
        return outcome;
    }
}
