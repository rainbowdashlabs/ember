/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mailimport.entity.MailRuleAction;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import dev.chojo.ember.feature.mailimport.entity.MailTitleSource;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MailRuleRepositoryTest extends RepositoryTestBase {

    private static MailRuleRepository repository;
    private static MailMailboxRepository mailboxRepository;
    private static Station station;
    private static Account account;
    private static int memberId;
    private static int mailboxId;
    private static int ruleId;

    @BeforeAll
    static void setup() {
        repository = new MailRuleRepository();
        mailboxRepository = new MailMailboxRepository();
        station = stationRepo.create("Rule Station");
        account = accountRepo.create("mail-rule@test.com", "Rule", "Tester");
        memberId = stationMemberRepo.create(station.id(), account.id()).id();
        mailboxId = mailboxRepository
                .create(
                        station.id(),
                        "Archiv",
                        "imap.musterstadt.de",
                        993,
                        MailSecurity.SSL,
                        "archive",
                        new EncryptedBlob(new byte[12], new byte[16]),
                        "INBOX",
                        15,
                        Instant.parse("2026-09-01T00:00:00Z"))
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /** A rule is never handed out without its patterns, or it would trust nobody and file nothing. */
    @Test
    @Order(1)
    void aRuleComesBackWithTheThreeListsThatHangOffIt() {
        var rule = repository.create(
                mailboxId,
                "Bescheinigungen",
                0,
                "Attest",
                ".pdf",
                List.of("application/pdf"),
                2048L,
                false,
                MailTitleSource.SUBJECT,
                true,
                true,
                true,
                MailRuleAction.MOVE,
                "Erledigt",
                List.of("archive@musterstadt.de", "*@praxis.de"),
                List.of("Attest", "Gesundheit"),
                List.of(memberId));

        assertEquals("Bescheinigungen", rule.name());
        assertEquals(List.of("application/pdf"), rule.acceptedTypes());
        assertEquals(2048L, rule.minSizeBytes());
        assertEquals(MailRuleAction.MOVE, rule.action());
        assertEquals("Erledigt", rule.moveToFolder());
        assertTrue(rule.hidden());
        assertTrue(rule.keepOnArchive());
        assertTrue(rule.readSubjectForMember());
        assertEquals(2, rule.senderPatterns().size());
        assertTrue(rule.senderPatterns().containsAll(List.of("archive@musterstadt.de", "*@praxis.de")));
        assertEquals(List.of("Attest", "Gesundheit"), rule.tags());
        assertEquals(List.of(memberId), rule.memberIds());
        assertFalse(rule.lostMember());
        ruleId = rule.id();
    }

    @Test
    @Order(2)
    void aRuleIsFoundByItsIdAndUnderItsMailbox() {
        assertTrue(repository.findById(ruleId).isPresent());
        assertEquals(1, repository.findByMailbox(mailboxId).size());
        assertTrue(repository.findById(-1).isEmpty());
        assertEquals(
                2,
                repository.findByMailbox(mailboxId).getFirst().senderPatterns().size());
    }

    @Test
    @Order(3)
    void rewritingARuleRewritesItsListsToo() {
        assertTrue(repository.update(
                ruleId,
                "Nur Fotos",
                2,
                false,
                null,
                null,
                List.of("image/jpeg", "image/png"),
                0L,
                true,
                MailTitleSource.FILE_NAME,
                false,
                false,
                false,
                MailRuleAction.MARK_SEEN,
                null,
                List.of("foto@praxis.de"),
                List.of(),
                List.of()));

        var rule = repository.findById(ruleId).orElseThrow();
        assertEquals("Nur Fotos", rule.name());
        assertEquals(2, rule.position());
        assertFalse(rule.enabled());
        assertNull(rule.subjectFilter());
        assertNull(rule.moveToFolder(), "a rule that no longer moves has nowhere to move to");
        assertEquals(MailTitleSource.FILE_NAME, rule.titleSource());
        assertTrue(rule.includeInline());
        assertEquals(List.of("foto@praxis.de"), rule.senderPatterns());
        assertEquals(List.of(), rule.tags());
        assertEquals(List.of(), rule.memberIds());
    }

    @Test
    @Order(4)
    void theRulesOfAMailboxComeBackInTheOrderTheyAreApplied() {
        repository.create(
                mailboxId,
                "Zuerst",
                0,
                null,
                null,
                List.of("application/pdf"),
                0L,
                false,
                MailTitleSource.SUBJECT,
                false,
                false,
                false,
                MailRuleAction.NOTHING,
                null,
                List.of("*@musterstadt.de"),
                List.of(),
                List.of());

        var rules = repository.findByMailbox(mailboxId);
        assertEquals(2, rules.size());
        assertEquals("Zuerst", rules.getFirst().name());
    }

    /**
     * A rule that silently stopped working the way it was written is worse than one that says what
     * happened to it, which is the whole reason this mark exists rather than the cascade alone.
     */
    @Test
    @Order(5)
    void aRuleThatLosesAMemberSaysSo() {
        var withMember = repository.create(
                mailboxId,
                "Fuer eine Person",
                5,
                null,
                null,
                List.of("application/pdf"),
                0L,
                false,
                MailTitleSource.SUBJECT,
                false,
                false,
                false,
                MailRuleAction.NOTHING,
                null,
                List.of("*@musterstadt.de"),
                List.of(),
                List.of(memberId));
        assertFalse(withMember.lostMember());

        repository.releaseMember(memberId);

        var after = repository.findById(withMember.id()).orElseThrow();
        assertTrue(after.lostMember(), "the rule says it lost somebody");
        assertEquals(List.of(), after.memberIds(), "and no longer files under them");
        assertTrue(after.enabled(), "while still working for everything else it was written for");

        assertTrue(repository.clearLostMember(withMember.id()));
        assertFalse(repository.findById(withMember.id()).orElseThrow().lostMember());
    }

    @Test
    @Order(6)
    void markingARuleAsHavingLostSomebodyIsIdempotent() {
        repository.markLostMember(ruleId);
        assertTrue(repository.findById(ruleId).orElseThrow().lostMember());
        repository.markLostMember(ruleId);
        assertTrue(repository.findById(ruleId).orElseThrow().lostMember());
    }

    @Test
    @Order(7)
    void aRuleCanBeTakenAway() {
        assertTrue(repository.delete(ruleId));
        assertTrue(repository.findById(ruleId).isEmpty());
        assertFalse(repository.delete(ruleId));
        assertFalse(repository.update(
                ruleId,
                "Weg",
                0,
                true,
                null,
                null,
                List.of("application/pdf"),
                0L,
                false,
                MailTitleSource.SUBJECT,
                false,
                false,
                false,
                MailRuleAction.NOTHING,
                null,
                List.of("*@musterstadt.de"),
                List.of(),
                List.of()));
        assertFalse(repository.clearLostMember(ruleId));
    }

    /** Taking the mailbox away takes its rules with it, since a rule without one means nothing. */
    @Test
    @Order(8)
    void takingTheMailboxAwayTakesItsRules() {
        assertFalse(repository.findByMailbox(mailboxId).isEmpty());

        mailboxRepository.delete(mailboxId);

        assertTrue(repository.findByMailbox(mailboxId).isEmpty());
    }
}
