/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.MailImport;
import dev.chojo.ember.feature.mailimport.entity.MailImportOutcome;
import dev.chojo.ember.feature.mailimport.entity.MailMailbox;
import dev.chojo.ember.feature.mailimport.entity.MailRule;
import dev.chojo.ember.feature.mailimport.entity.MailRuleAction;
import dev.chojo.ember.feature.mailimport.repository.MailImportLogRepository;
import dev.chojo.ember.feature.mailimport.repository.MailMailboxRepository;
import dev.chojo.ember.feature.mailimport.repository.MailRuleRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.mail.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One visit to one mailbox.
 *
 * <p>What a visit does, in order: read the envelopes that arrived since the mailbox starts from, skip the
 * messages already dealt with, find the rule that takes each one, file what its attachments turn out to
 * be, then do what the rule says with the message. Every message that was looked at leaves a log entry,
 * including the ones nothing was taken from.
 */
@Singleton
public class MailImportService {
    private static final Logger log = LoggerFactory.getLogger(MailImportService.class);
    private static final String AUTH_FAILURE = "dmarc=fail";
    private static final String SPF_FAILURE = "spf=fail";

    private final MailMailboxRepository mailboxRepository;
    private final MailRuleRepository ruleRepository;
    private final MailImportLogRepository logRepository;
    private final MailFilingService filingService;
    private final StorageQuotaService quotaService;
    private final CredentialCipher cipher;
    private final MailImport settings;
    private final NotificationService notificationService;

    @Inject
    public MailImportService(
            MailMailboxRepository mailboxRepository,
            MailRuleRepository ruleRepository,
            MailImportLogRepository logRepository,
            MailFilingService filingService,
            StorageQuotaService quotaService,
            CredentialCipher cipher,
            MailImport settings,
            NotificationService notificationService) {
        this.mailboxRepository = mailboxRepository;
        this.ruleRepository = ruleRepository;
        this.logRepository = logRepository;
        this.filingService = filingService;
        this.quotaService = quotaService;
        this.cipher = cipher;
        this.settings = settings;
        this.notificationService = notificationService;
    }

    /**
     * What one visit came to, which is what "run now" reports back to whoever pressed it.
     *
     * @param looked   how many messages were looked at
     * @param imported how many documents were filed
     * @param refused  how many attachments were refused, for any reason
     */
    public record Cycle(int looked, int imported, int refused) {}

    /**
     * Visits one mailbox.
     *
     * <p>Everything that can go wrong with somebody else's mail server goes wrong here, so the failure is
     * recorded on the mailbox and counted rather than thrown at the caller. A mailbox that fails enough
     * times in a row is taken out of the rotation.
     *
     * @param mailbox the mailbox to visit
     * @param now     the moment of the visit
     * @return what the visit came to
     */
    public Cycle run(MailMailbox mailbox, Instant now) {
        List<MailRule> rules = ruleRepository.findByMailbox(mailbox.id());
        if (rules.isEmpty()) {
            mailboxRepository.recordSuccess(mailbox.id(), now);
            return new Cycle(0, 0, 0);
        }

        boolean needsWriting = rules.stream().anyMatch(rule -> rule.action() != MailRuleAction.NOTHING);
        long ceiling = quotaService.perFileLimitBytes(mailbox.stationId());
        int looked = 0;
        int imported = 0;
        int refused = 0;

        try (var reader = new MailboxReader(
                mailbox.host(),
                mailbox.port(),
                mailbox.security(),
                mailbox.username(),
                passwordOf(mailbox),
                settings.timeoutSeconds())) {
            reader.open(mailbox.folder(), needsWriting);
            int budget = settings.maxAttachmentsPerCycle();
            for (Message message : reader.since(mailbox.importFrom(), budget)) {
                if (budget <= 0) break;
                var outcome = handle(reader, mailbox, rules, message, ceiling);
                looked++;
                imported += outcome.imported();
                refused += outcome.refused();
                budget -= Math.max(outcome.imported() + outcome.refused(), 1);
            }
            mailboxRepository.recordSuccess(mailbox.id(), now);
            tellAboutUnbound(mailbox, imported, now);
        } catch (Exception e) {
            log.warn("Could not read mailbox {} of station {}", mailbox.id(), mailbox.stationId(), e);
            mailboxRepository.recordFailure(mailbox.id(), now, shortReason(e));
            if (MailImportSchedule.shouldSuspend(mailbox.failureCount() + 1)) {
                mailboxRepository.suspend(mailbox.id());
                log.warn("Mailbox {} suspended after repeated failure", mailbox.id());
                tellAboutSuspension(mailbox, shortReason(e));
            }
        }
        return new Cycle(looked, imported, refused);
    }

    /**
     * Tells whoever looks after the station's mail that a mailbox has stopped being tried.
     *
     * <p>The error is on the mailbox either way, but nobody goes looking at a page for an import that
     * silently stopped a month ago, so this is pushed rather than waited for.
     */
    private void tellAboutSuspension(MailMailbox mailbox, String reason) {
        try {
            notificationService.notifyMembersWithRole(
                    mailbox.stationId(),
                    StationPermission.STATION_MAIL.name(),
                    NotificationType.MAILBOX_SUSPENDED,
                    NotificationData.of(
                            new NotificationParams.MailboxSuspended(mailbox.name(), reason),
                            new NotificationData.NotificationLink("station-mail-import", Map.of())));
        } catch (Exception e) {
            log.warn("Could not say that mailbox {} was suspended", mailbox.id(), e);
        }
    }

    /**
     * Tells whoever may read documents that paperwork is waiting to be sorted.
     *
     * <p>Filing under nobody is the ordinary outcome, so this is not a failure report. What nobody
     * notices is the store quietly filling up, and one line per cycle is what answers that.
     */
    private void tellAboutUnbound(MailMailbox mailbox, int imported, Instant now) {
        if (imported == 0) return;
        try {
            int unbound = logRepository.countUnboundSince(mailbox.stationId(), now.minus(Duration.ofDays(1)));
            if (unbound == 0) return;
            notificationService.notifyMembersWithRole(
                    mailbox.stationId(),
                    StationPermission.DOCUMENT_READ.name(),
                    NotificationType.MAIL_IMPORT_UNBOUND,
                    NotificationData.of(
                            new NotificationParams.MailImportUnbound(unbound),
                            new NotificationData.NotificationLink("station-members-documents", Map.of())));
        } catch (Exception e) {
            log.warn("Could not say what arrived unbound for station {}", mailbox.stationId(), e);
        }
    }

    private record Handled(int imported, int refused) {}

    /**
     * Deals with one message.
     *
     * <p>The identity is written down before the attachments are, because a connection that drops after
     * the import and before the flags are written is exactly the failure that would otherwise import
     * everything a second time.
     */
    private Handled handle(
            MailboxReader reader, MailMailbox mailbox, List<MailRule> rules, Message message, long ceiling)
            throws Exception {
        var envelope = reader.read(message, anyWantsInline(rules), everyAcceptedType(rules), ceiling);
        var identity =
                MessageIdentity.of(envelope.messageId(), envelope.sender(), envelope.subject(), envelope.receivedAt());

        if (logRepository.hasHandledMessage(mailbox.id(), identity.id())) {
            return new Handled(0, 0);
        }

        var names = envelope.attachments().stream()
                .map(MailboxReader.Attachment::fileName)
                .filter(java.util.Objects::nonNull)
                .toList();
        var chosen = RuleSelection.firstMatch(rules, envelope.sender(), envelope.subject(), names);

        if (chosen.isEmpty()) {
            boolean trustedByAnybody =
                    rules.stream().anyMatch(rule -> SenderPatterns.accepts(rule.senderPatterns(), envelope.sender()));
            filingService.recordRefusal(
                    mailbox,
                    null,
                    envelope,
                    identity.id(),
                    trustedByAnybody ? MailImportOutcome.NO_RULE_MATCHED : MailImportOutcome.SENDER_NOT_ALLOWED,
                    trustedByAnybody
                            ? "The sender is trusted but no rule's filters fit this message"
                            : "No rule of this mailbox trusts that address");
            logRepository.markMessageHandled(mailbox.id(), identity.id(), identity.derived());
            return new Handled(0, 1);
        }

        MailRule rule = chosen.get();
        if (failedItsOwnChecks(envelope.authResult())) {
            filingService.recordRefusal(
                    mailbox,
                    rule,
                    envelope,
                    identity.id(),
                    MailImportOutcome.AUTHENTICATION_FAILED,
                    "The receiving server said the sending domain failed its own published checks");
            logRepository.markMessageHandled(mailbox.id(), identity.id(), identity.derived());
            return new Handled(0, 1);
        }

        var wanted = envelope.attachments().stream()
                .filter(attachment -> rule.includeInline() || !attachment.inline())
                .filter(attachment -> nameFits(attachment.fileName(), rule.attachmentNameFilter()))
                .toList();

        if (wanted.isEmpty()) {
            filingService.recordRefusal(
                    mailbox,
                    rule,
                    envelope,
                    identity.id(),
                    MailImportOutcome.NO_ATTACHMENT,
                    "Nothing hung off this message that the rule could look at");
            logRepository.markMessageHandled(mailbox.id(), identity.id(), identity.derived());
            return new Handled(0, 1);
        }

        logRepository.markMessageHandled(mailbox.id(), identity.id(), identity.derived());
        int imported = 0;
        int refused = 0;
        for (var attachment : wanted) {
            var outcome = filingService.file(mailbox, rule, envelope, attachment, identity.id());
            if (outcome == MailImportOutcome.IMPORTED) {
                imported++;
            } else {
                refused++;
            }
        }
        applyAction(reader, rule, message);
        return new Handled(imported, refused);
    }

    /**
     * What becomes of the message once its attachments are dealt with.
     *
     * <p>A message whose disposal fails is still a message that was imported, so this is written down and
     * not thrown: the alternative is a cycle that files a document and then reports the whole visit as a
     * failure because a folder could not be made.
     */
    private void applyAction(MailboxReader reader, MailRule rule, Message message) {
        try {
            switch (rule.action()) {
                case MARK_SEEN -> reader.markSeen(message);
                case FLAG -> reader.flag(message);
                case MOVE -> {
                    if (rule.moveToFolder() != null && !rule.moveToFolder().isBlank()) {
                        reader.moveTo(message, rule.moveToFolder());
                    }
                }
                case NOTHING -> {
                    // A read-only account cannot be asked to change anything, which is why this exists.
                }
            }
        } catch (Exception e) {
            log.warn("Rule {} could not do what it says with a message it took", rule.id(), e);
        }
    }

    /**
     * Whether the receiving server said the sending domain failed its own checks.
     *
     * <p>A guard against carelessness and not a security boundary. The header is written by whoever
     * handled the message last, and a sender can write one claiming a pass, which is why a rule cannot be
     * set to require it: requiring it would have been satisfied by a forgery just as easily. What holds
     * the line is the sender pattern, kept narrow.
     */
    private static boolean failedItsOwnChecks(String authResult) {
        if (authResult == null) return false;
        String lower = authResult.toLowerCase(Locale.ROOT);
        return lower.contains(AUTH_FAILURE) || lower.contains(SPF_FAILURE);
    }

    private static boolean nameFits(String fileName, String filter) {
        if (filter == null || filter.isBlank()) return true;
        if (fileName == null) return false;
        return fileName.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * The types any rule of this mailbox might want, used for the first cut before anything is fetched.
     *
     * <p>Wider than one rule on purpose: which rule takes the message is not known until its attachment
     * names are, and the sniff after the download is the authority anyway.
     */
    private static List<String> everyAcceptedType(List<MailRule> rules) {
        var types = new ArrayList<String>();
        for (MailRule rule : rules) {
            for (String type : rule.acceptedTypes()) {
                if (!types.contains(type)) types.add(type);
            }
        }
        return types;
    }

    private static boolean anyWantsInline(List<MailRule> rules) {
        return rules.stream().anyMatch(MailRule::includeInline);
    }

    private String passwordOf(MailMailbox mailbox) {
        return new String(cipher.decrypt(mailbox.password()), StandardCharsets.UTF_8);
    }

    private static String shortReason(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
