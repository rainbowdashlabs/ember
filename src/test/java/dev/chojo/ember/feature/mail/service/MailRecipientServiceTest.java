/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where an account's mail goes: to the account when it has an address, to whoever looks after the
 * member when it has not, and nowhere at all when neither can be reached.
 */
class MailRecipientServiceTest extends RepositoryTestBase {

    private static MailRecipientService service;
    private static Station station;
    private static Account childAccount;
    private static Account firstGuardianAccount;
    private static Account secondGuardianAccount;
    private static Account grownUpAccount;
    private static StationMember child;

    @BeforeAll
    static void setup() {
        service = new MailRecipientService(accountRepo, stationMemberRepo);
        station = stationRepo.create("Recipient Station");
        childAccount = accountRepo.create("kid@recipient.local", "Lena", "Sommer");
        firstGuardianAccount = accountRepo.create("petra@recipient.test", "Petra", "Sommer");
        secondGuardianAccount = accountRepo.create("paul@recipient.test", "Paul", "Sommer");
        grownUpAccount = accountRepo.create("adult@recipient.test", "Adele", "Alt");

        child = stationMemberRepo.create(station.id(), childAccount.id());
        var firstGuardian = stationMemberRepo.create(station.id(), firstGuardianAccount.id());
        var secondGuardian = stationMemberRepo.create(station.id(), secondGuardianAccount.id());
        stationMemberRepo.create(station.id(), grownUpAccount.id());
        stationMemberRepo.addManager(firstGuardian.id(), child.id());
        stationMemberRepo.addManager(secondGuardian.id(), child.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(childAccount.id());
        accountRepo.delete(firstGuardianAccount.id());
        accountRepo.delete(secondGuardianAccount.id());
        accountRepo.delete(grownUpAccount.id());
    }

    @Test
    void somebodyWithAnAddressIsWrittenToAtIt() {
        var recipients = service.forAccount(grownUpAccount.id());

        assertEquals(1, recipients.size());
        assertEquals("adult@recipient.test", recipients.getFirst().email());
        assertEquals("Adele", recipients.getFirst().name());
        assertFalse(recipients.getFirst().guardian());
    }

    @Test
    void somebodyWithoutOneIsWrittenAboutToEveryoneWhoLooksAfterThem() {
        var recipients = service.forAccount(childAccount.id());

        assertEquals(
                List.of("petra@recipient.test", "paul@recipient.test"),
                recipients.stream().map(MailRecipientService.Recipient::email).toList());
        assertTrue(recipients.stream().allMatch(MailRecipientService.Recipient::guardian));
    }

    @Test
    void aGuardianReadsTheChildsNameRatherThanTheirOwn() {
        var recipients = service.forAccount(childAccount.id());

        assertTrue(recipients.stream().allMatch(recipient -> "Lena".equals(recipient.name())));
    }

    @Test
    void nobodyToWriteToIsNotAnError() {
        var orphanAccount = accountRepo.create("alone@recipient.local", "Allein", "Kind");
        var orphan = stationMemberRepo.create(station.id(), orphanAccount.id());

        assertTrue(service.forAccount(orphanAccount.id()).isEmpty());
        assertFalse(service.isReachable(orphanAccount.id()));

        stationMemberRepo.delete(orphan.id());
        accountRepo.delete(orphanAccount.id());
    }

    @Test
    void anAccountThatIsGoneReachesNobody() {
        assertTrue(service.forAccount(-1).isEmpty());
    }
}
