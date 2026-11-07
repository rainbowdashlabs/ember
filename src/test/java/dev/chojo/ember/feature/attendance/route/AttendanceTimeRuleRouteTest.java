/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.AttendanceRefusal;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.repository.AttendanceTimeRuleRepository;
import dev.chojo.ember.feature.attendance.service.AttendanceAudienceService;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.attendance.service.AttendanceTemplateGuards;
import dev.chojo.ember.feature.attendance.service.AttendanceTimeRuleService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A template's preparation and follow-up rules over HTTP: who may read and write them, and which lists
 * are refused before anything is stored.
 */
class AttendanceTimeRuleRouteTest extends RepositoryTestBase {
    private static final AttendanceTimeRuleRepository timeRules = new AttendanceTimeRuleRepository();

    private static AttendanceService attendanceService;
    private static RouteHarness harness;
    private static Station station;
    private static Station otherStation;
    private static Account account;
    private static StationMember member;
    private static MemberGroup leaders;

    private int templateId;

    @BeforeAll
    static void setupClass() {
        attendanceService = new AttendanceService(
                attendanceRepo,
                eventRepo,
                eventFieldRepo,
                eventFieldDefaultRepo,
                eventRegistrationRepo,
                stationMemberRepo,
                memberGroupRepo,
                new Attendance(),
                stationRepo,
                eventDateCancellationRepo,
                new AttendanceAudienceService(attendanceRepo),
                memberEligibility,
                new AttendanceTemplateGuards(attendanceRepo),
                new AttendanceTimeRuleRepository());
        harness = RouteHarness.serving(new AttendanceTimeRuleRoutes(
                        attendanceService,
                        new AttendanceTimeRuleService(
                                timeRules, privateTags, attendanceService, attendanceRepo, restrictionService)))
                .withStations(stationRepo);
        station = stationRepo.create("TimeRuleRouteStation");
        otherStation = stationRepo.create("TimeRuleRouteOther");
        account = accountRepo.create("time-rule-route@test.com", "Tim", "Regel");
        member = stationMemberRepo.create(station.id(), account.id());
        leaders = memberGroupRepo.create(station.id(), "Jugendwarte");
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        accountRepo.delete(account.id());
    }

    @BeforeEach
    void createTemplate() {
        templateId =
                attendanceService.createTemplate(station.id(), "Regel Vorlage").id();
    }

    @AfterEach
    void archiveTemplate() {
        attendanceService.archiveTemplate(templateId);
    }

    @Test
    void aConfiguratorWritesTheRulesAndAReaderReadsThem() {
        harness.run((server, client) -> {
            var written = client.put(
                    rulesOf(templateId),
                    body(rules(rule("Jugendwart", 30, 15, groupAudience(leaders.id())))),
                    harness.as(sessionAt(station, StationPermission.ATTENDANCE_CONFIGURE)));
            assertEquals(200, written.code());
            var first = json(written).get(0);
            assertEquals("Jugendwart", first.get("label").asString());
            assertEquals(30, first.get("preparationMinutes").asInt());
            assertEquals(15, first.get("followUpMinutes").asInt());
            assertTrue(first.get("intact").asBoolean());
            assertEquals(
                    leaders.id(), first.get("audience").get("groupIds").get(0).asInt());

            var read =
                    client.get(rulesOf(templateId), harness.as(sessionAt(station, StationPermission.ATTENDANCE_READ)));
            assertEquals(200, read.code());
            assertEquals(1, json(read).size());
        });
    }

    @Test
    void takingAttendanceIsNotConfiguringTemplates() {
        var answer = harness.request(client -> client.put(
                rulesOf(templateId),
                body(rules(rule("Jugendwart", 30, 0, groupAudience(leaders.id())))),
                harness.as(sessionAt(station, StationPermission.ATTENDANCE_EDIT))));

        assertEquals(403, answer.code());
        assertTrue(timeRules.findTemplateRules(templateId).isEmpty());
    }

    @Test
    void anotherStationsTemplateIsNotThere() {
        var answer = harness.request(client -> client.put(
                rulesOf(templateId),
                body(rules(rule("Jugendwart", 30, 0, groupAudience(leaders.id())))),
                harness.as(sessionAt(otherStation, StationPermission.ATTENDANCE_CONFIGURE))));

        assertEquals(GeneralRefusal.NOT_HERE_OR_NOT_YOURS, refusalOf(answer));
        assertTrue(timeRules.findTemplateRules(templateId).isEmpty());
    }

    @Test
    void aRuleNamingNobodyIsRefused() {
        var answer = configure(rules(rule("Alle", 10, 0, audience("[]", "[]", "[]", "AND"))));

        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_NOBODY, refusalOf(answer));
    }

    @Test
    void minutesBeyondADayAreRefused() {
        var tooMuch = configure(rules(rule("Lang", 1441, 0, groupAudience(leaders.id()))));
        var negative = configure(rules(rule("Minus", 0, -5, groupAudience(leaders.id()))));

        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_MINUTES_OUT_OF_RANGE, refusalOf(tooMuch));
        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_MINUTES_OUT_OF_RANGE, refusalOf(negative));
        assertTrue(timeRules.findTemplateRules(templateId).isEmpty());
    }

    @Test
    void aPrivateTagCannotChoosePeople() {
        UserTag hidden = userTagRepo.create(station.id(), "Verdeckt", null, TagVisibility.PRIVATE);

        var answer = configure(rules(rule("Geheim", 10, 0, audience("[]", "[]", "[" + hidden.id() + "]", "AND"))));

        assertNotNull(refusalOf(answer));
        assertTrue(timeRules.findTemplateRules(templateId).isEmpty());
    }

    @Test
    void aRuleThatLostATagReadsBackNamingNobodyUntilItIsNamedAnew() {
        UserTag trainers = userTagRepo.create(station.id(), "Ausbilder Kurz");
        configure(rules(rule("Ausbilder", 30, 0, audience("[\"MEMBER\"]", "[]", "[" + trainers.id() + "]", "AND"))));
        userTagRepo.delete(trainers.id());

        var read = harness.request(client ->
                client.get(rulesOf(templateId), harness.as(sessionAt(station, StationPermission.ATTENDANCE_READ))));
        var broken = json(read).get(0);
        assertFalse(broken.get("intact").asBoolean());
        assertEquals(0, broken.get("audience").get("userTypes").size(), "what is left of it is not handed out");

        var asRead = configure(rules(rule("Ausbilder", 30, 0, audience("[]", "[]", "[]", "AND"))));
        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_NOBODY, refusalOf(asRead));
        var stale = configure(
                rules(rule("Ausbilder", 30, 0, audience("[\"MEMBER\"]", "[]", "[" + trainers.id() + "]", "AND"))));
        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_UNKNOWN, refusalOf(stale));

        var namedAnew = configure(rules(rule("Ausbilder", 30, 0, groupAudience(leaders.id()))));
        assertEquals(200, namedAnew.code());
        assertTrue(timeRules.findTemplateRules(templateId).getFirst().intact());
    }

    @Test
    void anotherStationsGroupOrAnEmptyEntryIsRefused() {
        MemberGroup theirs = memberGroupRepo.create(otherStation.id(), "Fremde");

        var foreign = configure(rules(rule("Fremd", 10, 0, groupAudience(theirs.id()))));
        var emptyEntry = configure(rules(rule("Leer", 10, 0, audience("[]", "[null]", "[]", "AND"))));
        var unknownMember = configure(rules(rule(
                "Wer",
                10,
                0,
                "{\"userTypes\":[],\"groupIds\":[],\"tagIds\":[],\"memberIds\":[999999],\"mode\":\"AND\"}")));

        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_UNKNOWN, refusalOf(foreign));
        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_UNKNOWN, refusalOf(emptyEntry));
        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_UNKNOWN, refusalOf(unknownMember));
        assertTrue(timeRules.findTemplateRules(templateId).isEmpty());
    }

    @Test
    void aMissingRuleOrAudienceNamesNobody() {
        var nullEntry = configure("{\"rules\":[null]}");
        var noAudience = configure("{\"rules\":[{\"label\":\"Ohne\",\"preparationMinutes\":5,\"followUpMinutes\":0}]}");

        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_NOBODY, refusalOf(nullEntry));
        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_NOBODY, refusalOf(noAudience));
    }

    @Test
    void aDeletedTemplateTakesNoRules() {
        int archived =
                attendanceService.createTemplate(station.id(), "Geloescht").id();
        attendanceService.archiveTemplate(archived);

        var answer = harness.request(client -> client.put(
                rulesOf(archived),
                body(rules(rule("Spaet", 10, 0, groupAudience(leaders.id())))),
                harness.as(sessionAt(station, StationPermission.ATTENDANCE_CONFIGURE))));

        assertEquals(GeneralRefusal.NOT_HERE_OR_NOT_YOURS, refusalOf(answer));
        assertTrue(timeRules.findTemplateRules(archived).isEmpty());
    }

    @Test
    void anotherStationCannotReadTheRules() {
        var answer = harness.request(client -> client.get(
                rulesOf(templateId), harness.as(sessionAt(otherStation, StationPermission.ATTENDANCE_CONFIGURE))));

        assertEquals(GeneralRefusal.NOT_HERE_OR_NOT_YOURS, refusalOf(answer));
    }

    @Test
    void anEmptyListRemovesEveryRule() {
        configure(rules(rule("Jugendwart", 30, 0, groupAudience(leaders.id()))));

        var cleared = configure("{\"rules\":[]}");

        assertEquals(200, cleared.code());
        assertTrue(timeRules.findTemplateRules(templateId).isEmpty());
    }

    @Test
    void aNewSheetCopiesTheRulesAndKeepsThemWhenTheTemplateChanges() {
        configure(rules(rule("Jugendwart", 30, 15, groupAudience(leaders.id()))));
        int sessionId = newSheet();

        configure("{\"rules\":[]}");

        var read = harness.request(client ->
                client.get(sheetRulesOf(sessionId), harness.as(sessionAt(station, StationPermission.ATTENDANCE_READ))));
        assertEquals(200, read.code());
        assertEquals(1, json(read).size());
        assertEquals(30, json(read).get(0).get("preparationMinutes").asInt());
    }

    @Test
    void oneEveningIsCorrectedOnItsOwnSheet() {
        configure(rules(rule("Jugendwart", 30, 15, groupAudience(leaders.id()))));
        int sessionId = newSheet();

        var corrected = harness.request(client -> client.put(
                sheetRulesOf(sessionId),
                body(rules(rule("Jugendwart", 10, 0, groupAudience(leaders.id())))),
                harness.as(sessionAt(station, StationPermission.ATTENDANCE_EDIT))));

        assertEquals(200, corrected.code());
        assertEquals(10, timeRules.findSessionRules(sessionId).getFirst().preparationMinutes());
        assertEquals(30, timeRules.findTemplateRules(templateId).getFirst().preparationMinutes());
    }

    @Test
    void readingASheetIsNotCorrectingIt() {
        int sessionId = newSheet();

        var answer = harness.request(client -> client.put(
                sheetRulesOf(sessionId),
                body(rules(rule("Jugendwart", 10, 0, groupAudience(leaders.id())))),
                harness.as(sessionAt(station, StationPermission.ATTENDANCE_READ))));

        assertEquals(403, answer.code());
        assertTrue(timeRules.findSessionRules(sessionId).isEmpty());
    }

    @Test
    void anotherStationsSheetIsNotThere() {
        int sessionId = newSheet();

        var read = harness.request(client -> client.get(
                sheetRulesOf(sessionId), harness.as(sessionAt(otherStation, StationPermission.ATTENDANCE_READ))));
        var write = harness.request(client -> client.put(
                sheetRulesOf(sessionId),
                body(rules(rule("Fremd", 10, 0, groupAudience(leaders.id())))),
                harness.as(sessionAt(otherStation, StationPermission.ATTENDANCE_EDIT))));

        assertEquals(AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE, refusalOf(read));
        assertEquals(AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE, refusalOf(write));
        assertTrue(timeRules.findSessionRules(sessionId).isEmpty());
    }

    @Test
    void aClosedSheetTakesNoCorrection() {
        int sessionId = newSheet();
        int entryId = presentEntry(sessionId);
        attendanceRepo.lockSession(sessionId, Instant.now());

        var rulesAnswer = harness.request(client -> client.put(
                sheetRulesOf(sessionId),
                body(rules(rule("Spaet", 10, 0, groupAudience(leaders.id())))),
                harness.as(sessionAt(station, StationPermission.ATTENDANCE_EDIT))));
        var switchAnswer = setExtraTime(entryId, true, station);

        assertEquals(AttendanceRefusal.ATTENDANCE_SHEET_CLOSED, refusalOf(rulesAnswer));
        assertEquals(AttendanceRefusal.ATTENDANCE_SHEET_CLOSED, refusalOf(switchAnswer));
        assertTrue(timeRules.findSessionRules(sessionId).isEmpty());
        assertFalse(attendanceRepo.findEntryById(entryId).orElseThrow().skipExtraTime());
    }

    @Test
    void aPersonWhoDidNotPrepareIsMarkedAndUnmarked() {
        int entryId = presentEntry(newSheet());

        var marked = setExtraTime(entryId, true, station);
        assertEquals(200, marked.code());
        assertTrue(json(marked).get("skipExtraTime").asBoolean());
        assertTrue(attendanceRepo.findEntryById(entryId).orElseThrow().skipExtraTime());

        var unmarked = setExtraTime(entryId, false, station);
        assertFalse(json(unmarked).get("skipExtraTime").asBoolean());
    }

    @Test
    void anotherStationCannotMarkSomebody() {
        int entryId = presentEntry(newSheet());

        var answer = setExtraTime(entryId, true, otherStation);
        var missing = setExtraTime(999_999, true, station);

        assertEquals(AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE, refusalOf(answer));
        assertEquals(AttendanceRefusal.ATTENDANCE_ENTRY_NOT_HERE, refusalOf(missing));
        assertFalse(attendanceRepo.findEntryById(entryId).orElseThrow().skipExtraTime());
    }

    @Test
    void aSheetsRulesAreCheckedLikeATemplates() {
        int sessionId = newSheet();
        MemberGroup theirs = memberGroupRepo.create(otherStation.id(), "Fremde Wache");

        var nobody = correctSheet(sessionId, rules(rule("Alle", 10, 0, audience("[]", "[]", "[]", "AND"))));
        var foreign = correctSheet(sessionId, rules(rule("Fremd", 10, 0, groupAudience(theirs.id()))));

        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_NOBODY, refusalOf(nobody));
        assertEquals(AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_UNKNOWN, refusalOf(foreign));
        assertTrue(timeRules.findSessionRules(sessionId).isEmpty());
    }

    @Test
    void somebodyNotPresentCanBeMarkedAndKeepsItWhenTheyArrive() {
        int sessionId = newSheet();
        attendanceRepo.createEntry(
                sessionId, member.id(), AttendanceEntry.AttendanceStatus.ABSENT, AttendanceEntry.EntrySource.EXPECTED);
        int entryId =
                attendanceRepo.findEntry(sessionId, member.id()).orElseThrow().id();

        assertEquals(200, setExtraTime(entryId, true, station).code());
        attendanceService.updateEntryStatus(entryId, AttendanceEntry.AttendanceStatus.PRESENT);

        assertTrue(attendanceRepo.findEntryById(entryId).orElseThrow().skipExtraTime());
    }

    @Test
    void aSwitchThatDoesNotSayWhichIsRefused() {
        int entryId = presentEntry(newSheet());
        setExtraTime(entryId, true, station);

        var answer = harness.request(client -> client.put(
                RouteHarness.PREFIX + "/attendance/entries/" + entryId + "/extra-time",
                body("{}"),
                harness.as(sessionAt(station, StationPermission.ATTENDANCE_EDIT))));

        assertEquals(AttendanceRefusal.ATTENDANCE_EXTRA_TIME_NOT_GIVEN, refusalOf(answer));
        assertTrue(attendanceRepo.findEntryById(entryId).orElseThrow().skipExtraTime());
    }

    private Response correctSheet(int sessionId, String json) {
        return harness.request(client -> client.put(
                sheetRulesOf(sessionId),
                body(json),
                harness.as(sessionAt(station, StationPermission.ATTENDANCE_EDIT))));
    }

    @Test
    void aSheetSaysWhomItsRulesCredit() {
        memberGroupRepo.addMember(leaders.id(), member.id());
        configure(rules(rule("Jugendwart", 30, 15, groupAudience(leaders.id()))));
        int sessionId = newSheet();
        int entryId = presentEntry(sessionId);
        setExtraTime(entryId, true, station);

        var answer = harness.request(client -> client.get(
                RouteHarness.PREFIX + "/attendance/sessions/" + sessionId + "/extra-time",
                harness.as(sessionAt(station, StationPermission.ATTENDANCE_READ))));
        memberGroupRepo.removeMember(leaders.id(), member.id());

        assertEquals(200, answer.code());
        var credit = json(answer).get(0);
        assertEquals(entryId, credit.get("entryId").asInt());
        assertEquals(30, credit.get("preparationMinutes").asInt());
        assertEquals(15, credit.get("followUpMinutes").asInt());
        assertTrue(credit.get("skipped").asBoolean(), "the minutes are shown even while switched off");
    }

    @Test
    void anotherStationCannotReadWhomASheetCredits() {
        int sessionId = newSheet();

        var answer = harness.request(client -> client.get(
                RouteHarness.PREFIX + "/attendance/sessions/" + sessionId + "/extra-time",
                harness.as(sessionAt(otherStation, StationPermission.ATTENDANCE_READ))));

        assertEquals(AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE, refusalOf(answer));
    }

    @Test
    void aSheetWithoutRulesCreditsNobody() {
        int sessionId = newSheet();
        presentEntry(sessionId);

        var answer = harness.request(client -> client.get(
                RouteHarness.PREFIX + "/attendance/sessions/" + sessionId + "/extra-time",
                harness.as(sessionAt(station, StationPermission.ATTENDANCE_READ))));

        assertEquals(0, json(answer).size());
    }

    private int newSheet() {
        Instant start = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        return attendanceService
                .createSession(templateId, start, start.plus(2, ChronoUnit.HOURS), null, "Dienst", null, null, null)
                .id();
    }

    private static int presentEntry(int sessionId) {
        attendanceRepo.createEntry(
                sessionId, member.id(), AttendanceEntry.AttendanceStatus.PRESENT, AttendanceEntry.EntrySource.EXTRA);
        return attendanceRepo.findEntry(sessionId, member.id()).orElseThrow().id();
    }

    private Response setExtraTime(int entryId, boolean skip, Station as) {
        return harness.request(client -> client.put(
                RouteHarness.PREFIX + "/attendance/entries/" + entryId + "/extra-time",
                body("{\"skip\":" + skip + "}"),
                harness.as(sessionAt(as, StationPermission.ATTENDANCE_EDIT))));
    }

    private static String sheetRulesOf(int sessionId) {
        return RouteHarness.PREFIX + "/attendance/sessions/" + sessionId + "/time-rules";
    }

    private Response configure(String json) {
        return harness.request(client -> client.put(
                rulesOf(templateId),
                body(json),
                harness.as(sessionAt(station, StationPermission.ATTENDANCE_CONFIGURE))));
    }

    private static String rulesOf(int templateId) {
        return RouteHarness.PREFIX + "/attendance/templates/" + templateId + "/time-rules";
    }

    private static String rules(String... rules) {
        return "{\"rules\":[" + String.join(",", rules) + "]}";
    }

    private static String rule(String label, int preparation, int followUp, String audience) {
        return "{\"label\":\"%s\",\"preparationMinutes\":%d,\"followUpMinutes\":%d,\"audience\":%s}"
                .formatted(label, preparation, followUp, audience);
    }

    private static String groupAudience(int groupId) {
        return audience("[]", "[" + groupId + "]", "[]", "AND");
    }

    private static String audience(String userTypes, String groupIds, String tagIds, String mode) {
        return "{\"userTypes\":%s,\"groupIds\":%s,\"tagIds\":%s,\"memberIds\":[],\"mode\":\"%s\"}"
                .formatted(userTypes, groupIds, tagIds, mode);
    }

    private static UserSession sessionAt(Station at, StationPermission held) {
        return new UserSession(
                new Account(1, null, "wer@test.com", null, "Wer", "Da", true, null, "Wer Da", null, null),
                1,
                at.id(),
                at.uid(),
                member,
                EnumSet.of(StationPermission.USER, held),
                Set.of(),
                null);
    }
}
