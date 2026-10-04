/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.PronounPreset;
import dev.chojo.ember.feature.members.entity.PronounSet;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.StringNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One gender field per station, its own or its association's, made new or turned from a choice field
 * with its answers kept.
 */
class GenderFieldsTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final Map<String, PronounSet> MALE = PronounPreset.MALE.byLanguage();
    private static final PronounSet SILENT = new PronounSet(" ", null, null, "");

    private static GenderFields genders;

    @BeforeAll
    static void setUp() {
        genders = new GenderFields(profileFieldCore, stationRepo);
    }

    private static ProfileFieldConfig gender(List<String> answers, Map<String, Map<String, PronounSet>> pronouns) {
        return new ProfileFieldConfig(
                null, false, false, answers, null, false, null, null, null, null, null, null, null, null, null,
                pronouns);
    }

    private static ProfileFieldConfig choice(List<String> answers) {
        return gender(answers, null);
    }

    private static Station station() {
        return stationRepo.create("Wache Geschlecht " + NAMES.incrementAndGet());
    }

    private static int memberAt(Station station) {
        int n = NAMES.incrementAndGet();
        var account = accountRepo.create("gender" + n + "@test.com", "Lena", "Gender" + n);
        return stationMemberRepo.create(station.id(), account.id()).id();
    }

    @Test
    void aStationAsksOneGenderAndASecondIsRefusedByName() {
        var station = station();
        var first = profileFieldService.create(
                station.id(),
                "Geschlecht",
                FieldType.GENDER,
                gender(List.of("männlich"), Map.of()),
                false,
                false,
                null);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> profileFieldService.create(
                        station.id(), "Anrede", FieldType.GENDER, choice(List.of("Herr")), false, false, null));

        assertEquals(MemberRefusal.PROFILE_GENDER_ALREADY_ASKED, refused.refusal());
        assertEquals(RefusalDetail.text("Geschlecht"), refused.detail());
        assertEquals(first.id(), genders.askedAt(station.id()).orElseThrow().id());
        assertTrue(
                profileFieldService
                        .update(
                                first.id(),
                                "Geschlecht",
                                FieldType.GENDER,
                                choice(List.of("weiblich")),
                                false,
                                false,
                                null,
                                false)
                        .isPresent(),
                "the one gender field may be changed itself");
    }

    @Test
    void aChoiceBecomesAGenderAndItsAnswersStay() {
        var station = station();
        int member = memberAt(station);
        var field = profileFieldService.create(
                station.id(), "Geschlecht", FieldType.CHOICE, choice(List.of("m", "w")), false, false, null);
        profileFieldRepo.setValue(member, field.id(), StringNode.valueOf("m"));

        profileFieldService.update(
                field.id(),
                "Geschlecht",
                FieldType.GENDER,
                gender(List.of("m", "w"), Map.of("m", MALE)),
                false,
                false,
                null,
                false);

        var asked = genders.askedAt(station.id()).orElseThrow();
        assertEquals(FieldOrigin.STATION, asked.origin());
        assertEquals("m", genders.answerOf(member, asked).orElseThrow());
        assertEquals(
                "er",
                asked.config()
                        .pronounsOf("m", DocumentLanguage.DE)
                        .orElseThrow()
                        .subjectWord());
        assertEquals(
                "him",
                asked.config()
                        .pronounsOf("m", DocumentLanguage.EN)
                        .orElseThrow()
                        .dativeWord());
        assertTrue(
                asked.config().pronounsOf("w", DocumentLanguage.DE).isEmpty(),
                "an answer without pronouns uses the name");
    }

    @Test
    void pronounsAreKeptOnlyForOfferedAnswersThatNameAWord() {
        var station = station();
        var field = profileFieldService.create(
                station.id(),
                "Geschlecht",
                FieldType.GENDER,
                gender(
                        List.of("m", "w", "d"),
                        Map.of(
                                "m",
                                Map.of("de", MALE.get("de"), "en", SILENT),
                                "w",
                                PronounPreset.FEMALE.byLanguage(),
                                "d",
                                Map.of("de", SILENT),
                                "alt",
                                MALE)),
                false,
                false,
                null);

        profileFieldService.update(
                field.id(),
                "Geschlecht",
                FieldType.GENDER,
                gender(List.of("m", "d"), field.config().pronouns()),
                false,
                false,
                null,
                false);

        var kept = genders.askedAt(station.id()).orElseThrow().config().pronouns();
        assertEquals(Map.of("m", Map.of("de", MALE.get("de"))), kept);
    }

    @Test
    void onlyAChoiceTurnsIntoAGender() {
        var station = station();
        var text = profileFieldService.create(
                station.id(), "Freitext", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> profileFieldService.update(
                        text.id(), "Freitext", FieldType.GENDER, choice(List.of()), false, false, null, false));

        assertEquals(MemberRefusal.PROFILE_GENDER_NOT_FROM_CHOICE, refused.refusal());
    }

    @Test
    void aPronounLongerThanAPronounIsRefused() {
        var station = station();
        var tooLong = Map.of("x", Map.of("de", new PronounSet("x".repeat(PronounSet.MAX_WORD + 1), null, null, null)));

        var refused = assertThrows(
                RefusalResponse.class,
                () -> profileFieldService.create(
                        station.id(),
                        "Geschlecht",
                        FieldType.GENDER,
                        gender(List.of("x"), tooLong),
                        false,
                        false,
                        null));

        assertEquals(MemberRefusal.PROFILE_PRONOUN_TOO_LONG, refused.refusal());
    }

    @Test
    void anAssociationsGenderIsTheStationsAndNeitherSideAsksASecond() {
        int clusterId = clusterService
                .create("Kreisverband Geschlecht " + NAMES.incrementAndGet(), null)
                .id();
        var station = clusterService.createStation(clusterId, "Wache Verband " + NAMES.incrementAndGet());
        var field = clusterProfileFieldService.create(
                clusterId,
                "Geschlecht",
                FieldType.GENDER,
                gender(List.of("weiblich"), Map.of()),
                false,
                false,
                null,
                true,
                false,
                null);
        clusterProfileFieldService.assignToRole(clusterId, field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);

        var asked = genders.askedAt(station.id()).orElseThrow();
        assertEquals(FieldOrigin.CLUSTER, asked.origin());
        var stationRefused = assertThrows(
                RefusalResponse.class,
                () -> profileFieldService.create(
                        station.id(), "Anrede", FieldType.GENDER, choice(List.of()), false, false, null));
        assertEquals(MemberRefusal.PROFILE_GENDER_ALREADY_ASKED, stationRefused.refusal());

        int otherCluster = clusterService
                .create("Kreisverband Eigenes " + NAMES.incrementAndGet(), null)
                .id();
        var ownStation = clusterService.createStation(otherCluster, "Wache Eigenes " + NAMES.incrementAndGet());
        profileFieldService.create(
                ownStation.id(), "Geschlecht", FieldType.GENDER, choice(List.of()), false, false, null);
        var associationRefused = assertThrows(
                RefusalResponse.class,
                () -> clusterProfileFieldService.create(
                        otherCluster,
                        "Geschlecht",
                        FieldType.GENDER,
                        choice(List.of()),
                        false,
                        false,
                        null,
                        true,
                        false,
                        null));
        assertEquals(ClusterRefusal.CLUSTER_PROFILE_GENDER_ALREADY_ASKED, associationRefused.refusal());
    }
}
