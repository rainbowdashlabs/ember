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
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds demo member data: groups, profile fields, users, former members, profile field changes,
 * manager assignments, and user tags.
 */
@Singleton
public class DemoMemberSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoMemberSeeder.class);

    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final ProfileFieldChangeRepository profileFieldChangeRepository;
    private final UserTagRepository userTagRepository;

    @Inject
    public DemoMemberSeeder(
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            ProfileFieldRepository profileFieldRepository,
            ProfileFieldChangeRepository profileFieldChangeRepository,
            UserTagRepository userTagRepository) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.profileFieldRepository = profileFieldRepository;
        this.profileFieldChangeRepository = profileFieldChangeRepository;
        this.userTagRepository = userTagRepository;
    }

    /**
     * Result of member seeding, containing all created member groups and lists needed by other seeders.
     * The {@code head} is the station's primary manager — used by downstream seeders as the
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

    public SeedResult seed(int stationId, String passwordHash, Random rng) {
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

        // -- Groups --
        var groupBetreuer = memberGroupRepository.create(stationId, "Betreuer");
        var groupEltern = memberGroupRepository.create(stationId, "Eltern");
        var groupAnfaenger = memberGroupRepository.create(stationId, "Anfänger");
        var groupFortgeschritten = memberGroupRepository.create(stationId, "Fortgeschritten");

        // -- Profile fields: TEAM scope (Betreuer) --
        var fieldJuleica = profileFieldRepository.create(
                stationId,
                "Juleica",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.parse("{}"),
                0,
                ProfileFieldScope.TEAM);
        var fieldJuleicaAblauf = profileFieldRepository.create(
                stationId,
                "Juleica Ablaufdatum",
                ProfileFieldType.DATE,
                ProfileFieldConfig.parse("{}"),
                1,
                ProfileFieldScope.TEAM);
        var fieldFuehrerschein = profileFieldRepository.create(
                stationId,
                "Führerschein",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.parse("{}"),
                2,
                ProfileFieldScope.TEAM);
        var fieldFuehrerscheinAblauf = profileFieldRepository.create(
                stationId,
                "Führerschein Ablaufdatum",
                ProfileFieldType.DATE,
                ProfileFieldConfig.parse("{}"),
                3,
                ProfileFieldScope.TEAM);

        // -- Profile fields: GUARDIAN scope (Eltern) --
        var fieldTelefon = profileFieldRepository.create(
                stationId,
                "Mobilnummer",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{\"overview\":true,\"required\":true}"),
                0,
                ProfileFieldScope.GUARDIAN);
        var fieldFestnetz = profileFieldRepository.create(
                stationId,
                "Festnetz",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                1,
                ProfileFieldScope.GUARDIAN);
        var fieldNewsletter = profileFieldRepository.create(
                stationId,
                "Newsletter per Mail",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.parse("{\"defaultValue\":true}"),
                2,
                ProfileFieldScope.GUARDIAN);

        // -- Profile fields: MEMBER scope (kids) --
        var fieldPersonalnummer = profileFieldRepository.create(
                stationId,
                "Personalnummer",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{\"readonly\":true,\"overview\":true}"),
                0,
                ProfileFieldScope.MEMBER);
        var fieldGeschlecht = profileFieldRepository.create(
                stationId,
                "Geschlecht",
                ProfileFieldType.ENUM,
                ProfileFieldConfig.parse(
                        "{\"readonly\":true,\"overview\":true,\"options\":[\"männlich\",\"weiblich\",\"divers\"]}"),
                1,
                ProfileFieldScope.MEMBER);
        var fieldGeburtstag = profileFieldRepository.create(
                stationId,
                "Geburtstag",
                ProfileFieldType.DATE,
                ProfileFieldConfig.parse("{\"required\":true,\"overview\":true}"),
                2,
                ProfileFieldScope.MEMBER);
        var fieldAllergien = profileFieldRepository.create(
                stationId,
                "Allergien",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{\"overview\":true,\"notifyOnChange\":true}"),
                3,
                ProfileFieldScope.MEMBER);
        var fieldLeistungsspange = profileFieldRepository.create(
                stationId,
                "Leistungsspange",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.parse("{\"readonly\":true}"),
                4,
                ProfileFieldScope.MEMBER);
        var fieldLeistungsspangeDatum = profileFieldRepository.create(
                stationId,
                "Leistungsspange Datum",
                ProfileFieldType.DATE,
                ProfileFieldConfig.parse("{\"readonly\":true}"),
                5,
                ProfileFieldScope.MEMBER);
        var fieldJF1 = profileFieldRepository.create(
                stationId,
                "Jugendflamme 1",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.parse("{\"readonly\":true}"),
                6,
                ProfileFieldScope.MEMBER);
        var fieldJF1Datum = profileFieldRepository.create(
                stationId,
                "Jugendflamme 1 Datum",
                ProfileFieldType.DATE,
                ProfileFieldConfig.parse("{\"readonly\":true}"),
                7,
                ProfileFieldScope.MEMBER);
        var fieldJF2 = profileFieldRepository.create(
                stationId,
                "Jugendflamme 2",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.parse("{\"readonly\":true}"),
                8,
                ProfileFieldScope.MEMBER);
        var fieldJF2Datum = profileFieldRepository.create(
                stationId,
                "Jugendflamme 2 Datum",
                ProfileFieldType.DATE,
                ProfileFieldConfig.parse("{\"readonly\":true}"),
                9,
                ProfileFieldScope.MEMBER);
        var fieldJF3 = profileFieldRepository.create(
                stationId,
                "Jugendflamme 3",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.parse("{\"readonly\":true}"),
                10,
                ProfileFieldScope.MEMBER);
        var fieldJF3Datum = profileFieldRepository.create(
                stationId,
                "Jugendflamme 3 Datum",
                ProfileFieldType.DATE,
                ProfileFieldConfig.parse("{\"readonly\":true}"),
                11,
                ProfileFieldScope.MEMBER);

        // -- Users --
        // Betreuer (team role, in Betreuer group)
        record DemoUser(String firstName, String lastName) {}
        var betreuerData = List.of(
                new DemoUser("Max", "Mustermann"),
                new DemoUser("Anna", "Schmidt"),
                new DemoUser("Thomas", "Müller"),
                new DemoUser("Lisa", "Weber"),
                new DemoUser("Michael", "Wagner"));

        // Families: parent + kids sharing the same last name
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

        // Build user lists from families
        var elternData = new ArrayList<DemoUser>();
        var anfaengerData = new ArrayList<DemoUser>();
        var fortgeschrittenData = new ArrayList<DemoUser>();
        // Track which kids belong to which parent index for manager assignment
        // Indices are into allKids = anfaengerMembers ++ fortgeschrittenMembers
        var familyKidIndices = new ArrayList<List<Integer>>(); // per family: indices into allKids
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
            familyKidIndices.add(kidIndices);
        }

        var betreuerMembers = new ArrayList<StationMember>();
        var elternMembers = new ArrayList<StationMember>();
        var anfaengerMembers = new ArrayList<StationMember>();
        var fortgeschrittenMembers = new ArrayList<StationMember>();

        var stationAdminRole = stationMemberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();

        // Create Betreuer (TEAM -- not MEMBER). Max Mustermann is the station administrator
        // (replaces the former instance-admin-as-station-member setup), every other Betreuer
        // keeps the attendance / event / member management bundle.
        for (var u : betreuerData) {
            var m = createTeamMember(u.firstName(), u.lastName(), passwordHash, stationId, loginRole.id());
            if (u.lastName().equals("Mustermann")) {
                stationMemberRepository.setUserType(m.id(), StationUserType.MANAGER);
                stationMemberRepository.grantPermission(m.id(), stationAdminRole.id());
            } else {
                stationMemberRepository.grantPermission(m.id(), attendanceMgmt.id());
                stationMemberRepository.grantPermission(m.id(), eventMgmt.id());
                stationMemberRepository.grantPermission(m.id(), memberMgmt.id());
            }
            memberGroupRepository.addMember(groupBetreuer.id(), m.id());
            betreuerMembers.add(m);

            // Profile data
            boolean hasJuleica = rng.nextBoolean();
            profileFieldRepository.setValue(m.id(), fieldJuleica.id(), Boolean.toString(hasJuleica));
            if (hasJuleica) {
                profileFieldRepository.setValue(
                        m.id(),
                        fieldJuleicaAblauf.id(),
                        jsonStr(LocalDate.now().plusMonths(rng.nextInt(24)).toString()));
            }
            profileFieldRepository.setValue(m.id(), fieldFuehrerschein.id(), "true");
            profileFieldRepository.setValue(
                    m.id(),
                    fieldFuehrerscheinAblauf.id(),
                    jsonStr(LocalDate.now().plusYears(rng.nextInt(5) + 1).toString()));
        }

        // Create Eltern (member managers -- GUARDIAN role, not MEMBER)
        boolean firstEltern = true;
        for (var u : elternData) {
            var m = createGuardian(
                    u.firstName(), u.lastName(), passwordHash, stationId, loginRole.id(), memberManagerRole.id());
            memberGroupRepository.addMember(groupEltern.id(), m.id());
            elternMembers.add(m);

            // Profile data -- skip Mobilnummer for first member manager (incomplete profile)
            if (!firstEltern) {
                profileFieldRepository.setValue(
                        m.id(), fieldTelefon.id(), jsonStr("0151 " + (10000000 + rng.nextInt(90000000))));
            }
            firstEltern = false;
            profileFieldRepository.setValue(
                    m.id(), fieldFestnetz.id(), jsonStr("0208 " + (1000000 + rng.nextInt(9000000))));
            profileFieldRepository.setValue(m.id(), fieldNewsletter.id(), Boolean.toString(rng.nextBoolean()));
        }

        // Create Anfaenger
        int personalNr = 100000 + rng.nextInt(900000);
        boolean firstAnfaenger = true;
        for (var u : anfaengerData) {
            var m = createUser(u.firstName(), u.lastName(), passwordHash, stationId, loginRole.id(), memberRole.id());
            memberGroupRepository.addMember(groupAnfaenger.id(), m.id());
            anfaengerMembers.add(m);

            // Personalnummer
            profileFieldRepository.setValue(m.id(), fieldPersonalnummer.id(), jsonStr(String.valueOf(personalNr++)));

            // Geburtstag -- skip first Anfaenger (incomplete profile)
            if (!firstAnfaenger) {
                profileFieldRepository.setValue(
                        m.id(),
                        fieldGeburtstag.id(),
                        jsonStr(LocalDate.now()
                                .minusYears(10 + rng.nextInt(6))
                                .minusDays(rng.nextInt(365))
                                .toString()));
            }
            firstAnfaenger = false;

            // Geschlecht
            profileFieldRepository.setValue(
                    m.id(), fieldGeschlecht.id(), jsonStr(rng.nextBoolean() ? "männlich" : "weiblich"));

            // Some have Jugendflamme 1
            if (rng.nextInt(3) == 0) {
                profileFieldRepository.setValue(m.id(), fieldJF1.id(), "true");
                profileFieldRepository.setValue(
                        m.id(),
                        fieldJF1Datum.id(),
                        jsonStr(LocalDate.now().minusMonths(rng.nextInt(12)).toString()));
            }
            if (rng.nextBoolean()) {
                profileFieldRepository.setValue(m.id(), fieldAllergien.id(), jsonStr(randomAllergy(rng)));
            }
        }

        // Create Fortgeschritten
        for (var u : fortgeschrittenData) {
            var m = createUser(u.firstName(), u.lastName(), passwordHash, stationId, loginRole.id(), memberRole.id());
            memberGroupRepository.addMember(groupFortgeschritten.id(), m.id());
            fortgeschrittenMembers.add(m);

            // Personalnummer
            profileFieldRepository.setValue(m.id(), fieldPersonalnummer.id(), jsonStr(String.valueOf(personalNr++)));

            // Geburtstag
            profileFieldRepository.setValue(
                    m.id(),
                    fieldGeburtstag.id(),
                    jsonStr(LocalDate.now()
                            .minusYears(12 + rng.nextInt(6))
                            .minusDays(rng.nextInt(365))
                            .toString()));

            // Geschlecht
            profileFieldRepository.setValue(
                    m.id(), fieldGeschlecht.id(), jsonStr(rng.nextBoolean() ? "männlich" : "weiblich"));

            // Most have JF1, some JF2, few JF3
            profileFieldRepository.setValue(m.id(), fieldJF1.id(), "true");
            profileFieldRepository.setValue(
                    m.id(),
                    fieldJF1Datum.id(),
                    jsonStr(LocalDate.now().minusMonths(rng.nextInt(24) + 6).toString()));
            if (rng.nextInt(3) != 0) {
                profileFieldRepository.setValue(m.id(), fieldJF2.id(), "true");
                profileFieldRepository.setValue(
                        m.id(),
                        fieldJF2Datum.id(),
                        jsonStr(LocalDate.now().minusMonths(rng.nextInt(12)).toString()));
            }
            if (rng.nextInt(5) == 0) {
                profileFieldRepository.setValue(m.id(), fieldJF3.id(), "true");
                profileFieldRepository.setValue(
                        m.id(),
                        fieldJF3Datum.id(),
                        jsonStr(LocalDate.now().minusMonths(rng.nextInt(6)).toString()));
                profileFieldRepository.setValue(m.id(), fieldLeistungsspange.id(), "true");
                profileFieldRepository.setValue(
                        m.id(),
                        fieldLeistungsspangeDatum.id(),
                        jsonStr(LocalDate.now().minusMonths(rng.nextInt(3)).toString()));
            }
            if (rng.nextBoolean()) {
                profileFieldRepository.setValue(m.id(), fieldAllergien.id(), jsonStr(randomAllergy(rng)));
            }
        }

        // -- Former members --
        var formerMember1 = createUser("Max", "Altmann", passwordHash, stationId, loginRole.id(), memberRole.id());
        memberGroupRepository.addMember(groupAnfaenger.id(), formerMember1.id());
        stationMemberRepository.setFormer(formerMember1.id(), true);

        var formerMember2 = createUser("Lisa", "Wegner", passwordHash, stationId, loginRole.id(), memberRole.id());
        memberGroupRepository.addMember(groupFortgeschritten.id(), formerMember2.id());
        stationMemberRepository.setFormer(formerMember2.id(), true);

        var formerMember3 = createTeamMember("Tom", "Richter", passwordHash, stationId, loginRole.id());
        stationMemberRepository.setFormer(formerMember3.id(), true);

        // -- Profile field changes (unacknowledged) --
        if (anfaengerMembers.size() >= 3) {
            // Simulate phone number changes
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
            // Simulate allergy field change
            profileFieldChangeRepository.create(
                    fieldAllergien.id(),
                    anfaengerMembers.get(2).id(),
                    "\"Keine\"",
                    "\"Nussallergie\"",
                    anfaengerMembers.get(2).id(),
                    true);
        }

        // -- Past profile field changes (acknowledged) --
        if (anfaengerMembers.size() >= 5 && !betreuerMembers.isEmpty()) {
            int bId = betreuerMembers.getFirst().id();
            // Phone number changes
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
            // Allergy changes
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
            // Birthday correction
            profileFieldChangeRepository.create(
                    fieldGeburtstag.id(),
                    anfaengerMembers.get(3).id(),
                    "\"2014-05-10\"",
                    "\"2014-05-11\"",
                    betreuerMembers.getFirst().id(),
                    true);
            // Non-acknowledged changes that don't require ack
            profileFieldChangeRepository.create(
                    fieldTelefon.id(),
                    fortgeschrittenMembers.getFirst().id(),
                    "\"0151 77766655\"",
                    "\"0176 88899900\"",
                    fortgeschrittenMembers.getFirst().id(),
                    false);
        }

        // -- Manager assignments: each Eltern manages their own kids (same last name) --
        var allKids = new ArrayList<>(anfaengerMembers);
        allKids.addAll(fortgeschrittenMembers);
        for (int fi = 0; fi < families.size(); fi++) {
            var elternMember = elternMembers.get(fi);
            for (int kidIndex : familyKidIndices.get(fi)) {
                if (kidIndex < allKids.size()) {
                    stationMemberRepository.addManager(
                            elternMember.id(), allKids.get(kidIndex).id());
                }
            }
        }

        // -- User Tags --
        var tagWettkampf = userTagRepository.create(stationId, "Wettkampfgruppe");
        var tagErsthelfer = userTagRepository.create(stationId, "Ersthelfer");
        // Add some Fortgeschritten to Wettkampfgruppe
        for (int i = 0; i < 6 && i < fortgeschrittenMembers.size(); i++) {
            userTagRepository.addMember(
                    tagWettkampf.id(), fortgeschrittenMembers.get(i).id());
        }
        // Add some Betreuer as Ersthelfer
        for (int i = 0; i < 3 && i < betreuerMembers.size(); i++) {
            userTagRepository.addMember(
                    tagErsthelfer.id(), betreuerMembers.get(i).id());
        }

        // JFW tag -- visible badge for all Betreuer (managers)
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
            String firstName, String lastName, String hash, int stationId, int loginRoleId, int memberRoleId) {
        String email = firstName.toLowerCase() + "@" + lastName.toLowerCase() + ".local";
        var account = accountRepository.create(email, firstName, lastName, true);
        accountRepository.createCredential(account.id(), hash);
        var member = stationMemberRepository.create(stationId, account.id());
        stationMemberRepository.setUserType(member.id(), StationUserType.MEMBER);
        stationMemberRepository.grantPermission(member.id(), loginRoleId);
        stationMemberRepository.grantPermission(member.id(), memberRoleId);
        return member;
    }

    private StationMember createTeamMember(
            String firstName, String lastName, String hash, int stationId, int loginRoleId) {
        String email = firstName.toLowerCase() + "@" + lastName.toLowerCase() + ".local";
        var account = accountRepository.create(email, firstName, lastName, true);
        accountRepository.createCredential(account.id(), hash);
        var member = stationMemberRepository.create(stationId, account.id());
        stationMemberRepository.setUserType(member.id(), StationUserType.TEAM);
        stationMemberRepository.grantPermission(member.id(), loginRoleId);
        return member;
    }

    private StationMember createGuardian(
            String firstName, String lastName, String hash, int stationId, int loginRoleId, int guardianRoleId) {
        String email = firstName.toLowerCase() + "@" + lastName.toLowerCase() + ".local";
        var account = accountRepository.create(email, firstName, lastName, true);
        accountRepository.createCredential(account.id(), hash);
        var member = stationMemberRepository.create(stationId, account.id());
        stationMemberRepository.setUserType(member.id(), StationUserType.GUARDIAN);
        stationMemberRepository.grantPermission(member.id(), loginRoleId);
        stationMemberRepository.grantPermission(member.id(), guardianRoleId);
        return member;
    }

    private String jsonStr(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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
}
