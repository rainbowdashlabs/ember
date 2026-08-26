/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldType;
import dev.chojo.ember.feature.waitinglist.repository.WaitingListRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.StringNode;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Seeder for demo waiting list data with sample entries and invite codes.
 */
@Singleton
public class DemoWaitingListSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoWaitingListSeeder.class);
    private final WaitingListRepository waitingListRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public DemoWaitingListSeeder(
            WaitingListRepository waitingListRepository,
            MemberGroupRepository memberGroupRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository) {
        this.waitingListRepository = waitingListRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Before the parallel band, because this is where the roster stops moving.
     *
     * <p>An applicant who withdrew after being invited is deleted again, member and account both. Run
     * beside a seeder that lists the station's members, that deletion lands between the listing and
     * the write that follows it, and the write points at somebody who is no longer there.
     */
    @Override
    public int order() {
        return WAITING_LIST;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        seedWaitingList(
                station.stationId(),
                station.members().groupAnfaenger().id(),
                station.profile().addressSuffix());
        log.info("Demo: Created Waiting list");
    }

    /**
     * @param codeSuffix distinguishes this station's invite codes, which are one instance's namespace rather
     *                   than one station's: two stations handing out {@code demo-invite-active} would be one
     *                   code, and the second station could not be seeded at all
     */
    public void seedWaitingList(int stationId, int joinGroupId, String codeSuffix) {
        // Create the "Gäste" group for testing-phase members
        var gaesteGroup = memberGroupRepository.create(stationId, "Gäste");

        // --- Jugendfeuerwehr waitlist ---
        var list = waitingListRepository.create(
                stationId,
                "Jugendfeuerwehr",
                "Warteliste für neue Mitglieder der Jugendfeuerwehr (10–18 Jahre). Melde dich an und wir laden dich zu einer Schnupperübung ein.",
                "age([Geburtsdatum]) * (\"[Erfahrung]\" == \"fortgeschritten\" ? 2 : 1)",
                180,
                gaesteGroup.id(),
                joinGroupId,
                5,
                true,
                null,
                null);

        var birthdayField = waitingListRepository.createField(
                list.id(), "Geburtsdatum", WaitingListFieldType.DATE, WaitingListFieldConfig.EMPTY, 0, true, true);
        var expField = waitingListRepository.createField(
                list.id(),
                "Erfahrung",
                WaitingListFieldType.ENUM,
                new WaitingListFieldConfig(List.of("Anfänger", "Fortgeschritten"), null),
                1,
                true,
                true);

        // --- Kinderfeuerwehr waitlist ---
        var kinderList = waitingListRepository.create(
                stationId,
                "Kinderfeuerwehr",
                "Warteliste für die Kinderfeuerwehr (6–10 Jahre). Spielerisch die Feuerwehr kennenlernen!",
                null,
                365,
                null,
                null,
                0,
                true,
                null,
                null);
        waitingListRepository.createField(
                kinderList.id(),
                "Name des Kindes",
                WaitingListFieldType.TEXT,
                WaitingListFieldConfig.EMPTY,
                0,
                true,
                true);
        waitingListRepository.createField(
                kinderList.id(),
                "Geburtsdatum",
                WaitingListFieldType.DATE,
                WaitingListFieldConfig.EMPTY,
                1,
                true,
                true);
        waitingListRepository.createInvite(kinderList.id(), "demo-kinder-invite" + codeSuffix, 10, null);

        // --- Schnupperstunde: a list that asks for nothing beyond a name and an address ---
        // Both other lists insist on answers of their own - a date of birth, an experience level -
        // which is right for them and makes them useless for showing what the bare registration
        // looks like, in the demo as much as in the end-to-end suite.
        waitingListRepository.create(
                stationId,
                "Schnupperstunde",
                "Einmal reinschnuppern, ohne Angaben: Name und E-Mail genügen, wir melden uns mit einem Termin.",
                null,
                365,
                null,
                null,
                0,
                true,
                null,
                null);

        // Create invite codes
        waitingListRepository.createInvite(list.id(), "demo-invite-active" + codeSuffix, 5, null);
        var usedInvite = waitingListRepository.createInvite(list.id(), "demo-invite-used" + codeSuffix, 1, null);
        waitingListRepository.incrementInviteUses(usedInvite.id());

        // Create sample entries
        record Kid(
                String firstname,
                String lastname,
                String parentFirstname,
                String email,
                String alter,
                String erfahrung,
                WaitingListEntryStatus status) {}
        var kids = List.of(
                new Kid(
                        "Max",
                        "Müller",
                        "Sabine",
                        "sabine@example.com",
                        "8",
                        "Fortgeschritten",
                        WaitingListEntryStatus.WAITING),
                new Kid(
                        "Lena",
                        "Fischer",
                        "Thomas",
                        "thomas@example.com",
                        "7",
                        "Anfänger",
                        WaitingListEntryStatus.WAITING),
                new Kid(
                        "Tim",
                        "Bauer",
                        "Maria",
                        "maria@example.com",
                        "10",
                        "Fortgeschritten",
                        WaitingListEntryStatus.TESTING),
                new Kid("Anna", "Klein", "Heike", "heike@example.com", "9", "Anfänger", WaitingListEntryStatus.TESTING),
                new Kid(
                        "Sophie",
                        "Wagner",
                        "Klaus",
                        "klaus@example.com",
                        "6",
                        "Anfänger",
                        WaitingListEntryStatus.JOINED),
                new Kid(
                        "Felix",
                        "Schmidt",
                        "Petra",
                        "petra@example.com",
                        "9",
                        "Fortgeschritten",
                        WaitingListEntryStatus.WITHDRAWN),
                new Kid(
                        "Jonas",
                        "Lehmann",
                        "Andrea",
                        "andrea@example.com",
                        "8",
                        "Anfänger",
                        WaitingListEntryStatus.PENDING),
                new Kid(
                        "Mia",
                        "Hoffmann",
                        "Carsten",
                        "carsten@example.com",
                        "7",
                        "Anfänger",
                        WaitingListEntryStatus.PENDING));

        for (var kid : kids) {
            var entry = waitingListRepository.createEntry(
                    list.id(),
                    kid.firstname,
                    kid.lastname,
                    kid.parentFirstname + " " + kid.lastname,
                    kid.email,
                    UUID.randomUUID().toString(),
                    "",
                    null);
            LocalDate birthday = LocalDate.now().minusYears(Integer.parseInt(kid.alter));
            waitingListRepository.upsertEntryValue(
                    entry.id(), birthdayField.id(), StringNode.valueOf(birthday.toString()));
            waitingListRepository.upsertEntryValue(entry.id(), expField.id(), StringNode.valueOf(kid.erfahrung));

            waitingListRepository.createGuardian(
                    entry.id(), kid.parentFirstname, kid.lastname, kid.email, "+49 170 " + (1000000 + entry.id()), 0);
            if (entry.id() % 2 == 0) {
                waitingListRepository.createGuardian(
                        entry.id(),
                        "Zweit-EB",
                        kid.lastname,
                        "zweit-" + kid.email,
                        "+49 171 " + (2000000 + entry.id()),
                        1);
            }

            // PENDING entries just need their status set (from public registration)
            if (kid.status == WaitingListEntryStatus.PENDING) {
                waitingListRepository.updateEntryStatus(entry.id(), WaitingListEntryStatus.PENDING);
                continue;
            }

            // For entries that progressed beyond WAITING, create a linked member
            if (kid.status != WaitingListEntryStatus.WAITING) {
                var account = accountRepository.create(null, kid.firstname, kid.lastname);
                var member = stationMemberRepository.create(stationId, account.id());
                stationMemberRepository
                        .findPermissionByName(StationPermission.USER)
                        .ifPresent(role -> stationMemberRepository.grantPermission(member.id(), role.id()));
                waitingListRepository.linkMember(entry.id(), member.id());
                waitingListRepository.updateEntryStatusWithTimestamp(
                        entry.id(), WaitingListEntryStatus.INVITED, "invited_at");

                switch (kid.status) {
                    case TESTING -> {
                        // Match WaitingListService.inviteEntry: members carried over from the
                        // waiting list during their trial period live under the TRIAL user type,
                        // not the schema default of MEMBER, so they show up correctly on the
                        // members overview and in the testing-group section.
                        stationMemberRepository.setUserType(member.id(), StationUserType.TRIAL);
                        memberGroupRepository.addMember(gaesteGroup.id(), member.id());
                        waitingListRepository.updateEntryStatusWithTimestamp(
                                entry.id(), WaitingListEntryStatus.TESTING, "testing_at");
                    }
                    case JOINED -> {
                        // The applicant first goes through the TRIAL phase like a real waiting-list
                        // transition does, then graduates to a full MEMBER once joined.
                        stationMemberRepository.setUserType(member.id(), StationUserType.TRIAL);
                        waitingListRepository.updateEntryStatusWithTimestamp(
                                entry.id(), WaitingListEntryStatus.TESTING, "testing_at");
                        stationMemberRepository
                                .findPermissionByName(StationPermission.USER)
                                .ifPresent(role -> stationMemberRepository.revokePermission(member.id(), role.id()));
                        stationMemberRepository.setUserType(member.id(), StationUserType.MEMBER);
                        memberGroupRepository.addMember(joinGroupId, member.id());
                        waitingListRepository.updateEntryStatusWithTimestamp(
                                entry.id(), WaitingListEntryStatus.JOINED, "joined_at");
                    }
                    case WITHDRAWN -> {
                        // Delete the member and orphaned account
                        stationMemberRepository.delete(member.id());
                        accountRepository.delete(account.id());
                        waitingListRepository.updateEntryStatusWithTimestamp(
                                entry.id(), WaitingListEntryStatus.WITHDRAWN, "withdrawn_at");
                    }
                    default -> {}
                }
            }
        }
    }
}
