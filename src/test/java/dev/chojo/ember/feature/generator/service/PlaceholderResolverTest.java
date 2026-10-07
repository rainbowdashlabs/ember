/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.MovableClock;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.generator.entity.DataSubject;
import dev.chojo.ember.feature.generator.entity.DateFormat;
import dev.chojo.ember.feature.generator.entity.DatePreset;
import dev.chojo.ember.feature.generator.entity.DocumentIssuer;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.SubjectRole;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.PronounSet;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.StringNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every key of the catalogue filled in for one member: official names, guardians in the order the
 * member page sets, profile answers as exports print them, pronouns from the member's answer to the
 * gender field in the template's language, and what is missing.
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
    private static int birthField;
    private static int phoneField;
    private static int consentField;

    @BeforeAll
    static void setup() {
        resolver = newPlaceholderResolver(new MovableClock(NOW));
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

        birthField = field("Geburtsdatum", FieldType.BIRTH_DATE, null, null);
        int genderField = field(
                "Geschlecht",
                FieldType.GENDER,
                List.of("männlich", "weiblich", "divers"),
                Map.of(
                        "männlich",
                        Map.of(
                                "de",
                                new PronounSet("er", "ihn", "ihm", "sein"),
                                "en",
                                new PronounSet("he", "him", null, "his")),
                        "weiblich",
                        Map.of("de", new PronounSet("sie", "sie", "ihr", "ihr"))));
        phoneField = field("Telefon", FieldType.TEXT, null, null);
        consentField = field("Fotos erlaubt", FieldType.BOOLEAN, null, null);
        profileFieldRepo.setValue(lena.id(), birthField, StringNode.valueOf("2012-05-17"));
        profileFieldRepo.setValue(lena.id(), genderField, StringNode.valueOf("weiblich"));
        profileFieldRepo.setValue(lena.id(), consentField, BooleanNode.TRUE);
        profileFieldRepo.setValue(anna.id(), phoneField, StringNode.valueOf("030 1234"));
        profileFieldRepo.setValue(anna.id(), genderField, StringNode.valueOf("divers"));
        profileFieldRepo.setValue(max.id(), genderField, StringNode.valueOf("männlich"));
    }

    private static StationMember member(String email, String first, String last) {
        var account = accountRepo.create(email, first, last);
        return stationMemberRepo.create(station.id(), account.id());
    }

    private static int field(
            String name,
            FieldType type,
            @Nullable List<String> options,
            @Nullable Map<String, Map<String, PronounSet>> pronouns) {
        var config = new ProfileFieldConfig(
                null, false, false, options, null, false, null, null, null, null, null, null, null, null, null,
                pronouns);
        return profileFieldRepo
                .create(station.id(), name, type, config, false, false, null)
                .id();
    }

    private static Map<String, String> values(StationMember member, DocumentLanguage language, String... keys) {
        return resolver.resolve(
                        station.id(),
                        member.id(),
                        new LinkedHashSet<>(List.of(keys)),
                        language,
                        GenerationContext.by(manager.id(), DocumentIssuer.NONE))
                .values();
    }

    private static Map<String, String> values(StationMember member, String... keys) {
        return values(member, DocumentLanguage.DE, keys);
    }

    @Test
    void namesAreOfficialAndOnlyTheCalledNameReadsTheNickname() {
        var values = values(lena, "member.firstName", "member.lastName", "member.fullName", "member.calledName");

        assertEquals("Magdalena", values.get("member.firstName"));
        assertEquals("Schmidt", values.get("member.lastName"));
        assertEquals("Magdalena Schmidt", values.get("member.fullName"));
        assertEquals("Lena Schmidt", values.get("member.calledName"));
    }

    @Test
    void datesAgeAndMembershipReadAsAPersonWouldWriteThem() {
        var values = values(
                lena,
                "member.birthDate",
                "member.age",
                "member.joinDate",
                "member.userType",
                "today",
                "generatedBy.fullName");

        assertEquals("17.05.2012", values.get("member.birthDate"));
        assertEquals("14", values.get("member.age"));
        assertEquals("01.10.2024", values.get("member.joinDate"));
        assertEquals("Mitglied", values.get("member.userType"));
        assertEquals("02.10.2026", values.get("today"));
        assertEquals("Nora Fülling", values.get("generatedBy.fullName"));
    }

    /** A ready-made format prints the date in the template's language. */
    @Test
    void aFormatPrintsInTheTemplatesLanguage() {
        var german = values(lena, "today|long", "member.joinDate|monthYear");
        var english = values(lena, DocumentLanguage.EN, "today|long", "member.joinDate|monthYear");

        assertEquals("2. Oktober 2026", german.get("today|long"));
        assertEquals("Oktober 2024", german.get("member.joinDate|monthYear"));
        assertEquals("October 2, 2026", english.get("today|long"));
        assertEquals("October 2024", english.get("member.joinDate|monthYear"));
    }

    @Test
    void everyReadyMadeFormatPrintsInTheTemplatesLanguage() {
        var keys = Arrays.stream(DatePreset.values())
                .filter(preset -> !DateFormat.of(preset).readsClock())
                .map(preset -> "today|" + preset.written())
                .toArray(String[]::new);

        assertEquals(
                List.of(
                        "02.10.2026",
                        "2. Oktober 2026",
                        "2. Okt. 2026",
                        "Oktober 2026",
                        "2026",
                        "Freitag, 2. Oktober 2026"),
                List.copyOf(values(lena, keys).values()));
        assertEquals(
                List.of(
                        "02.10.2026",
                        "October 2, 2026",
                        "Oct 2, 2026",
                        "October 2026",
                        "2026",
                        "Friday, October 2, 2026"),
                List.copyOf(values(lena, DocumentLanguage.EN, keys).values()));
    }

    @Test
    void anOwnFormatPrintsItsTokensWithWordsInTheTemplatesLanguage() {
        String key = "member.birthDate|TTT, T.M.JJ / TT.MM.JJJJ - MMM MMMM";

        assertEquals("Do., 17.5.12 / 17.05.2012 - Mai Mai", values(lena, key).get(key));
        assertEquals(
                "Thu, 17.5.12 / 17.05.2012 - May May",
                values(lena, DocumentLanguage.EN, key).get(key));
    }

    @Test
    void anAnswerOfADateTypeTakesAFormatAndOtherValuesDoNot() {
        String birth = "profile." + birthField + "|long";
        String name = "member.firstName|long";

        var resolved = resolver.resolve(
                station.id(),
                lena.id(),
                new LinkedHashSet<>(List.of(birth, name, "member.birthDate|hh:mm")),
                DocumentLanguage.DE,
                GenerationContext.by(manager.id(), DocumentIssuer.NONE));

        assertEquals("17. Mai 2012", resolved.values().get(birth));
        assertEquals(List.of(name, "member.birthDate|hh:mm"), resolved.missing());
    }

    @Test
    void theTimesOfAnAppointmentTakeTheFormatsWithATimeOfDay() {
        var event = new GenerationContext.EventFacts(
                "Berlin Marathon", Instant.parse("2026-09-27T07:00:00Z"), Instant.parse("2026-09-27T14:00:00Z"), null);

        var values = resolver.resolve(
                        station.id(),
                        lena.id(),
                        new LinkedHashSet<>(List.of("event.start|time", "event.end|weekday", "event.end|T.M. h:mm")),
                        DocumentLanguage.DE,
                        new GenerationContext(manager.id(), event, DocumentIssuer.NONE))
                .values();

        assertEquals("09:00", values.get("event.start|time"));
        assertEquals("Sonntag, 27. September 2026", values.get("event.end|weekday"));
        assertEquals("27.9. 16:00", values.get("event.end|T.M. h:mm"));
    }

    @Test
    void profileAnswersAreWrittenAsExportsWriteThem() {
        var values = values(lena, "profile." + consentField, "guardian2.profile." + phoneField);

        assertEquals("Ja", values.get("profile." + consentField));
        assertEquals("030 1234", values.get("guardian2.profile." + phoneField));
    }

    @Test
    void theStationIsTheStationThatFilesTheDocument() {
        var values = values(lena, "station.name", "station.address", "station.postalCode", "station.city");

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
        var values = values(lena, "guardian1.fullName", "guardian2.firstName", "guardian2.lastName");

        assertEquals("Bernd Schmidt", values.get("guardian1.fullName"));
        assertEquals("Anna", values.get("guardian2.firstName"));
        assertEquals("Schmidt", values.get("guardian2.lastName"));
    }

    @Test
    void pronounsFollowTheGenderAnswerWithTheirEndingAndFallBackToTheName() {
        var her = values(lena, "pronoun.subject", "pronoun.dative", "pronoun.subject.start", "pronoun.possessive.e");
        var him = values(max, "pronoun.object", "pronoun.possessive.en", "pronoun.possessive.start");
        var named = values(anna, "pronoun.subject", "pronoun.possessive.en");
        var noAnswer = values(bernd, "pronoun.possessive");

        assertEquals(
                Map.of(
                        "pronoun.subject", "sie",
                        "pronoun.dative", "ihr",
                        "pronoun.subject.start", "Sie",
                        "pronoun.possessive.e", "ihre"),
                her);
        assertEquals(
                Map.of("pronoun.object", "ihn", "pronoun.possessive.en", "seinen", "pronoun.possessive.start", "Sein"),
                him);
        assertEquals(Map.of("pronoun.subject", "Anna", "pronoun.possessive.en", "Annas"), named);
        assertEquals("Bernds", noAnswer.get("pronoun.possessive"));
    }

    @Test
    void theTemplatesLanguagePicksThePronounsAndALanguageWithoutThemUsesTheName() {
        var him = values(max, DocumentLanguage.EN, "pronoun.subject.start", "pronoun.dative", "pronoun.possessive");
        var her = values(lena, DocumentLanguage.EN, "pronoun.subject", "pronoun.possessive");

        assertEquals(Map.of("pronoun.subject.start", "He", "pronoun.dative", "him", "pronoun.possessive", "his"), him);
        assertEquals(Map.of("pronoun.subject", "Magdalena", "pronoun.possessive", "Magdalena's"), her);
    }

    @Test
    void whatIsNotKnownIsReportedMissingInTemplateOrder() {
        var resolved = resolver.resolve(
                station.id(),
                max.id(),
                new LinkedHashSet<>(
                        List.of("member.firstName", "member.birthDate", "guardian1.fullName", "event.name")),
                DocumentLanguage.DE,
                GenerationContext.NOBODY);

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
                DocumentLanguage.DE,
                new GenerationContext(manager.id(), event, DocumentIssuer.NONE));

        assertEquals("Berlin Marathon", resolved.values().get("event.name"));
        assertEquals("27.09.2026 09:00", resolved.values().get("event.start"));
        assertEquals(List.of("event.location"), resolved.missing());
    }

    @Test
    void theIssuerIsNamedOfficiallyWithTheirFunction() {
        var issuer = member("warden.resolver@test.com", "Erika", "Wehr");
        stationMemberRepo.setNickname(issuer.id(), "Eri", issuer.id());

        var resolved = resolver.resolve(
                station.id(),
                lena.id(),
                Set.of("issuer.fullName", "issuer.function"),
                DocumentLanguage.DE,
                GenerationContext.by(manager.id(), DocumentIssuer.ofTemplate(issuer.id(), "Jugendwartin")));

        assertEquals("Erika Wehr", resolved.values().get("issuer.fullName"));
        assertEquals("Jugendwartin", resolved.values().get("issuer.function"));
        assertTrue(resolved.complete());
    }

    /** Nobody named, a member who left and a member of another station all leave the issuer missing. */
    @Test
    void anIssuerWhoIsNoCurrentMemberIsMissing() {
        var left = member("left.resolver@test.com", "Gerd", "Gegangen");
        stationMemberRepo.setFormer(left.id(), true);
        var elsewhere = stationMemberRepo.create(
                stationRepo.create("Andere Resolver Wache").id(),
                accountRepo
                        .create("elsewhere.resolver@test.com", "Fred", "Fremd")
                        .id());
        var keys = Set.of("issuer.fullName", "issuer.function");

        for (var issuer : List.of(
                DocumentIssuer.NONE,
                DocumentIssuer.ofTemplate(left.id(), null),
                DocumentIssuer.picked(elsewhere.id(), null))) {
            var resolved = resolver.resolve(
                    station.id(), lena.id(), keys, DocumentLanguage.DE, GenerationContext.by(manager.id(), issuer));
            assertTrue(resolved.missing().contains("issuer.fullName"), issuer.toString());
            assertTrue(resolved.missing().contains("issuer.function"), issuer.toString());
        }
    }

    /** A guardian is among the people a document is about only where the template reads their data. */
    @Test
    void theDataSubjectsAreTheMemberAndTheGuardiansNamed() {
        var withGuardian = resolver.resolve(
                station.id(),
                lena.id(),
                Set.of("member.fullName", "guardian2.fullName"),
                DocumentLanguage.DE,
                GenerationContext.by(manager.id(), DocumentIssuer.NONE));
        var withoutGuardian = resolver.resolve(
                station.id(),
                lena.id(),
                Set.of("member.fullName"),
                DocumentLanguage.DE,
                GenerationContext.by(manager.id(), DocumentIssuer.NONE));

        assertEquals(
                List.of(
                        new DataSubject(lena.id(), SubjectRole.MEMBER),
                        new DataSubject(anna.id(), SubjectRole.GUARDIAN)),
                withGuardian.subjects());
        assertEquals(List.of(new DataSubject(lena.id(), SubjectRole.MEMBER)), withoutGuardian.subjects());
    }

    @Test
    void aPreviewWithoutAMemberKnowsTheStationAndTheDay() {
        var values = resolver.withoutMember(
                new Owner.Station(station.id()),
                DocumentLanguage.DE,
                Set.of("station.name", "today", "today|monthYear", "member.fullName", "member.birthDate|long"));

        assertEquals(
                Map.of("station.name", "Resolver Wache", "today", "02.10.2026", "today|monthYear", "Oktober 2026"),
                values);
    }

    @Test
    void theUserTypeIsWrittenInTheStationsWords() {
        stationMemberRepo.setUserType(max.id(), StationUserType.TRIAL);

        assertEquals("Probe", values(max, "member.userType").get("member.userType"));
        stationMemberRepo.setUserType(max.id(), StationUserType.MEMBER);
    }
}
