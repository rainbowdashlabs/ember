/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.MovableClock;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.generator.entity.DataSubject;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.PronounForm;
import dev.chojo.ember.feature.generator.entity.PronounSource;
import dev.chojo.ember.feature.generator.entity.SubjectRole;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.StringNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every key of the catalogue filled in for one member: official names, guardians in the order the
 * member page sets, profile answers as exports print them, pronouns by the template's mapping, and what
 * is missing.
 */
class PlaceholderResolverTest extends RepositoryTestBase {
    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    private static PlaceholderResolver resolver;
    private static Station station;
    private static StationMember lena;
    private static StationMember max;
    private static StationMember anna;
    private static StationMember bernd;
    private static StationMember manager;
    private static int genderField;
    private static int phoneField;
    private static int consentField;

    @BeforeAll
    static void setup() {
        resolver = new PlaceholderResolver(
                stationRepo, stationMemberRepo, memberNameResolver, profileFieldRepo, new MovableClock(NOW));
        station = stationRepo.create("Resolver Wache");
        stationRepo.updateLocation(station.id(), "Dönhoffstr. 31", "10318", "Berlin", "DE", null, null);
        stationRepo.updateNicknamesEnabled(station.id(), true);

        lena = member("lena.resolver@test.com", "Magdalena", "Schmidt");
        stationMemberRepo.setNickname(lena.id(), "Lena", lena.id());
        stationMemberRepo.setJoinDate(lena.id(), LocalDate.of(2024, 10, 1));
        max = member("max.resolver@test.com", "Max", "Weiß");
        anna = member("anna.resolver@test.com", "Anna", "Schmidt");
        bernd = member("bernd.resolver@test.com", "Bernd", "Schmidt");
        manager = member("nora.resolver@test.com", "Nora", "Fülling");
        stationMemberRepo.addManager(bernd.id(), lena.id());
        stationMemberRepo.addManager(anna.id(), lena.id());

        int birthField = field("Geburtsdatum", FieldType.BIRTH_DATE, null);
        genderField = field("Geschlecht", FieldType.CHOICE, List.of("männlich", "weiblich", "divers"));
        phoneField = field("Telefon", FieldType.TEXT, null);
        consentField = field("Fotos erlaubt", FieldType.BOOLEAN, null);
        profileFieldRepo.setValue(lena.id(), birthField, StringNode.valueOf("2012-05-17"));
        profileFieldRepo.setValue(lena.id(), genderField, StringNode.valueOf("weiblich"));
        profileFieldRepo.setValue(lena.id(), consentField, BooleanNode.TRUE);
        profileFieldRepo.setValue(anna.id(), phoneField, StringNode.valueOf("030 1234"));
        profileFieldRepo.setValue(max.id(), genderField, StringNode.valueOf("männlich"));
    }

    private static StationMember member(String email, String first, String last) {
        var account = accountRepo.create(email, first, last);
        return stationMemberRepo.create(station.id(), account.id());
    }

    private static int field(String name, FieldType type, List<String> options) {
        var config = new ProfileFieldConfig(
                null, false, false, options, null, false, null, null, null, null, null, null, null, null, null, null);
        return profileFieldRepo
                .create(station.id(), name, type, config, false, false, null)
                .id();
    }

    private static Map<String, String> values(StationMember member, PronounSource pronouns, String... keys) {
        return resolver.resolve(
                        station.id(),
                        member.id(),
                        new LinkedHashSet<>(List.of(keys)),
                        pronouns,
                        GenerationContext.by(manager.id()))
                .values();
    }

    @Test
    void namesAreOfficialAndOnlyTheCalledNameReadsTheNickname() {
        var values = values(lena, null, "member.firstName", "member.lastName", "member.fullName", "member.calledName");

        assertEquals("Magdalena", values.get("member.firstName"));
        assertEquals("Schmidt", values.get("member.lastName"));
        assertEquals("Magdalena Schmidt", values.get("member.fullName"));
        assertEquals("Lena Schmidt", values.get("member.calledName"));
    }

    @Test
    void datesAgeAndMembershipReadAsAPersonWouldWriteThem() {
        var values = values(
                lena,
                null,
                "member.birthDate",
                "member.age",
                "member.joinDate",
                "member.joinDate.monthYear",
                "member.userType",
                "today",
                "today.long",
                "generatedBy.fullName");

        assertEquals("17.05.2012", values.get("member.birthDate"));
        assertEquals("14", values.get("member.age"));
        assertEquals("01.10.2024", values.get("member.joinDate"));
        assertEquals("Oktober 2024", values.get("member.joinDate.monthYear"));
        assertEquals("Mitglied", values.get("member.userType"));
        assertEquals("02.10.2026", values.get("today"));
        assertEquals("2. Oktober 2026", values.get("today.long"));
        assertEquals("Nora Fülling", values.get("generatedBy.fullName"));
    }

    @Test
    void profileAnswersAreWrittenAsExportsWriteThem() {
        var values = values(lena, null, "profile." + consentField, "guardian2.profile." + phoneField);

        assertEquals("Ja", values.get("profile." + consentField));
        assertEquals("030 1234", values.get("guardian2.profile." + phoneField));
    }

    @Test
    void theStationIsTheStationThatFilesTheDocument() {
        var values = values(lena, null, "station.name", "station.address", "station.postalCode", "station.city");

        assertEquals(
                Map.of(
                        "station.name", "Resolver Wache",
                        "station.address", "Dönhoffstr. 31",
                        "station.postalCode", "10318",
                        "station.city", "Berlin"),
                values);
    }

    /** The guardian linked first is first, until the order is changed on the member page. */
    @Test
    void guardiansFollowTheirOrder() {
        var values = values(lena, null, "guardian1.fullName", "guardian2.firstName", "guardian2.lastName");

        assertEquals("Bernd Schmidt", values.get("guardian1.fullName"));
        assertEquals("Anna", values.get("guardian2.firstName"));
        assertEquals("Schmidt", values.get("guardian2.lastName"));
    }

    @Test
    void pronounsFollowTheMappingAndFallBackToTheName() {
        var mapping = new PronounSource(
                genderField, Map.of("männlich", PronounForm.ER, "weiblich", PronounForm.SIE), PronounForm.NAME);

        var her = values(lena, mapping, "pronoun.subject", "pronoun.dative", "pronoun.subject.start");
        var him = values(max, mapping, "pronoun.object", "pronoun.possessive", "pronoun.possessive.start");
        var named = values(anna, mapping, "pronoun.subject", "pronoun.possessive");
        var noSource = values(max, null, "pronoun.possessive");

        assertEquals(Map.of("pronoun.subject", "sie", "pronoun.dative", "ihr", "pronoun.subject.start", "Sie"), her);
        assertEquals(
                Map.of("pronoun.object", "ihn", "pronoun.possessive", "sein", "pronoun.possessive.start", "Sein"), him);
        assertEquals(Map.of("pronoun.subject", "Anna", "pronoun.possessive", "Annas"), named);
        assertEquals("Max'", noSource.get("pronoun.possessive"));
    }

    @Test
    void whatIsNotKnownIsReportedMissingInTemplateOrder() {
        var resolved = resolver.resolve(
                station.id(),
                max.id(),
                new LinkedHashSet<>(
                        List.of("member.firstName", "member.birthDate", "guardian1.fullName", "event.name")),
                null,
                new GenerationContext(null, null));

        assertEquals(List.of("member.birthDate", "guardian1.fullName", "event.name"), resolved.missing());
        assertFalse(resolved.complete());
        assertFalse(resolved.values().containsKey("generatedBy.fullName"));
    }

    @Test
    void anAppointmentFillsItsKeysWhereADocumentIsGeneratedForOne() {
        var event = new GenerationContext.EventFacts(
                "Berlin Marathon", Instant.parse("2026-09-27T07:00:00Z"), Instant.parse("2026-09-27T14:00:00Z"), null);

        var resolved = resolver.resolve(
                station.id(),
                lena.id(),
                Set.of("event.name", "event.start", "event.location"),
                null,
                new GenerationContext(manager.id(), event));

        assertEquals("Berlin Marathon", resolved.values().get("event.name"));
        assertEquals("27.09.2026 09:00", resolved.values().get("event.start"));
        assertEquals(List.of("event.location"), resolved.missing());
    }

    /** A guardian is among the people a document is about only where the template reads their data. */
    @Test
    void theDataSubjectsAreTheMemberAndTheGuardiansNamed() {
        var withGuardian = resolver.resolve(
                station.id(),
                lena.id(),
                Set.of("member.fullName", "guardian2.fullName"),
                null,
                GenerationContext.by(manager.id()));
        var withoutGuardian = resolver.resolve(
                station.id(), lena.id(), Set.of("member.fullName"), null, GenerationContext.by(manager.id()));

        assertEquals(
                List.of(
                        new DataSubject(lena.id(), SubjectRole.MEMBER),
                        new DataSubject(anna.id(), SubjectRole.GUARDIAN)),
                withGuardian.subjects());
        assertEquals(List.of(new DataSubject(lena.id(), SubjectRole.MEMBER)), withoutGuardian.subjects());
    }

    @Test
    void aPreviewWithoutAMemberKnowsTheStationAndTheDay() {
        var values = resolver.withoutMember(station.id());

        assertEquals("Resolver Wache", values.get("station.name"));
        assertEquals("02.10.2026", values.get("today"));
        assertTrue(values.keySet().stream().noneMatch(key -> key.startsWith("member.")));
    }

    @Test
    void theUserTypeIsWrittenInTheStationsWords() {
        stationMemberRepo.setUserType(max.id(), StationUserType.TRIAL);

        assertEquals("Probe", values(max, null, "member.userType").get("member.userType"));
        stationMemberRepo.setUserType(max.id(), StationUserType.MEMBER);
    }
}
