/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
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

    @Inject
    public DemoWaitingListSeeder(
            WaitingListRepository waitingListRepository,
            MemberGroupRepository memberGroupRepository,
            StationMemberRepository stationMemberRepository,
            AttendanceRepository attendanceRepository) {
        this.waitingListRepository = waitingListRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.attendanceRepository = attendanceRepository;
    }

    public void seedWaitingList(int stationId, int joinGroupId, int joinRoleId) {
        // Create the "Gäste" group for testing-phase members
        var gaesteGroup = memberGroupRepository.create(stationId, "Gäste");

        var list = waitingListRepository.create(
                stationId,
                "Warteliste 2026",
                "Warteliste für neue Mitglieder im Jahr 2026",
                "[Alter] * (\"[Erfahrung]\" == \"fortgeschritten\" ? 2 : 1)",
                180,
                gaesteGroup.id(),
                joinGroupId,
                joinRoleId,
                5);

        var nameField = waitingListRepository.createField(list.id(), "Vorname", "TEXT", "{}", 0, true);
        var ageField = waitingListRepository.createField(list.id(), "Alter", "NUMBER", "{}", 1, true);
        var expField = waitingListRepository.createField(
                list.id(), "Erfahrung", "ENUM", "{\"options\":[\"anfaenger\",\"fortgeschritten\"]}", 2, true);

        // Create invite codes
        waitingListRepository.createInvite(list.id(), "demo-invite-active", 5, null);
        var usedInvite = waitingListRepository.createInvite(list.id(), "demo-invite-used", 1, null);
        waitingListRepository.incrementInviteUses(usedInvite.id());

        // Create sample entries
        record Kid(
                String firstname,
                String lastname,
                String parentName,
                String email,
                String vorname,
                String alter,
                String erfahrung,
                WaitingListEntryStatus status) {}
        var kids = List.of(
                new Kid(
                        "Max",
                        "Müller",
                        "Sabine Müller",
                        "sabine@example.com",
                        "Max",
                        "8",
                        "fortgeschritten",
                        WaitingListEntryStatus.WAITING),
                new Kid(
                        "Lena",
                        "Fischer",
                        "Thomas Fischer",
                        "thomas@example.com",
                        "Lena",
                        "7",
                        "anfaenger",
                        WaitingListEntryStatus.WAITING),
                new Kid(
                        "Tim",
                        "Bauer",
                        "Maria Bauer",
                        "maria@example.com",
                        "Tim",
                        "10",
                        "fortgeschritten",
                        WaitingListEntryStatus.TESTING),
                new Kid(
                        "Anna",
                        "Klein",
                        "Heike Klein",
                        "heike@example.com",
                        "Anna",
                        "9",
                        "anfaenger",
                        WaitingListEntryStatus.TESTING),
                new Kid(
                        "Sophie",
                        "Wagner",
                        "Klaus Wagner",
                        "klaus@example.com",
                        "Sophie",
                        "6",
                        "anfaenger",
                        WaitingListEntryStatus.JOINED),
                new Kid(
                        "Felix",
                        "Schmidt",
                        "Petra Schmidt",
                        "petra@example.com",
                        "Felix",
                        "9",
                        "fortgeschritten",
                        WaitingListEntryStatus.WITHDRAWN));

        // Collect testing member IDs to add attendance later
        var testingMemberIds = new ArrayList<Integer>();

        for (var kid : kids) {
            var entry = waitingListRepository.createEntry(
                    list.id(),
                    kid.firstname,
                    kid.lastname,
                    kid.parentName,
                    kid.email,
                    UUID.randomUUID().toString(),
                    "");
            waitingListRepository.upsertEntryValue(entry.id(), nameField.id(), "\"" + kid.vorname + "\"");
            waitingListRepository.upsertEntryValue(entry.id(), ageField.id(), kid.alter);
            waitingListRepository.upsertEntryValue(entry.id(), expField.id(), "\"" + kid.erfahrung + "\"");

            // For entries that progressed beyond WAITING, create a linked member
            if (kid.status != WaitingListEntryStatus.WAITING) {
                var member =
                        stationMemberRepository.createWithDisplayName(stationId, kid.firstname + " " + kid.lastname);
                stationMemberRepository
                        .findRoleByName(Roles.MEMBER)
                        .ifPresent(role -> stationMemberRepository.addRole(member.id(), role.id()));
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
                        waitingListRepository.updateEntryStatusWithTimestamp(
                                entry.id(), WaitingListEntryStatus.JOINED, "joined_at");
                    }
                    case WITHDRAWN -> {
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
