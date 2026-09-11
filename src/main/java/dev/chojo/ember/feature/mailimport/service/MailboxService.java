/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.conf.file.elements.MailImport;
import dev.chojo.ember.feature.mailimport.entity.MailImportEntry;
import dev.chojo.ember.feature.mailimport.entity.MailMailbox;
import dev.chojo.ember.feature.mailimport.entity.MailRule;
import dev.chojo.ember.feature.mailimport.entity.MailRuleAction;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import dev.chojo.ember.feature.mailimport.repository.MailImportLogRepository;
import dev.chojo.ember.feature.mailimport.repository.MailMailboxRepository;
import dev.chojo.ember.feature.mailimport.repository.MailRuleRepository;
import dev.chojo.ember.feature.mailimport.route.MailImportRoutes.RuleRequest;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Setting up what a station reads and where it goes.
 *
 * <p>Everything a station may get wrong is refused here rather than discovered by a background thread an
 * hour later: a password with nowhere safe to go, an interval below the operator's floor, a sender pattern
 * that would trust the world, a rule that trusts nobody and would therefore file nothing.
 */
@Singleton
public class MailboxService {
    private static final Logger log = LoggerFactory.getLogger(MailboxService.class);
    private static final int MAX_NAME = 200;

    private final MailMailboxRepository mailboxRepository;
    private final MailRuleRepository ruleRepository;
    private final MailImportLogRepository logRepository;
    private final MailImportService importService;
    private final CredentialCipher cipher;
    private final MailHostPolicy hostPolicy;
    private final MailImport settings;

    @Inject
    public MailboxService(
            MailMailboxRepository mailboxRepository,
            MailRuleRepository ruleRepository,
            MailImportLogRepository logRepository,
            MailImportService importService,
            CredentialCipher cipher,
            MailHostPolicy hostPolicy,
            MailImport settings) {
        this.mailboxRepository = mailboxRepository;
        this.ruleRepository = ruleRepository;
        this.logRepository = logRepository;
        this.importService = importService;
        this.cipher = cipher;
        this.hostPolicy = hostPolicy;
        this.settings = settings;
    }

    /**
     * What a connection test came to.
     *
     * @param connected      whether the host answered at all
     * @param error          what went wrong, or null where nothing did
     * @param folders        the folders the account can see, so a misspelled one is obvious
     * @param folderExists   whether the folder the mailbox names is among them
     * @param writesAuthResult whether the server writes an authentication result, which is worth knowing
     *                         and worth nothing as a security guarantee
     */
    public record TestResult(
            boolean connected, String error, List<String> folders, boolean folderExists, boolean writesAuthResult) {}

    /**
     * Whether a password can be kept at all.
     *
     * <p>Without an encryption key configured there is nowhere safe to put one, and storing it in the clear
     * is not an option this offers. The page asks first and says so, rather than letting somebody type a
     * credential that would then be refused.
     */
    public boolean canStorePasswords() {
        return cipher.isConfigured();
    }

    public List<MailMailbox> findByStation(int stationId) {
        return mailboxRepository.findByStation(stationId);
    }

    public Optional<MailMailbox> find(int id) {
        return mailboxRepository.findById(id);
    }

    public MailMailbox require(int id) {
        return mailboxRepository.findById(id).orElseThrow(NotFoundResponse::new);
    }

    public MailMailbox create(
            int stationId,
            String name,
            String host,
            int port,
            MailSecurity security,
            String username,
            String password,
            String folder,
            boolean verifyDkim,
            int intervalMinutes,
            Instant importFrom) {
        requireSomewhereToPutThePassword();
        if (password == null || password.isBlank()) throw new BadRequestResponse("A mailbox needs a password");
        requireText(name, "name");
        requireText(host, "host");
        requireText(username, "user");
        requireReachable(host, security);
        var mailbox = mailboxRepository.create(
                stationId,
                name.trim(),
                host.trim(),
                port,
                security,
                username.trim(),
                cipher.encrypt(password.getBytes(StandardCharsets.UTF_8)),
                folderOr(folder),
                verifyDkim,
                heldToTheFloor(intervalMinutes),
                importFrom != null ? importFrom : Instant.now());
        log.info("Station {} added mailbox {}", stationId, mailbox.id());
        return mailbox;
    }

    public boolean update(
            int id,
            String name,
            String host,
            int port,
            MailSecurity security,
            String username,
            String folder,
            boolean verifyDkim,
            boolean enabled,
            int intervalMinutes,
            Instant importFrom) {
        requireText(name, "name");
        requireText(host, "host");
        requireText(username, "user");
        requireReachable(host, security);
        return mailboxRepository.update(
                id,
                name.trim(),
                host.trim(),
                port,
                security,
                username.trim(),
                folderOr(folder),
                verifyDkim,
                enabled,
                heldToTheFloor(intervalMinutes),
                importFrom != null ? importFrom : Instant.now());
    }

    public void updatePassword(int id, String password) {
        requireSomewhereToPutThePassword();
        if (password == null || password.isBlank()) throw new BadRequestResponse("A mailbox needs a password");
        mailboxRepository.updatePassword(id, cipher.encrypt(password.getBytes(StandardCharsets.UTF_8)));
    }

    public boolean delete(int id) {
        return mailboxRepository.delete(id);
    }

    public boolean resume(int id) {
        return mailboxRepository.resume(id);
    }

    /**
     * Connects, lists the folders, and sends nothing.
     *
     * <p>A failure is an answer rather than an error: whoever is setting this up wants to read what went
     * wrong on the page, not a stack trace in a log they cannot see.
     */
    public TestResult test(MailMailbox mailbox) {
        try (var reader = new MailboxReader(
                hostPolicy,
                mailbox.host(),
                mailbox.port(),
                mailbox.security(),
                mailbox.username(),
                new String(cipher.decrypt(mailbox.password()), StandardCharsets.UTF_8),
                settings.timeoutSeconds())) {
            var folders = reader.folders();
            return new TestResult(true, null, folders, folders.contains(mailbox.folder()), false);
        } catch (Exception e) {
            return new TestResult(
                    false,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    List.of(),
                    false,
                    false);
        }
    }

    /** Runs one cycle now, which is what everybody does after writing their first rule. */
    public MailImportService.Cycle runNow(MailMailbox mailbox, Instant now) {
        if (!settings.enabled()) {
            throw new BadRequestResponse("Reading mail for documents is switched off for this instance");
        }
        return importService.run(mailbox, now);
    }

    public List<MailRule> rulesOf(int mailboxId) {
        return ruleRepository.findByMailbox(mailboxId);
    }

    public Optional<MailRule> findRule(int id) {
        return ruleRepository.findById(id);
    }

    public MailRule requireRule(int id) {
        return ruleRepository.findById(id).orElseThrow(NotFoundResponse::new);
    }

    public MailRule createRule(MailMailbox mailbox, RuleRequest request) {
        validate(request);
        return ruleRepository.create(
                mailbox.id(),
                request.name().trim(),
                request.position(),
                blankToNull(request.subjectFilter()),
                blankToNull(request.attachmentNameFilter()),
                request.acceptedTypes(),
                Math.max(request.minSizeBytes(), 0),
                request.includeInline(),
                request.titleSource(),
                request.hidden(),
                request.keepOnArchive(),
                request.readSubjectForMember(),
                request.action(),
                request.action() == MailRuleAction.MOVE ? blankToNull(request.moveToFolder()) : null,
                request.senderPatterns(),
                request.tags());
    }

    public boolean updateRule(int id, RuleRequest request) {
        validate(request);
        return ruleRepository.update(
                id,
                request.name().trim(),
                request.position(),
                request.enabled(),
                blankToNull(request.subjectFilter()),
                blankToNull(request.attachmentNameFilter()),
                request.acceptedTypes(),
                Math.max(request.minSizeBytes(), 0),
                request.includeInline(),
                request.titleSource(),
                request.hidden(),
                request.keepOnArchive(),
                request.readSubjectForMember(),
                request.action(),
                request.action() == MailRuleAction.MOVE ? blankToNull(request.moveToFolder()) : null,
                request.senderPatterns(),
                request.tags());
    }

    public boolean deleteRule(int id) {
        return ruleRepository.delete(id);
    }

    public List<MailImportEntry> log(int stationId, int limit, int offset) {
        return logRepository.findByStation(stationId, limit, offset);
    }

    public int countLog(int stationId) {
        return logRepository.countByStation(stationId);
    }

    /**
     * Refuses a rule that cannot do what it looks like it does.
     *
     * <p>The sender patterns are the boundary, so a pattern that is not one of the two forms is refused as
     * it is typed rather than stored to match nothing. A rule with no pattern is refused outright: it would
     * be legal, would fail closed, and would silently file nothing, which is the complaint this feature is
     * most likely to produce.
     */
    private void validate(RuleRequest request) {
        requireText(request.name(), "name");
        if (request.senderPatterns() == null || request.senderPatterns().isEmpty()) {
            throw new BadRequestResponse("A rule has to say which senders it trusts, or it accepts nothing");
        }
        for (String pattern : request.senderPatterns()) {
            if (!SenderPatterns.isValid(pattern)) {
                throw new BadRequestResponse(
                        "%s is neither an address nor a domain written as *@domain".formatted(pattern));
            }
        }
        if (request.acceptedTypes() == null || request.acceptedTypes().isEmpty()) {
            throw new BadRequestResponse("A rule has to say which kinds of file it takes");
        }
        for (String type : request.acceptedTypes()) {
            if (!ContentSniffer.SUPPORTED_TYPES.contains(type)) {
                throw new BadRequestResponse("%s is not a kind of file this can recognise".formatted(type));
            }
        }
        if (request.action() == MailRuleAction.MOVE && blankToNull(request.moveToFolder()) == null) {
            throw new BadRequestResponse("Moving a message needs a folder to move it to");
        }
    }

    /**
     * Refuses a host this instance may not connect to, and clear text anywhere but the local network.
     *
     * <p>Asked as the mailbox is written so that whoever is setting one up is told now, rather than
     * finding out from a connection test that the address they typed was never going to be tried.
     */
    private void requireReachable(String host, MailSecurity security) {
        var objection = hostPolicy.objection(host, security);
        if (objection.isPresent()) throw new BadRequestResponse(objection.get());
    }

    private void requireSomewhereToPutThePassword() {
        if (!cipher.isConfigured()) {
            throw new BadRequestResponse(
                    "No encryption key is configured on this instance, so a mailbox password cannot be kept safely");
        }
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) throw new BadRequestResponse("A mailbox needs a " + what);
        if (value.length() > MAX_NAME) throw new BadRequestResponse("The " + what + " is too long");
    }

    private static String folderOr(String folder) {
        return folder == null || folder.isBlank() ? "INBOX" : folder.trim();
    }

    /** The station chooses how often, and never more often than the operator allows. */
    private int heldToTheFloor(int intervalMinutes) {
        return Math.max(intervalMinutes, settings.minimumIntervalMinutes());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
