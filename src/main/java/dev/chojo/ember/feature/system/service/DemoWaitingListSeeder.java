/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldType;
import dev.chojo.ember.feature.waitinglist.repository.WaitingListRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeder for demo waiting list data with sample entries and invite codes.
 */
@Singleton
public class DemoWaitingListSeeder {
    private final WaitingListRepository waitingListRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AttendanceRepository attendanceRepository;
    private final AccountRepository accountRepository;

    @Inject
    public DemoWaitingListSeeder(
            WaitingListRepository waitingListRepository,
            MemberGroupRepository memberGroupRepository,
            StationMemberRepository stationMemberRepository,
            AttendanceRepository attendanceRepository,
            AccountRepository accountRepository) {
        this.waitingListRepository = waitingListRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.attendanceRepository = attendanceRepository;
        this.accountRepository = accountRepository;
    }

    public void seedWaitingList(int stationId, int joinGroupId) {
        // Create the "Gäste" group for testing-phase members
        var gaesteGroup = memberGroupRepository.create(stationId, "Gäste");

        // --- Jugendfeuerwehr waitlist ---
        var list = waitingListRepository.create(
                stationId,
                "Jugendfeuerwehr",
                "Warteliste für neue Mitglieder der Jugendfeuerwehr (10–18 Jahre). Melde dich an und wir laden dich zu einer Schnupperübung ein.",
                "[Alter] * (\"[Erfahrung]\" == \"fortgeschritten\" ? 2 : 1)",
                180,
                gaesteGroup.id(),
                joinGroupId,
                5,
                true);

        var nameField = waitingListRepository.createField(
                list.id(), "Vorname", WaitingListFieldType.TEXT, WaitingListFieldConfig.parse("{}"), 0, true, true);
        var ageField = waitingListRepository.createField(
                list.id(), "Alter", WaitingListFieldType.NUMBER, WaitingListFieldConfig.parse("{}"), 1, true, true);
        var expField = waitingListRepository.createField(
                list.id(),
                "Erfahrung",
                WaitingListFieldType.ENUM,
                WaitingListFieldConfig.parse("{\"options\":[\"Anfänger\",\"Fortgeschritten\"]}"),
                2,
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
                true);
        waitingListRepository.createField(
                kinderList.id(),
                "Name des Kindes",
                WaitingListFieldType.TEXT,
                WaitingListFieldConfig.parse("{}"),
                0,
                true,
                true);
        waitingListRepository.createField(
                kinderList.id(),
                "Geburtsdatum",
                WaitingListFieldType.DATE,
                WaitingListFieldConfig.parse("{}"),
                1,
                true,
                true);
        waitingListRepository.createInvite(kinderList.id(), "demo-kinder-invite", 10, null);

        // Create invite codes
        waitingListRepository.createInvite(list.id(), "demo-invite-active", 5, null);
        var usedInvite = waitingListRepository.createInvite(list.id(), "demo-invite-used", 1, null);
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

        // Collect testing member IDs to add attendance later
        var testingMemberIds = new ArrayList<Integer>();

        for (var kid : kids) {
            var entry = waitingListRepository.createEntry(
                    list.id(),
                    kid.firstname,
                    kid.lastname,
                    kid.parentFirstname + " " + kid.lastname,
                    kid.email,
                    UUID.randomUUID().toString(),
                    "");
            waitingListRepository.upsertEntryValue(entry.id(), nameField.id(), "\"" + kid.firstname + "\"");
            waitingListRepository.upsertEntryValue(entry.id(), ageField.id(), kid.alter);
            waitingListRepository.upsertEntryValue(entry.id(), expField.id(), "\"" + kid.erfahrung + "\"");

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
                        memberGroupRepository.addMember(gaesteGroup.id(), member.id());
                        waitingListRepository.updateEntryStatusWithTimestamp(
                                entry.id(), WaitingListEntryStatus.TESTING, "testing_at");
                        testingMemberIds.add(member.id());
                    }
                    case JOINED -> {
                        waitingListRepository.updateEntryStatusWithTimestamp(
                                entry.id(), WaitingListEntryStatus.TESTING, "testing_at");
                        // Remove TRIAL, assign MEMBER user type, move to join group
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

        // Add testing members to recent attendance sessions as guests
        if (!testingMemberIds.isEmpty()) {
            var sessions = attendanceRepository.findRecentSessions(stationId, 10);
            for (var session : sessions) {
                for (int memberId : testingMemberIds) {
                    attendanceRepository.createEntry(
                            session.id(),
                            memberId,
                            AttendanceEntry.AttendanceStatus.PRESENT,
                            AttendanceEntry.EntrySource.EXTRA);
                }
            }
        }
    }
}
