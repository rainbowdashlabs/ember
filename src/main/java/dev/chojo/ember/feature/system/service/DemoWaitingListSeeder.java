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
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
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
     * <p>Applicants in their trial period and beyond arrive on the station as members here. Run
     * beside a seeder that lists the station's members, those rows land between the listing and the
     * write that follows it, and half the band works from a roster the other half has already
     * changed.
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
     * Seeds three lists with invite codes and sample entries. The Schnupperstunde list asks for nothing
     * beyond name and address, so the bare registration can be shown. Being invited is only a status
     * and a date; a joined applicant passes through the trial phase first, and one withdrawn straight
     * from the invitation has no member to take back.
     *
     * @param codeSuffix distinguishes this station's invite codes, which are one instance's namespace rather
     *                   than one station's: two stations handing out {@code demo-invite-active} would be one
     *                   code, and the second station could not be seeded at all
     */
    public void seedWaitingList(int stationId, int joinGroupId, String codeSuffix) {
        var gaesteGroup = memberGroupRepository.create(stationId, "Gäste");

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
                true,
                null,
                null);

        var birthdayField = waitingListRepository.createField(
                list.id(), "Geburtsdatum", FieldType.BIRTH_DATE, WaitingListFieldConfig.EMPTY, 0, true, true);
        var expField = waitingListRepository.createField(
                list.id(),
                "Erfahrung",
                FieldType.CHOICE,
                new WaitingListFieldConfig(List.of("Anfänger", "Fortgeschritten"), null),
                1,
                true,
                true);

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
                true,
                null,
                null);
        waitingListRepository.createField(
                kinderList.id(), "Name des Kindes", FieldType.TEXT, WaitingListFieldConfig.EMPTY, 0, true, true);
        waitingListRepository.createField(
                kinderList.id(), "Geburtsdatum", FieldType.BIRTH_DATE, WaitingListFieldConfig.EMPTY, 1, true, true);
        waitingListRepository.createInvite(kinderList.id(), "demo-kinder-invite" + codeSuffix, 10, null);

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
                true,
                null,
                null);

        waitingListRepository.createInvite(list.id(), "demo-invite-active" + codeSuffix, 5, null);
        var usedInvite = waitingListRepository.createInvite(list.id(), "demo-invite-used" + codeSuffix, 1, null);
        waitingListRepository.incrementInviteUses(usedInvite.id());

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

            if (kid.status == WaitingListEntryStatus.PENDING) {
                waitingListRepository.updateEntryStatus(entry.id(), WaitingListEntryStatus.PENDING);
                continue;
            }

            if (kid.status != WaitingListEntryStatus.WAITING) {
                waitingListRepository.updateEntryStatusWithTimestamp(
                        entry.id(), WaitingListEntryStatus.INVITED, "invited_at");

                switch (kid.status) {
                    case TESTING -> {
                        int memberId = createTrialMember(stationId, entry.id(), kid.firstname, kid.lastname);
                        memberGroupRepository.addMember(gaesteGroup.id(), memberId);
                        waitingListRepository.updateEntryStatusWithTimestamp(
                                entry.id(), WaitingListEntryStatus.TESTING, "testing_at");
                    }
                    case JOINED -> {
                        int memberId = createTrialMember(stationId, entry.id(), kid.firstname, kid.lastname);
                        waitingListRepository.updateEntryStatusWithTimestamp(
                                entry.id(), WaitingListEntryStatus.TESTING, "testing_at");
                        stationMemberRepository
                                .findPermissionByName(StationPermission.USER)
                                .ifPresent(role -> stationMemberRepository.revokePermission(memberId, role.id()));
                        stationMemberRepository.setUserType(memberId, StationUserType.MEMBER);
                        memberGroupRepository.addMember(joinGroupId, memberId);
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
    }

    /**
     * The account and the membership a trial period runs on, the way the waiting list writes them
     * when somebody turns up for the first time: under the trial user type rather than the schema
     * default, so the member shows on the members overview and in the testing group.
     *
     * @return the id of the new member
     */
    private int createTrialMember(int stationId, int entryId, String firstname, String lastname) {
        var account = accountRepository.create(null, firstname, lastname);
        var member = stationMemberRepository.create(stationId, account.id());
        stationMemberRepository.setUserType(member.id(), StationUserType.TRIAL);
        stationMemberRepository
                .findPermissionByName(StationPermission.USER)
                .ifPresent(role -> stationMemberRepository.grantPermission(member.id(), role.id()));
        waitingListRepository.linkMember(entryId, member.id());
        return member.id();
    }
}
