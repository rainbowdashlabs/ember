/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.TimeRule;
import dev.chojo.ember.feature.attendance.entity.TimeRuleInput;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.sql.Transactions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The preparation and follow-up rules of templates and sheets as they are stored: written as a whole,
 * read back in their order with whom they name, and copied from a template onto a sheet that owns them
 * from then on.
 */
class AttendanceTimeRuleRepositoryTest extends RepositoryTestBase {
    private static final AttendanceTimeRuleRepository rules = new AttendanceTimeRuleRepository();

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static MemberGroup leaders;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Time Rule Station");
        account = accountRepo.create("time-rules@test.com", "Time", "Rule");
        member = stationMemberRepo.create(station.id(), account.id());
        leaders = memberGroupRepo.create(station.id(), "Jugendwarte");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static RestrictionAudience groupAudience(int groupId) {
        return new RestrictionAudience(List.of(), List.of(groupId), List.of(), List.of(), RestrictionMode.AND);
    }

    private static RestrictionAudience typeAudience(StationUserType type) {
        return new RestrictionAudience(List.of(type), List.of(), List.of(), List.of(), RestrictionMode.OR);
    }

    private static int newTemplate(String name) {
        return attendanceRepo.createTemplate(station.id(), name).id();
    }

    private static int newSession(int templateId) {
        Instant start = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        return attendanceRepo
                .createSession(templateId, start, start.plus(2, ChronoUnit.HOURS), null, "Dienst", null)
                .id();
    }

    @Test
    void aTemplateKeepsItsRulesInOrderWithWhomTheyName() {
        int templateId = newTemplate("Order");
        rules.replaceTemplateRules(
                templateId,
                List.of(
                        new TimeRuleInput("  Jugendwart ", 30, 15, groupAudience(leaders.id())),
                        new TimeRuleInput(" ", 10, 0, typeAudience(StationUserType.MANAGER))));

        List<TimeRule> read = rules.findTemplateRules(templateId);

        assertEquals(2, read.size());
        assertEquals("Jugendwart", read.get(0).label());
        assertEquals(30, read.get(0).preparationMinutes());
        assertEquals(15, read.get(0).followUpMinutes());
        assertEquals(List.of(leaders.id()), read.get(0).audience().groupIds());
        assertEquals(RestrictionMode.AND, read.get(0).audience().mode());
        assertNull(read.get(1).label(), "a blank label is no label");
        assertEquals(List.of(StationUserType.MANAGER), read.get(1).audience().userTypes());
        assertEquals(RestrictionMode.OR, read.get(1).audience().mode());
        assertEquals(List.of(0, 1), read.stream().map(TimeRule::position).toList());
    }

    @Test
    void replacingTheRulesDropsTheOldOnes() {
        int templateId = newTemplate("Replace");
        rules.replaceTemplateRules(templateId, List.of(new TimeRuleInput("Alt", 5, 5, groupAudience(leaders.id()))));

        rules.replaceTemplateRules(templateId, List.of(new TimeRuleInput("Neu", 20, 0, groupAudience(leaders.id()))));

        List<TimeRule> read = rules.findTemplateRules(templateId);
        assertEquals(1, read.size());
        assertEquals("Neu", read.getFirst().label());
    }

    @Test
    void aSheetOwnsTheRulesItCopied() {
        int templateId = newTemplate("Copy");
        rules.replaceTemplateRules(
                templateId, List.of(new TimeRuleInput("Jugendwart", 30, 15, groupAudience(leaders.id()))));
        int sessionId = newSession(templateId);

        rules.copyTemplateRules(templateId, sessionId);
        rules.replaceTemplateRules(templateId, List.of());

        List<TimeRule> onSheet = rules.findSessionRules(sessionId);
        assertEquals(1, onSheet.size(), "a later change to the template does not reach the sheet");
        assertEquals(30, onSheet.getFirst().preparationMinutes());
        assertEquals(List.of(leaders.id()), onSheet.getFirst().audience().groupIds());
    }

    @Test
    void copyingFromATemplateWithoutRulesLeavesTheSheetWithout() {
        int templateId = newTemplate("Empty");
        int sessionId = newSession(templateId);

        rules.copyTemplateRules(templateId, sessionId);

        assertTrue(rules.findSessionRules(sessionId).isEmpty());
    }

    @Test
    void severalSheetsAreReadAtOnce() {
        int templateId = newTemplate("Batch");
        int first = newSession(templateId);
        int second = newSession(templateId);
        int bare = newSession(templateId);
        rules.replaceSessionRules(first, List.of(new TimeRuleInput("A", 10, 0, groupAudience(leaders.id()))));
        rules.replaceSessionRules(
                second,
                List.of(
                        new TimeRuleInput("B", 0, 20, groupAudience(leaders.id())),
                        new TimeRuleInput("C", 5, 5, typeAudience(StationUserType.MEMBER))));

        Map<Integer, List<TimeRule>> read = rules.findSessionRules(List.of(first, second, bare));

        assertEquals(1, read.get(first).size());
        assertEquals(
                List.of("B", "C"),
                read.get(second).stream().map(TimeRule::label).toList());
        assertFalse(read.containsKey(bare), "a sheet without rules is missing");
        assertTrue(rules.findSessionRules(List.of()).isEmpty());
    }

    @Test
    void aRuleWhoseOnlyGroupWasDeletedCreditsNobody() {
        MemberGroup gone = memberGroupRepo.create(station.id(), "Aufgeloest");
        int templateId = newTemplate("Deleted group");
        rules.replaceTemplateRules(templateId, List.of(new TimeRuleInput("Weg", 30, 30, groupAudience(gone.id()))));

        memberGroupRepo.delete(gone.id());

        TimeRule rule = rules.findTemplateRules(templateId).getFirst();
        assertTrue(rule.audience().namesNobody());
        assertFalse(rule.appliesTo(new RestrictionMember(member.id(), StationUserType.MEMBER, List.of(), List.of())));
    }

    @Test
    void membersNamedOneByOneAndMixedKindsComeBackAsWritten() {
        UserTag trainer = userTagRepo.create(station.id(), "Ausbilder");
        int templateId = newTemplate("Mixed");
        var mixed = new RestrictionAudience(
                List.of(StationUserType.MEMBER),
                List.of(leaders.id()),
                List.of(trainer.id()),
                List.of(member.id()),
                RestrictionMode.AND);
        var named = new RestrictionAudience(List.of(), List.of(), List.of(), List.of(member.id()), RestrictionMode.OR);
        rules.replaceTemplateRules(
                templateId,
                List.of(new TimeRuleInput("Gemischt", 15, 15, mixed), new TimeRuleInput("Einzeln", 5, 0, named)));

        List<TimeRule> read = rules.findTemplateRules(templateId);

        assertEquals(mixed, read.get(0).audience());
        assertEquals(named, read.get(1).audience());
        assertTrue(read.stream().allMatch(TimeRule::intact));
    }

    @Test
    void aRuleThatLostATagItNeededCreditsNobodyAndIsNotCopied() {
        UserTag trainer = userTagRepo.create(station.id(), "Kurzzeit");
        int templateId = newTemplate("Lost tag");
        var memberAndTag = new RestrictionAudience(
                List.of(StationUserType.MEMBER), List.of(), List.of(trainer.id()), List.of(), RestrictionMode.AND);
        rules.replaceTemplateRules(templateId, List.of(new TimeRuleInput("Ausbilder", 30, 0, memberAndTag)));

        userTagRepo.delete(trainer.id());

        TimeRule rule = rules.findTemplateRules(templateId).getFirst();
        assertFalse(rule.intact(), "it still names members, but no longer only those with the tag");
        assertFalse(rule.appliesTo(new RestrictionMember(member.id(), StationUserType.MEMBER, List.of(), List.of())));
        int sessionId = newSession(templateId);
        rules.copyTemplateRules(templateId, sessionId);
        assertTrue(rules.findSessionRules(sessionId).isEmpty(), "what is left of it is not copied onto a sheet");
    }

    @Test
    void losingAMemberNamedOneByOneOnlyNarrowsTheRule() {
        Account other = accountRepo.create("time-rules-other@test.com", "Other", "Member");
        StationMember leaving = stationMemberRepo.create(station.id(), other.id());
        int templateId = newTemplate("Lost member");
        var named = new RestrictionAudience(
                List.of(), List.of(), List.of(), List.of(member.id(), leaving.id()), RestrictionMode.OR);
        rules.replaceTemplateRules(templateId, List.of(new TimeRuleInput("Einzeln", 10, 0, named)));

        stationMemberRepo.delete(leaving.id());
        accountRepo.delete(other.id());

        TimeRule rule = rules.findTemplateRules(templateId).getFirst();
        assertTrue(rule.intact());
        assertEquals(List.of(member.id()), rule.audience().memberIds());
    }

    @Test
    void timeRulesDoNotKeepAGroupFromBeingDeleted() {
        MemberGroup named = memberGroupRepo.create(station.id(), "Nur Zeit");
        int templateId = newTemplate("Group count");
        rules.replaceTemplateRules(templateId, List.of(new TimeRuleInput("Zeit", 10, 0, groupAudience(named.id()))));
        rules.copyTemplateRules(templateId, newSession(templateId));

        assertEquals(0, memberGroupRepo.countContentLimitedTo(named.id()));
    }

    @Test
    void deletingASheetTakesItsRulesAlong() {
        int templateId = newTemplate("Cascade");
        int sessionId = newSession(templateId);
        rules.replaceSessionRules(sessionId, List.of(new TimeRuleInput("Weg", 10, 0, groupAudience(leaders.id()))));

        attendanceRepo.deleteSession(sessionId);

        assertTrue(rules.findSessionRules(sessionId).isEmpty());
    }

    @Test
    void copyingJoinsATransactionThatIsAlreadyRunning() {
        int templateId = newTemplate("Joined");
        rules.replaceTemplateRules(templateId, List.of(new TimeRuleInput("Mit", 10, 5, groupAudience(leaders.id()))));
        int sessionId = newSession(templateId);

        assertThrows(
                IllegalStateException.class,
                () -> Transactions.run(() -> {
                    rules.copyTemplateRules(templateId, sessionId);
                    throw new IllegalStateException("the sheet could not be made");
                }));
        assertTrue(rules.findSessionRules(sessionId).isEmpty(), "rolled back with the rest");

        Transactions.run(() -> rules.copyTemplateRules(templateId, sessionId));
        assertEquals(1, rules.findSessionRules(sessionId).size());
    }

    @Test
    void anEntryCarriesItsSwitchUnsetUntilSomebodySetsIt() {
        int sessionId = newSession(newTemplate("Entry"));
        attendanceRepo.createEntry(
                sessionId, member.id(), AttendanceEntry.AttendanceStatus.PRESENT, AttendanceEntry.EntrySource.EXPECTED);

        AttendanceEntry entry = attendanceRepo.findEntry(sessionId, member.id()).orElseThrow();

        assertFalse(entry.skipExtraTime());
        assertEquals(entry, attendanceRepo.findEntryById(entry.id()).orElseThrow());
    }

    @Test
    void aTagATemplateRuleNamesCountsAsSelectingWhileItsSheetCopiesDoNot() {
        UserTag named = userTagRepo.create(station.id(), "Zaehlt");
        int templateId = newTemplate("Tag uses");
        var byTag = new RestrictionAudience(List.of(), List.of(), List.of(named.id()), List.of(), RestrictionMode.AND);
        rules.replaceTemplateRules(templateId, List.of(new TimeRuleInput("Tag", 10, 0, byTag)));
        long templateOnly = userTagRepo.countSelectingUses(named.id());

        rules.copyTemplateRules(templateId, newSession(templateId));

        assertEquals(1, templateOnly);
        assertEquals(1, userTagRepo.countSelectingUses(named.id()), "a sheet copy does not keep the tag public");
    }
}
