/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.StringNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds demo member data: groups, profile fields, users, former members, profile field changes,
 * manager assignments, and user tags.
 */
@Singleton
public class DemoMemberSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoMemberSeeder.class);
    private static final String NO_JUGENDFLAMME = "Keine";
    private static final String[] JUGENDFLAMME_LEVELS = {"Jugendflamme 1", "Jugendflamme 2", "Jugendflamme 3"};

    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberLookupService memberLookupService;
    private final MemberGroupRepository memberGroupRepository;
    private final MemberGroupSetRepository memberGroupSetRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final ProfileFieldChangeRepository profileFieldChangeRepository;
    private final UserTagRepository userTagRepository;
    private final StationRepository stationRepository;

    @Inject
    public DemoMemberSeeder(
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberLookupService memberLookupService,
            MemberGroupRepository memberGroupRepository,
            MemberGroupSetRepository memberGroupSetRepository,
            ProfileFieldRepository profileFieldRepository,
            ProfileFieldChangeRepository profileFieldChangeRepository,
            UserTagRepository userTagRepository,
            StationRepository stationRepository) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberLookupService = memberLookupService;
        this.memberGroupRepository = memberGroupRepository;
        this.memberGroupSetRepository = memberGroupSetRepository;
        this.profileFieldRepository = profileFieldRepository;
        this.profileFieldChangeRepository = profileFieldChangeRepository;
        this.userTagRepository = userTagRepository;
        this.stationRepository = stationRepository;
    }

    @Override
    public int order() {
        return MEMBERS;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var members = seed(station.stationId(), run.passwordHash(), station.profile(), new Random(42));
        station.members(members);
        station.adminMember(members.head());
        stationRepository.setOwner(station.stationId(), members.head().id());
    }

    /**
     * Seeds the groups, profile fields, team, guardians and kids of one station, with each guardian
     * managing the kids who share their last name.
     *
     * <p>Max Mustermann is the station administrator. Michael Wagner may read the members but not
     * manage them, because reading and notes are separate rights and a team where everyone holds the
     * whole bundle never shows the difference. The date of birth is one question put to four audiences:
     * only member management writes it by default, and the two audiences that may are told so on their
     * own row. The first guardian and the first beginner are left with incomplete profiles.
     */
    public SeedResult seed(int stationId, String passwordHash, DemoStationProfile profile, Random rng) {
        var loginRole = stationMemberRepository
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow();
        var memberRole = stationMemberRepository
                .findPermissionByName(StationPermission.USER)
                .orElseThrow();
        var memberManagerRole = stationMemberRepository
                .findPermissionByName(StationPermission.MEMBER_GUARDIAN)
                .orElseThrow();
        var attendanceMgmt = stationMemberRepository
                .findPermissionByName(StationPermission.ATTENDANCE_MANAGER)
                .orElseThrow();
        var eventMgmt = stationMemberRepository
                .findPermissionByName(StationPermission.EVENT_MANAGER)
                .orElseThrow();
        var memberMgmt = stationMemberRepository
                .findPermissionByName(StationPermission.MEMBER_MANAGER)
                .orElseThrow();
        var memberRead = stationMemberRepository
                .findPermissionByName(StationPermission.MEMBER_READ)
                .orElseThrow();

        var groupBetreuer = memberGroupRepository.create(stationId, "Betreuer");
        var groupEltern = memberGroupRepository.create(stationId, "Eltern");
        var groupAnfaenger = memberGroupRepository.create(stationId, "Anfänger");
        var groupFortgeschritten = memberGroupRepository.create(stationId, "Fortgeschritten");
        var levels = memberGroupSetRepository.create(stationId, "Ausbildungsstufen");
        memberGroupRepository.assignSet(groupAnfaenger.id(), levels.id());
        memberGroupRepository.assignSet(groupFortgeschritten.id(), levels.id());
        memberGroupRepository.replaceUserTypes(
                groupBetreuer.id(), List.of(StationUserType.TEAM, StationUserType.MANAGER));

        var fieldJuleica = askOf(stationId, "JuLeiCa", FieldType.BOOLEAN, "{}", ProfileFieldScope.TEAM, 0);
        var fieldJuleicaAblauf = askOf(
                stationId,
                "JuLeiCa Ablaufdatum",
                FieldType.EXPIRY_DATE,
                "{\"warnFromDays\":90,\"reminderDays\":[90,30],\"remindManagement\":true}",
                ProfileFieldScope.TEAM,
                1);
        var fieldFuehrerschein = askOf(stationId, "Führerschein", FieldType.BOOLEAN, "{}", ProfileFieldScope.TEAM, 2);
        var fieldFuehrerscheinAblauf = askOf(
                stationId,
                "Führerschein Ablaufdatum",
                FieldType.EXPIRY_DATE,
                "{\"warnFromDays\":60,\"reminderDays\":[60,14]}",
                ProfileFieldScope.TEAM,
                3);

        var fieldTelefon = askOf(
                stationId,
                "Mobilnummer",
                FieldType.TEXT,
                "{\"overview\":true}",
                true,
                ProfileFieldScope.GUARDIAN,
                0,
                false);
        var fieldFestnetz = askOf(stationId, "Festnetz", FieldType.TEXT, "{}", ProfileFieldScope.GUARDIAN, 1);
        var fieldNewsletter = askOf(
                stationId,
                "Newsletter per Mail",
                FieldType.BOOLEAN,
                "{\"defaultValue\":true}",
                ProfileFieldScope.GUARDIAN,
                2);

        var fieldPersonalnummer = askOf(
                stationId,
                "Personalnummer",
                FieldType.TEXT,
                "{\"overview\":true}",
                false,
                ProfileFieldScope.MEMBER,
                0,
                true);
        var fieldGeschlecht =
                askOf(stationId, "Geschlecht", FieldType.GENDER, """
                {"overview":true,"options":["männlich","weiblich","divers"],"pronouns":{
                  "männlich":{"de":{"subject":"er","object":"ihn","dative":"ihm","possessive":"sein"},
                              "en":{"subject":"he","object":"him","possessive":"his"}},
                  "weiblich":{"de":{"subject":"sie","object":"sie","dative":"ihr","possessive":"ihr"},
                              "en":{"subject":"she","object":"her","possessive":"her"}}}}""", false, ProfileFieldScope.MEMBER, 1, true);

        var fieldGeburtstag = askOf(
                stationId,
                "Geburtstag",
                FieldType.BIRTH_DATE,
                "{\"overview\":true}",
                true,
                ProfileFieldScope.MEMBER,
                2,
                true);
        profileFieldRepository.assignToRole(fieldGeburtstag.id(), ProfileFieldScope.TRIAL, 2, null, null, null);
        profileFieldRepository.assignToRole(fieldGeburtstag.id(), ProfileFieldScope.TEAM, 4, null, false, null);
        profileFieldRepository.assignToRole(fieldGeburtstag.id(), ProfileFieldScope.MANAGER, 4, null, false, null);

        var fieldAllergien = askOf(
                stationId,
                "Allergien",
                FieldType.TEXT,
                "{\"overview\":true,\"notifyOnChange\":true}",
                ProfileFieldScope.MEMBER,
                3);
        var fieldLeistungsspange =
                askOf(stationId, "Leistungsspange", FieldType.BOOLEAN, "{}", false, ProfileFieldScope.MEMBER, 4, true);
        var fieldLeistungsspangeDatum = askOf(
                stationId, "Leistungsspange Datum", FieldType.DATE, "{}", false, ProfileFieldScope.MEMBER, 5, true);
        var fieldJugendflamme = askOf(
                stationId,
                "Jugendflamme",
                FieldType.CHOICE,
                "{\"options\":[\"%s\",\"%s\",\"%s\",\"%s\"]}"
                        .formatted(
                                NO_JUGENDFLAMME,
                                JUGENDFLAMME_LEVELS[0],
                                JUGENDFLAMME_LEVELS[1],
                                JUGENDFLAMME_LEVELS[2]),
                false,
                ProfileFieldScope.MEMBER,
                6,
                true);
        var fieldJugendflammeDatum =
                askOf(stationId, "Jugendflamme Datum", FieldType.DATE, "{}", false, ProfileFieldScope.MEMBER, 7, true);

        record DemoUser(String firstName, String lastName) {}
        var betreuerData = List.of(
                new DemoUser("Max", "Mustermann"),
                new DemoUser("Anna", "Schmidt"),
                new DemoUser("Thomas", "Müller"),
                new DemoUser("Lisa", "Weber"),
                new DemoUser("Michael", "Wagner"));

        record Family(
                String parentFirstName,
                String lastName,
                List<String> anfaengerKids,
                List<String> fortgeschrittenKids) {}
        var families = List.of(
                new Family("Hans", "Berger", List.of("Tim", "Lena"), List.of("Mia")),
                new Family("Petra", "Frank", List.of("Lukas"), List.of("Ben", "Laura")),
                new Family("Klaus", "Schulze", List.of("Sophie", "Felix"), List.of()),
                new Family("Monika", "Lehmann", List.of("Emma"), List.of("Markus")),
                new Family("Jürgen", "König", List.of("Jonas", "Marie"), List.of("Nina")),
                new Family("Ursula", "Huber", List.of("Niklas"), List.of("Christian", "Sandra")),
                new Family("Werner", "Kaiser", List.of("Lea", "Paul"), List.of()),
                new Family("Ingrid", "Peters", List.of("Hannah"), List.of("Tobias", "Katharina")),
                new Family("Helmut", "Lang", List.of("Leon"), List.of("Andreas")),
                new Family("Gerda", "Scholz", List.of(), List.of("Melanie", "Patrick")));

        var elternData = new ArrayList<DemoUser>();
        var anfaengerData = new ArrayList<DemoUser>();
        var fortgeschrittenData = new ArrayList<DemoUser>();
        var allKidsIndicesPerFamily = new ArrayList<List<Integer>>();
        int anfaengerCounter = 0;
        int fortgeschrittenCounter = 0;
        int totalAnfaenger =
                families.stream().mapToInt(f -> f.anfaengerKids().size()).sum();
        for (var family : families) {
            elternData.add(new DemoUser(family.parentFirstName(), family.lastName()));
            var kidIndices = new ArrayList<Integer>();
            for (var kidName : family.anfaengerKids()) {
                anfaengerData.add(new DemoUser(kidName, family.lastName()));
                kidIndices.add(anfaengerCounter++);
            }
            for (var kidName : family.fortgeschrittenKids()) {
                fortgeschrittenData.add(new DemoUser(kidName, family.lastName()));
                kidIndices.add(totalAnfaenger + fortgeschrittenCounter++);
            }
            allKidsIndicesPerFamily.add(kidIndices);
        }

        var betreuerMembers = new ArrayList<StationMember>();
        var elternMembers = new ArrayList<StationMember>();
        var anfaengerMembers = new ArrayList<StationMember>();
        var fortgeschrittenMembers = new ArrayList<StationMember>();

        var stationAdminRole = stationMemberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();

        for (var u : betreuerData) {
            var m = createTeamMember(u.firstName(), u.lastName(), passwordHash, stationId, profile, loginRole.id());
            if (u.lastName().equals("Mustermann")) {
                stationMemberRepository.setUserType(m.id(), StationUserType.MANAGER);
                stationMemberRepository.grantPermission(m.id(), stationAdminRole.id());
                stationMemberRepository.setNickname(m.id(), "Maxe", m.id());
            } else if (u.lastName().equals("Wagner")) {
                stationMemberRepository.grantPermission(m.id(), attendanceMgmt.id());
                stationMemberRepository.grantPermission(m.id(), eventMgmt.id());
                stationMemberRepository.grantPermission(m.id(), memberRead.id());
            } else {
                stationMemberRepository.grantPermission(m.id(), attendanceMgmt.id());
                stationMemberRepository.grantPermission(m.id(), eventMgmt.id());
                stationMemberRepository.grantPermission(m.id(), memberMgmt.id());
            }
            memberGroupRepository.addMember(groupBetreuer.id(), m.id());
            betreuerMembers.add(m);

            boolean hasJuleica = rng.nextBoolean();
            profileFieldRepository.setValue(m.id(), fieldJuleica.id(), BooleanNode.valueOf(hasJuleica));
            if (hasJuleica) {
                profileFieldRepository.setValue(
                        m.id(),
                        fieldJuleicaAblauf.id(),
                        text(LocalDate.now()
                                .plusMonths(rng.nextInt(24) - 2)
                                .plusDays(20)
                                .toString()));
            }
            profileFieldRepository.setValue(m.id(), fieldFuehrerschein.id(), BooleanNode.TRUE);
            profileFieldRepository.setValue(
                    m.id(),
                    fieldFuehrerscheinAblauf.id(),
                    text(LocalDate.now().plusYears(rng.nextInt(5) + 1).toString()));
        }

        boolean firstEltern = true;
        for (var u : elternData) {
            var m = createGuardian(
                    u.firstName(),
                    u.lastName(),
                    passwordHash,
                    stationId,
                    profile,
                    loginRole.id(),
                    memberManagerRole.id());
            memberGroupRepository.addMember(groupEltern.id(), m.id());
            elternMembers.add(m);

            if (!firstEltern) {
                profileFieldRepository.setValue(
                        m.id(), fieldTelefon.id(), text("0151 " + (10000000 + rng.nextInt(90000000))));
            }
            firstEltern = false;
            profileFieldRepository.setValue(
                    m.id(), fieldFestnetz.id(), text("0208 " + (1000000 + rng.nextInt(9000000))));
            profileFieldRepository.setValue(m.id(), fieldNewsletter.id(), BooleanNode.valueOf(rng.nextBoolean()));
        }

        int personalNr = 100000 + rng.nextInt(900000);
        boolean firstAnfaenger = true;
        for (var u : anfaengerData) {
            var m = createUser(
                    u.firstName(), u.lastName(), passwordHash, stationId, profile, loginRole.id(), memberRole.id());
            memberGroupRepository.addMember(groupAnfaenger.id(), m.id());
            anfaengerMembers.add(m);

            profileFieldRepository.setValue(m.id(), fieldPersonalnummer.id(), text(String.valueOf(personalNr++)));

            if (!firstAnfaenger) {
                profileFieldRepository.setValue(
                        m.id(),
                        fieldGeburtstag.id(),
                        text(LocalDate.now()
                                .minusYears(10 + rng.nextInt(6))
                                .minusDays(rng.nextInt(365))
                                .toString()));
            }
            firstAnfaenger = false;

            profileFieldRepository.setValue(
                    m.id(), fieldGeschlecht.id(), text(rng.nextBoolean() ? "männlich" : "weiblich"));

            var jugendflamme = JugendflammeReached.NONE;
            if (rng.nextInt(3) == 0) {
                jugendflamme = jugendflamme.reached(1, LocalDate.now().minusMonths(rng.nextInt(12)));
            }
            seedJugendflamme(m.id(), fieldJugendflamme, fieldJugendflammeDatum, jugendflamme);
            if (rng.nextBoolean()) {
                profileFieldRepository.setValue(m.id(), fieldAllergien.id(), text(randomAllergy(rng)));
            }
        }

        for (var u : fortgeschrittenData) {
            var m = createUser(
                    u.firstName(), u.lastName(), passwordHash, stationId, profile, loginRole.id(), memberRole.id());
            memberGroupRepository.addMember(groupFortgeschritten.id(), m.id());
            fortgeschrittenMembers.add(m);

            profileFieldRepository.setValue(m.id(), fieldPersonalnummer.id(), text(String.valueOf(personalNr++)));

            profileFieldRepository.setValue(
                    m.id(),
                    fieldGeburtstag.id(),
                    text(LocalDate.now()
                            .minusYears(12 + rng.nextInt(6))
                            .minusDays(rng.nextInt(365))
                            .toString()));

            profileFieldRepository.setValue(
                    m.id(), fieldGeschlecht.id(), text(rng.nextBoolean() ? "männlich" : "weiblich"));

            var jugendflamme =
                    JugendflammeReached.NONE.reached(1, LocalDate.now().minusMonths(rng.nextInt(24) + 6));
            if (rng.nextInt(3) != 0) {
                jugendflamme = jugendflamme.reached(2, LocalDate.now().minusMonths(rng.nextInt(12)));
            }
            boolean reachedThird = rng.nextInt(5) == 0;
            if (reachedThird) {
                jugendflamme = jugendflamme.reached(3, LocalDate.now().minusMonths(rng.nextInt(6)));
            }
            seedJugendflamme(m.id(), fieldJugendflamme, fieldJugendflammeDatum, jugendflamme);
            if (reachedThird) {
                profileFieldRepository.setValue(m.id(), fieldLeistungsspange.id(), BooleanNode.TRUE);
                profileFieldRepository.setValue(
                        m.id(),
                        fieldLeistungsspangeDatum.id(),
                        text(LocalDate.now().minusMonths(rng.nextInt(3)).toString()));
            }
            if (rng.nextBoolean()) {
                profileFieldRepository.setValue(m.id(), fieldAllergien.id(), text(randomAllergy(rng)));
            }
        }

        var formerMember1 =
                createUser("Max", "Altmann", passwordHash, stationId, profile, loginRole.id(), memberRole.id());
        memberGroupRepository.addMember(groupAnfaenger.id(), formerMember1.id());
        stationMemberRepository.setFormer(formerMember1.id(), true);

        var formerMember2 =
                createUser("Lisa", "Wegner", passwordHash, stationId, profile, loginRole.id(), memberRole.id());
        memberGroupRepository.addMember(groupFortgeschritten.id(), formerMember2.id());
        stationMemberRepository.setFormer(formerMember2.id(), true);

        var formerMember3 = createTeamMember("Tom", "Richter", passwordHash, stationId, profile, loginRole.id());
        stationMemberRepository.setFormer(formerMember3.id(), true);

        if (anfaengerMembers.size() >= 3) {
            profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    anfaengerMembers.get(0).id(),
                    "\"0151 12345678\"",
                    "\"0171 98765432\"",
                    anfaengerMembers.get(0).id(),
                    true);
            profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    anfaengerMembers.get(1).id(),
                    "\"0152 11223344\"",
                    "\"0163 55667788\"",
                    anfaengerMembers.get(1).id(),
                    true);
            profileFieldChangeRepository.create(
                    fieldAllergien.id(),
                    anfaengerMembers.get(2).id(),
                    "\"Keine\"",
                    "\"Nussallergie\"",
                    anfaengerMembers.get(2).id(),
                    true);
        }

        if (anfaengerMembers.size() >= 5 && !betreuerMembers.isEmpty()) {
            int bId = betreuerMembers.getFirst().id();
            var c1 = profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    anfaengerMembers.get(3).id(),
                    "\"0155 33344455\"",
                    "\"0177 11122233\"",
                    anfaengerMembers.get(3).id(),
                    true);
            profileFieldChangeRepository.acknowledge(c1.id(), bId, null);
            var c2 = profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    anfaengerMembers.get(4).id(),
                    "\"0160 99988877\"",
                    "\"0172 44455566\"",
                    anfaengerMembers.get(4).id(),
                    true);
            profileFieldChangeRepository.acknowledge(c2.id(), bId, "Nummer geprüft");
            var c3 = profileFieldChangeRepository.create(
                    fieldAllergien.id(),
                    anfaengerMembers.get(3).id(),
                    "\"Keine\"",
                    "\"Laktoseintoleranz\"",
                    betreuerMembers.getFirst().id(),
                    true);
            profileFieldChangeRepository.acknowledge(c3.id(), bId, null);
            var c4 = profileFieldChangeRepository.create(
                    fieldAllergien.id(),
                    anfaengerMembers.get(4).id(),
                    "\"Heuschnupfen\"",
                    "\"Heuschnupfen, Hausstaub\"",
                    anfaengerMembers.get(4).id(),
                    true);
            profileFieldChangeRepository.acknowledge(c4.id(), bId, "Mit Eltern abgestimmt");
            profileFieldChangeRepository.create(
                    fieldGeburtstag.id(),
                    anfaengerMembers.get(3).id(),
                    "\"2014-05-10\"",
                    "\"2014-05-11\"",
                    betreuerMembers.getFirst().id(),
                    true);
            profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    fortgeschrittenMembers.getFirst().id(),
                    "\"0151 77766655\"",
                    "\"0176 88899900\"",
                    fortgeschrittenMembers.getFirst().id(),
                    false);
        }

        var allKids = new ArrayList<>(anfaengerMembers);
        allKids.addAll(fortgeschrittenMembers);
        for (int fi = 0; fi < families.size(); fi++) {
            var elternMember = elternMembers.get(fi);
            for (int kidIndex : allKidsIndicesPerFamily.get(fi)) {
                if (kidIndex < allKids.size()) {
                    stationMemberRepository.addManager(
                            elternMember.id(), allKids.get(kidIndex).id());
                }
            }
        }

        var tagWettkampf = userTagRepository.create(stationId, "Wettkampfgruppe");
        var tagErsthelfer = userTagRepository.create(stationId, "Ersthelfer");
        for (int i = 0; i < 6 && i < fortgeschrittenMembers.size(); i++) {
            userTagRepository.addMember(
                    tagWettkampf.id(), fortgeschrittenMembers.get(i).id());
        }
        for (int i = 0; i < 3 && i < betreuerMembers.size(); i++) {
            userTagRepository.addMember(
                    tagErsthelfer.id(), betreuerMembers.get(i).id());
        }

        var tagJfw = userTagRepository.create(stationId, "JFW");
        userTagRepository.update(tagJfw.id(), "JFW", "#FF6421", true, 10);
        for (var m : betreuerMembers) {
            userTagRepository.addMember(tagJfw.id(), m.id());
        }

        log.info(
                "Demo: Created {} members (betreuer={}, eltern={}, anfaenger={}, fortgeschritten={})",
                betreuerMembers.size() + elternMembers.size() + anfaengerMembers.size() + fortgeschrittenMembers.size(),
                betreuerMembers.size(),
                elternMembers.size(),
                anfaengerMembers.size(),
                fortgeschrittenMembers.size());

        StationMember head = betreuerMembers.getFirst();

        return new SeedResult(
                head,
                List.copyOf(betreuerMembers),
                List.copyOf(anfaengerMembers),
                List.copyOf(fortgeschrittenMembers),
                List.copyOf(elternMembers),
                groupBetreuer,
                groupEltern,
                groupAnfaenger,
                groupFortgeschritten,
                tagWettkampf,
                tagErsthelfer);
    }

    private StationMember createUser(
            String firstName,
            String lastName,
            String hash,
            int stationId,
            DemoStationProfile profile,
            int loginRoleId,
            int memberRoleId) {
        String email = profile.address(firstName, lastName);
        var account = accountRepository.create(email, firstName, lastName, true);
        accountRepository.setUid(account.id(), DemoUids.account(email));
        accountRepository.createCredential(account.id(), hash);
        var member = stationMemberRepository.create(stationId, account.id());
        memberLookupService.setUid(member.id(), DemoUids.member(email, stationId));
        stationMemberRepository.setUserType(member.id(), StationUserType.MEMBER);
        stationMemberRepository.grantPermission(member.id(), loginRoleId);
        stationMemberRepository.grantPermission(member.id(), memberRoleId);
        return member;
    }

    private StationMember createTeamMember(
            String firstName,
            String lastName,
            String hash,
            int stationId,
            DemoStationProfile profile,
            int loginRoleId) {
        String email = profile.address(firstName, lastName);
        var account = accountRepository.create(email, firstName, lastName, true);
        accountRepository.setUid(account.id(), DemoUids.account(email));
        accountRepository.createCredential(account.id(), hash);
        var member = stationMemberRepository.create(stationId, account.id());
        memberLookupService.setUid(member.id(), DemoUids.member(email, stationId));
        stationMemberRepository.setUserType(member.id(), StationUserType.TEAM);
        stationMemberRepository.grantPermission(member.id(), loginRoleId);
        return member;
    }

    private StationMember createGuardian(
            String firstName,
            String lastName,
            String hash,
            int stationId,
            DemoStationProfile profile,
            int loginRoleId,
            int guardianRoleId) {
        String email = profile.address(firstName, lastName);
        var account = accountRepository.create(email, firstName, lastName, true);
        accountRepository.setUid(account.id(), DemoUids.account(email));
        accountRepository.createCredential(account.id(), hash);
        var member = stationMemberRepository.create(stationId, account.id());
        memberLookupService.setUid(member.id(), DemoUids.member(email, stationId));
        stationMemberRepository.setUserType(member.id(), StationUserType.GUARDIAN);
        stationMemberRepository.grantPermission(member.id(), loginRoleId);
        stationMemberRepository.grantPermission(member.id(), guardianRoleId);
        return member;
    }

    private StringNode text(String value) {
        return StringNode.valueOf(value);
    }

    private String randomAllergy(Random rng) {
        var allergies = List.of(
                "Nussallergie",
                "Laktoseintoleranz",
                "Glutenunverträglichkeit",
                "Pollenallergie",
                "Tierhaarallergie",
                "Keine");
        return allergies.get(rng.nextInt(allergies.size()));
    }

    /**
     * Result of member seeding, containing all created member groups and lists needed by other seeders.
     * The {@code head} is the station's primary manager - used by downstream seeders as the
     * creator / owner of station-scoped content (news, events, KB, pages, …).
     */
    public record SeedResult(
            StationMember head,
            List<StationMember> betreuer,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten,
            List<StationMember> eltern,
            MemberGroup groupBetreuer,
            MemberGroup groupEltern,
            MemberGroup groupAnfaenger,
            MemberGroup groupFortgeschritten,
            UserTag tagWettkampf,
            UserTag tagErsthelfer) {}

    /**
     * Writes a question down and puts it to one kind of member, which is the ordinary case.
     *
     * @param stationId the station asking
     * @param name      what the question is called
     * @param type      what kind of answer it takes
     * @param config    the rest of what the question is, as JSON
     * @param role      who is asked
     * @param position  where it sits on their form
     * @return the definition, so a caller can assign it to somebody else as well
     */
    private ProfileField askOf(
            int stationId, String name, FieldType type, String config, ProfileFieldScope role, int position) {
        return askOf(stationId, name, type, config, false, role, position, false);
    }

    /**
     * The same, where an answer is expected or only the member management may write it.
     *
     * @param required whether an answer is expected of everybody asked
     * @param readonly whether only the member management may write the answer
     */
    private ProfileField askOf(
            int stationId,
            String name,
            FieldType type,
            String config,
            boolean required,
            ProfileFieldScope role,
            int position,
            boolean readonly) {
        var field = profileFieldRepository.create(
                stationId, name, type, ProfileFieldConfig.parse(config), required, readonly, null);
        profileFieldRepository.assignToRole(field.id(), role, position, null, null, null);
        return field;
    }

    /**
     * Writes the highest Jugendflamme a member has reached, and the day they reached it where they
     * reached one at all.
     */
    private void seedJugendflamme(
            int memberId, ProfileField levelField, ProfileField dateField, JugendflammeReached reached) {
        profileFieldRepository.setValue(memberId, levelField.id(), text(reached.label()));
        if (reached.on() != null) {
            profileFieldRepository.setValue(
                    memberId, dateField.id(), text(reached.on().toString()));
        }
    }

    /**
     * The highest Jugendflamme a member has passed so far.
     *
     * @param level the level, 0 for none
     * @param on    the day it was passed, {@code null} for none
     */
    private record JugendflammeReached(int level, LocalDate on) {
        static final JugendflammeReached NONE = new JugendflammeReached(0, null);

        /** This or the given level, whichever is higher. */
        JugendflammeReached reached(int passed, LocalDate day) {
            return passed > level ? new JugendflammeReached(passed, day) : this;
        }

        String label() {
            return level == 0 ? NO_JUGENDFLAMME : JUGENDFLAMME_LEVELS[level - 1];
        }
    }
}
