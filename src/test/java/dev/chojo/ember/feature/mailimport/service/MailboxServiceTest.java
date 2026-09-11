/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import dev.chojo.ember.conf.file.elements.MailImport;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mailimport.entity.MailRuleAction;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import dev.chojo.ember.feature.mailimport.entity.MailTitleSource;
import dev.chojo.ember.feature.mailimport.repository.MailImportLogRepository;
import dev.chojo.ember.feature.mailimport.repository.MailMailboxRepository;
import dev.chojo.ember.feature.mailimport.repository.MailRuleRepository;
import dev.chojo.ember.feature.mailimport.route.MailImportRoutes.RuleRequest;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Setting a mailbox up, and everything that is refused as it is typed rather than discovered an hour
 * later by a thread nobody is watching.
 */
class MailboxServiceTest extends RepositoryTestBase {

    private static final String USER = "archive@musterstadt.de";
    private static final String PASSWORD = "not-a-real-password";
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static GreenMail greenMail;
    private static MailboxService service;
    private static MailboxService withoutAKey;
    private static MailboxService publicHostsOnly;
    private static Station station;
    private static Account account;

    @BeforeAll
    static void setup() {
        greenMail = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP));
        greenMail.start();
        greenMail.setUser(USER, USER, PASSWORD);

        var mailboxRepository = new MailMailboxRepository();
        var ruleRepository = new MailRuleRepository();
        var logRepository = new MailImportLogRepository();
        var importService = mock(MailImportService.class);
        service = new MailboxService(
                mailboxRepository,
                ruleRepository,
                logRepository,
                importService,
                new CredentialCipher(KEY),
                MailHostPolicies.allowingTheLocalNetwork(),
                new MailImport());
        withoutAKey = new MailboxService(
                mailboxRepository,
                ruleRepository,
                logRepository,
                importService,
                new CredentialCipher(""),
                MailHostPolicies.allowingTheLocalNetwork(),
                new MailImport());
        publicHostsOnly = new MailboxService(
                mailboxRepository,
                ruleRepository,
                logRepository,
                importService,
                new CredentialCipher(KEY),
                MailHostPolicies.publicHostsOnly(),
                new MailImport());

        station = stationRepo.create("Mailbox Service Station");
        account = accountRepo.create("mailbox-service@test.com", "Box", "Tester");
        stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        if (greenMail != null) greenMail.stop();
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private int mailboxId() {
        return service.create(
                        station.id(),
                        "Archiv",
                        "127.0.0.1",
                        greenMail.getImap().getPort(),
                        MailSecurity.NONE,
                        USER,
                        PASSWORD,
                        "INBOX",
                        false,
                        15,
                        Instant.now())
                .id();
    }

    private static RuleRequest ruleRequest(
            List<String> senders, List<String> types, MailRuleAction action, String folder) {
        return new RuleRequest(
                "Bescheinigungen",
                0,
                true,
                null,
                null,
                types,
                0L,
                false,
                MailTitleSource.SUBJECT,
                false,
                false,
                false,
                action,
                folder,
                senders,
                List.of());
    }

    /**
     * Without an encryption key there is nowhere safe to put a password, and storing it in the clear is
     * not an option this offers.
     */
    @Test
    void withoutAnEncryptionKeyAMailboxCannotBeSavedAtAll() {
        assertFalse(withoutAKey.canStorePasswords());
        assertTrue(service.canStorePasswords());

        assertThrows(
                BadRequestResponse.class,
                () -> withoutAKey.create(
                        station.id(),
                        "Archiv",
                        "imap.musterstadt.de",
                        993,
                        MailSecurity.SSL,
                        USER,
                        PASSWORD,
                        "INBOX",
                        false,
                        15,
                        Instant.now()));
    }

    @Test
    void aMailboxNeedsAHostAUserAndAPassword() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "Archiv",
                        "imap.x.de",
                        993,
                        MailSecurity.SSL,
                        USER,
                        null,
                        "INBOX",
                        false,
                        15,
                        Instant.now()));
        assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "  ",
                        "imap.x.de",
                        993,
                        MailSecurity.SSL,
                        USER,
                        PASSWORD,
                        "INBOX",
                        false,
                        15,
                        Instant.now()));
        assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "Archiv",
                        "",
                        993,
                        MailSecurity.SSL,
                        USER,
                        PASSWORD,
                        "INBOX",
                        false,
                        15,
                        Instant.now()));
        assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "Archiv",
                        "imap.x.de",
                        993,
                        MailSecurity.SSL,
                        " ",
                        PASSWORD,
                        "INBOX",
                        false,
                        15,
                        Instant.now()));
        assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "x".repeat(500),
                        "imap.x.de",
                        993,
                        MailSecurity.SSL,
                        USER,
                        PASSWORD,
                        "INBOX",
                        false,
                        15,
                        Instant.now()));
    }

    /**
     * Connecting a mailbox is a station setting, so without this a station manager could aim one at
     * loopback or an address inside the deployment and read off the connection test whether anything
     * answered there.
     */
    @Test
    void anAddressInsideTheDeploymentIsRefused() {
        assertThrows(
                BadRequestResponse.class,
                () -> publicHostsOnly.create(
                        station.id(),
                        "Innen",
                        "127.0.0.1",
                        993,
                        MailSecurity.SSL,
                        USER,
                        PASSWORD,
                        "INBOX",
                        false,
                        15,
                        Instant.now()));
    }

    /** A password sent in the clear to a host on the internet is a password given away. */
    @Test
    void anUnencryptedMailboxIsOnlyOfferedOnYourOwnNetwork() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "Offen",
                        "8.8.8.8",
                        143,
                        MailSecurity.NONE,
                        USER,
                        PASSWORD,
                        "INBOX",
                        false,
                        15,
                        Instant.now()));

        var nextDoor = service.create(
                station.id(),
                "Nebenan",
                "127.0.0.1",
                greenMail.getImap().getPort(),
                MailSecurity.NONE,
                USER,
                PASSWORD,
                "INBOX",
                false,
                15,
                Instant.now());

        assertEquals(MailSecurity.NONE, nextDoor.security());
    }

    /** The floor belongs to the operator, so asking for one minute gets the floor and not one minute. */
    @Test
    void aStationCannotAskToBeVisitedMoreOftenThanTheOperatorAllows() {
        var eager = service.create(
                station.id(),
                "Ungeduldig",
                "imap.musterstadt.de",
                993,
                MailSecurity.SSL,
                USER,
                PASSWORD,
                "INBOX",
                false,
                1,
                Instant.now());

        assertEquals(15, eager.intervalMinutes());
        assertEquals(
                "INBOX",
                service.create(
                                station.id(),
                                "Leer",
                                "imap.x.de",
                                993,
                                MailSecurity.SSL,
                                USER,
                                PASSWORD,
                                "  ",
                                false,
                                15,
                                Instant.now())
                        .folder());
    }

    @Test
    void aMailboxThatIsNotThereIsNotFound() {
        assertTrue(service.find(-1).isEmpty());
        assertThrows(NotFoundResponse.class, () -> service.require(-1));
        assertTrue(service.findRule(-1).isEmpty());
        assertThrows(NotFoundResponse.class, () -> service.requireRule(-1));
    }

    /**
     * The refusal that matters most. A rule with no sender pattern would be legal, would fail closed, and
     * would silently file nothing, which is the complaint this feature is likeliest to produce.
     */
    @Test
    void aRuleThatTrustsNobodyIsRefusedAsItIsWritten() {
        var mailbox = service.require(mailboxId());

        assertThrows(
                BadRequestResponse.class,
                () -> service.createRule(
                        mailbox, ruleRequest(List.of(), List.of("application/pdf"), MailRuleAction.NOTHING, null)));
        assertThrows(
                BadRequestResponse.class,
                () -> service.createRule(
                        mailbox, ruleRequest(null, List.of("application/pdf"), MailRuleAction.NOTHING, null)));
    }

    @Test
    void aPatternThatIsNeitherFormIsRefused() {
        var mailbox = service.require(mailboxId());

        assertThrows(
                BadRequestResponse.class,
                () -> service.createRule(
                        mailbox, ruleRequest(List.of("*"), List.of("application/pdf"), MailRuleAction.NOTHING, null)));
        assertThrows(
                BadRequestResponse.class,
                () -> service.createRule(
                        mailbox, ruleRequest(List.of(".*"), List.of("application/pdf"), MailRuleAction.NOTHING, null)));
        assertThrows(
                BadRequestResponse.class,
                () -> service.createRule(
                        mailbox,
                        ruleRequest(
                                List.of("musterstadt.de"), List.of("application/pdf"), MailRuleAction.NOTHING, null)));
    }

    @Test
    void aRuleHasToSayWhichKindsOfFileItTakes() {
        var mailbox = service.require(mailboxId());

        assertThrows(
                BadRequestResponse.class,
                () -> service.createRule(
                        mailbox, ruleRequest(List.of("*@musterstadt.de"), List.of(), MailRuleAction.NOTHING, null)));
        assertThrows(
                BadRequestResponse.class,
                () -> service.createRule(
                        mailbox, ruleRequest(List.of("*@musterstadt.de"), null, MailRuleAction.NOTHING, null)));
        assertThrows(
                BadRequestResponse.class,
                () -> service.createRule(
                        mailbox,
                        ruleRequest(
                                List.of("*@musterstadt.de"),
                                List.of("application/x-msdownload"),
                                MailRuleAction.NOTHING,
                                null)));
    }

    @Test
    void movingAMessageNeedsSomewhereToMoveItTo() {
        var mailbox = service.require(mailboxId());

        assertThrows(
                BadRequestResponse.class,
                () -> service.createRule(
                        mailbox,
                        ruleRequest(
                                List.of("*@musterstadt.de"), List.of("application/pdf"), MailRuleAction.MOVE, " ")));

        var moved = service.createRule(
                mailbox,
                ruleRequest(List.of("*@musterstadt.de"), List.of("application/pdf"), MailRuleAction.MOVE, "Erledigt"));
        assertEquals("Erledigt", moved.moveToFolder());
    }

    @Test
    void aRuleCanBeWrittenChangedAndTakenAway() {
        var mailbox = service.require(mailboxId());
        var rule = service.createRule(
                mailbox,
                ruleRequest(List.of("*@musterstadt.de"), List.of("application/pdf"), MailRuleAction.MARK_SEEN, null));

        assertEquals(1, service.rulesOf(mailbox.id()).size());
        assertTrue(service.updateRule(
                rule.id(),
                ruleRequest(List.of("post@musterstadt.de"), List.of("image/png"), MailRuleAction.FLAG, null)));
        assertEquals(List.of("image/png"), service.requireRule(rule.id()).acceptedTypes());
        assertTrue(service.deleteRule(rule.id()));
        assertTrue(service.rulesOf(mailbox.id()).isEmpty());
    }

    /**
     * Reporting the folders is the point rather than a nicety: the commonest mistake in setting one of
     * these up is a folder spelled the way the person says it rather than the way the provider does.
     */
    @Test
    void aConnectionTestListsTheFoldersAndSendsNothing() {
        var result = service.test(service.require(mailboxId()));

        assertTrue(result.connected());
        assertTrue(result.folders().contains("INBOX"));
        assertTrue(result.folderExists());
        assertEquals(0, greenMail.getReceivedMessages().length, "a test sends nothing");
    }

    /** A failure is an answer to be read on the page, not a stack trace in a log nobody can see. */
    @Test
    void aConnectionTestThatFailsSaysWhyRatherThanThrowing() {
        var unreachable = service.create(
                station.id(),
                "Kaputt",
                "127.0.0.1",
                1,
                MailSecurity.NONE,
                USER,
                PASSWORD,
                "INBOX",
                false,
                15,
                Instant.now());

        var result = service.test(unreachable);

        assertFalse(result.connected());
        assertNotNull(result.error());
        assertTrue(result.folders().isEmpty());
        assertFalse(result.folderExists());
    }

    @Test
    void aMailboxCanBeChangedGivenANewPasswordAndTakenAway() {
        int id = mailboxId();

        assertTrue(service.update(
                id,
                "Anders",
                "imap.anders.de",
                143,
                MailSecurity.STARTTLS,
                "wer",
                "Andere",
                true,
                true,
                60,
                Instant.now()));
        var changed = service.require(id);
        assertEquals("Anders", changed.name());
        assertEquals(MailSecurity.STARTTLS, changed.security());
        assertEquals(60, changed.intervalMinutes());
        assertTrue(changed.verifyDkim(), "what the mailbox demands of a sender is changed with the rest");
        assertThrows(
                BadRequestResponse.class,
                () -> service.update(
                        id, "Offen", "8.8.8.8", 143, MailSecurity.NONE, "wer", "INBOX", false, true, 60, Instant.now()),
                "the same host rules hold when a mailbox is changed as when it is written");

        service.updatePassword(id, "ein neues Kennwort");
        assertThrows(BadRequestResponse.class, () -> service.updatePassword(id, " "));
        assertThrows(BadRequestResponse.class, () -> withoutAKey.updatePassword(id, "egal"));

        assertTrue(service.resume(id));
        assertTrue(service.delete(id));
        assertFalse(service.delete(id));
    }

    @Test
    void theLogOfAStationIsCountedAndPaged() {
        assertEquals(0, service.countLog(station.id()));
        assertTrue(service.log(station.id(), 10, 0).isEmpty());
    }

    @Test
    void aMailboxOfAStationIsListedForThatStation() {
        int id = mailboxId();

        assertTrue(service.findByStation(station.id()).stream().anyMatch(box -> box.id() == id));
    }
}
